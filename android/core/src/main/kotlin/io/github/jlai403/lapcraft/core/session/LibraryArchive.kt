package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.lap.LapTimeSource

import io.github.jlai403.lapcraft.core.io.AtomicFiles
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.LinkedHashMap
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExportReport(val laps: Int, val bytes: Long)

data class ImportReport(val laps: Int, val skipped: Int, val failed: Int, val profiles: List<DriverProfile> = emptyList())

@Serializable
data class LibraryManifest(
    val format: Int,
    val exportedAt: String,
    val laps: Int,
    val bytes: Long,
)

object LibraryArchive {
    const val FORMAT = 2
    private const val MANIFEST = "library.json"
    private const val ITEM_ROOT = "library"
    private const val BUFFER = 64 * 1024
    private const val MAX_ENTRIES = 100_000
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_PROFILES_BYTES = 1024 * 1024
    private const val MAX_TOTAL_BYTES = 20L * 1024 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun export(
        rootDir: File,
        laps: List<LapId>,
        profiles: List<DriverProfile>,
        out: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ExportReport {
        LayoutMigration.migrate(rootDir, { null })
        val entries = laps.mapNotNull { resolve(rootDir, it) }
        val profilesJson = if (profiles.isEmpty()) null else json.encodeToString(profiles)
        val total = entries.sumOf { it.bytes } + (profilesJson?.length?.toLong() ?: 0)
        val manifest = LibraryManifest(FORMAT, Instant.now().toString(), entries.size, total)

        ZipOutputStream(BufferedOutputStream(out, BUFFER)).use { zip ->
            zip.writeText(MANIFEST, json.encodeToString(manifest))
            var written = 0L
            if (profilesJson != null) {
                zip.writeText("profiles.json", profilesJson)
                written += profilesJson.length.toLong()
                onProgress(written, total)
            }
            for (entry in entries) {
                val prefix = "$ITEM_ROOT/${entry.id}"
                zip.writeText("$prefix/lap.json", entry.lapJson)
                written += entry.lapJson.length.toLong()
                onProgress(written, total)
                for (file in entry.files) {
                    zip.levelFor(file.relative)
                    zip.putNextEntry(ZipEntry("$prefix/${file.relative}"))
                    file.file.inputStream().use { it.copyTo(zip, BUFFER) }
                    zip.closeEntry()
                    written += file.file.length()
                    onProgress(written, total)
                }
            }
        }
        return ExportReport(entries.size, total)
    }

    fun import(
        rootDir: File,
        input: InputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ImportReport {
        val lapsDir = File(rootDir, "laps").apply { mkdirs() }
        LayoutMigration.migrate(rootDir, { null })
        sweepStaging(lapsDir)
        val staging = File(lapsDir, ".import-${UUID.randomUUID()}")
        var manifest: LibraryManifest? = null
        var profiles: List<DriverProfile> = emptyList()
        var readBytes = 0L
        var imported = 0
        var skipped = 0
        var failed = 0
        try {
            ZipInputStream(BufferedInputStream(input, BUFFER)).use { zip ->
                var entry = zip.nextEntry
                var entryCount = 0
                while (entry != null) {
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "too many archive entries" }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }
                    val entrySize = entry.size
                    if (entrySize >= 0) {
                        require(entrySize <= MAX_ENTRY_BYTES) { "archive entry too large: ${entry.name}" }
                    }
                    when (entry.name) {
                        MANIFEST -> {
                            val bytes = zip.readBytes()
                            require(bytes.size <= MAX_MANIFEST_BYTES) { "manifest too large" }
                            manifest = json.decodeFromString<LibraryManifest>(bytes.toString(Charsets.UTF_8))
                            readBytes += bytes.size.toLong()
                            require(readBytes <= MAX_TOTAL_BYTES) { "archive too large" }
                        }
                        "profiles.json" -> {
                            val bytes = zip.readBytes()
                            require(bytes.size <= MAX_PROFILES_BYTES) { "profiles too large" }
                            profiles = runCatching {
                                json.decodeFromString<List<DriverProfile>>(bytes.toString(Charsets.UTF_8))
                            }.getOrDefault(emptyList()).filter { validProfile(it) }
                            readBytes += bytes.size.toLong()
                            require(readBytes <= MAX_TOTAL_BYTES) { "archive too large" }
                        }
                        else -> {
                            val target = File(staging, safeEntryPath(entry.name))
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out ->
                                val buffer = ByteArray(BUFFER)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count <= 0) break
                                    out.write(buffer, 0, count)
                                    readBytes += count
                                    require(readBytes <= MAX_TOTAL_BYTES) { "archive too large" }
                                    onProgress(readBytes, manifest?.bytes ?: 0)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val parsed = requireNotNull(manifest) { "Unsupported library format: missing $MANIFEST" }
            require(parsed.format == FORMAT) {
                "Unsupported library format ${parsed.format}; expected $FORMAT"
            }

            staging.listFiles().orEmpty().forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val id = dir.name
                if (!ID_PATTERN.matches(id)) {
                    failed++
                    dir.deleteRecursively()
                    return@forEach
                }
                if (File(lapsDir, id).exists()) {
                    skipped++
                    dir.deleteRecursively()
                    return@forEach
                }
                if (runCatching { importStaged(dir, id, lapsDir, parsed.format) }.getOrDefault(false)) {
                    imported++
                } else {
                    failed++
                    dir.deleteRecursively()
                }
            }
        } finally {
            staging.deleteRecursively()
        }
        return ImportReport(imported, skipped, failed, profiles)
    }

    private fun importStaged(dir: File, id: String, lapsDir: File, format: Int): Boolean {
        val lapJsonFile = File(dir, "lap.json")
        val telemetryFile = File(dir, "lap.gt7lap")
        if (!lapJsonFile.isFile || !telemetryFile.isFile) return false
        val record = runCatching { json.decodeFromString<LapRecord>(lapJsonFile.readText()) }.getOrNull() ?: return false
        if (!validRecord(record)) return false
        if (runCatching { LapTelemetryCodec.validateHeader(telemetryFile.readBytes()) }.isFailure) return false
        val target = File(lapsDir, id)
        if (target.exists()) return false
        return dir.renameTo(target)
    }

    private fun validProfile(profile: DriverProfile): Boolean {
        if (profile.psnId.isBlank() || profile.psnId.length > 64) return false
        if (profile.nickname.length > 64) return false
        if (profile.countryCode != null && (profile.countryCode.length != 2 || !profile.countryCode.all { it in 'A'..'Z' })) return false
        if (profile.dr != null && (profile.dr.isEmpty() || profile.dr.length > 3)) return false
        if (profile.sr != null && (profile.sr.isEmpty() || profile.sr.length > 3)) return false
        return true
    }

    private fun validRecord(record: LapRecord): Boolean {
        if (record.lapTimeMs !in 0..24 * 60 * 60 * 1000) return false
        if (record.sampleRateHz !in 1..200) return false
        if (record.trackLabel.length > 120 || record.carName.length > 120) return false
        if (record.driverPsnId != null && record.driverPsnId.length > 64) return false
        if (runCatching { java.time.Instant.parse(record.capturedAt) }.isFailure) return false
        return true
    }

    private fun sweepStaging(lapsDir: File) {
        lapsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(".import-") }
            ?.forEach { it.deleteRecursively() }
    }

    private fun safeEntryPath(name: String): String {
        val normalized = name.replace('\\', '/')
        val parts = normalized.split('/')
        val unsafe = normalized.startsWith("/") ||
            !normalized.startsWith("$ITEM_ROOT/") ||
            parts.any { it.isEmpty() || it == "." || it == ".." }
        require(!unsafe) { "unsafe archive entry: $name" }
        return normalized.removePrefix("$ITEM_ROOT/")
    }

    private class ArchivedFile(val relative: String, val file: File)

    private class Entry(
        val id: String,
        val lapJson: String,
        val files: List<ArchivedFile>,
    ) {
        val bytes: Long = lapJson.length.toLong() + files.sumOf { it.file.length() }
    }

    private fun resolve(rootDir: File, id: LapId): Entry? {
        if (!ID_PATTERN.matches(id.value)) return null
        val newDir = File(rootDir, "laps/${id.value}")
        val newLapFile = File(newDir, "lap.json")
        if (newLapFile.isFile) {
            val record = runCatching { json.decodeFromString<LapRecord>(newLapFile.readText()) }.getOrNull() ?: return null
            val media = buildList {
                val telemetry = File(newDir, "lap.gt7lap")
                if (telemetry.isFile) add(ArchivedFile("lap.gt7lap", telemetry))
                for (name in listOf("replay.mp4", "source.mp4", "raw.mp4")) {
                    val f = File(newDir, name)
                    if (f.isFile) add(ArchivedFile(name, f))
                }
            }
            return Entry(id = id.value, lapJson = json.encodeToString(record), files = media)
        }
        return null
    }

    private fun ZipOutputStream.writeText(name: String, text: String) {
        levelFor(name)
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.levelFor(name: String) {
        setLevel(if (name.endsWith(".mp4")) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
    }
}
