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
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when (entry.name) {
                            MANIFEST -> {
                                manifest = json.decodeFromString<LibraryManifest>(zip.readBytes().toString(Charsets.UTF_8))
                                readBytes += entry.size.coerceAtLeast(0)
                            }
                            "profiles.json" -> {
                                profiles = runCatching {
                                    json.decodeFromString<List<DriverProfile>>(zip.readBytes().toString(Charsets.UTF_8))
                                }.getOrDefault(emptyList())
                                readBytes += entry.size.coerceAtLeast(0)
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
                                        onProgress(readBytes, manifest?.bytes ?: 0)
                                    }
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
        return if (lapJsonFile.isFile && File(dir, "lap.gt7lap").isFile) {
            val record = runCatching { json.decodeFromString<LapRecord>(lapJsonFile.readText()) }.getOrNull() ?: return false
            val target = File(lapsDir, id)
            if (target.exists()) return false
            dir.renameTo(target)
            true
        } else {
            false
        }
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
