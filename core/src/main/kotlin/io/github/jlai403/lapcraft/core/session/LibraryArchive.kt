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
    const val LEGACY_FORMAT = 1
    private const val MANIFEST = "library.json"
    private const val ITEM_ROOT = "library"
    private const val BUFFER = 64 * 1024
    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val CLIP_PATH = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun export(
        rootDir: File,
        laps: List<LapId>,
        profiles: List<DriverProfile>,
        out: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ExportReport {
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
            require(parsed.format == FORMAT || parsed.format == LEGACY_FORMAT) {
                "Unsupported library format ${parsed.format}; expected $FORMAT or $LEGACY_FORMAT"
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
        } else if (File(dir, "session.json").isFile) {
            val session = runCatching { SessionJsonCodec.decodeSession(File(dir, "session.json").readText()) }.getOrNull() ?: return false
            val incoming = runCatching { SessionJsonCodec.decodeLap(File(dir, "lap.json").readText()) }.getOrNull() ?: return false
            val telemetryRel = "laps/lap-%03d.gt7lap".format(incoming.index)
            if (!File(dir, telemetryRel).isFile) return false
            val lap = incoming.copy(
                videoFile = incoming.videoFile.takeIf { it.isNotBlank() }?.let { keepClip(dir, it) }.orEmpty(),
                sourceFile = incoming.sourceFile?.let { keepClip(dir, it) },
            )
            val raw = session.rawFile.takeIf { it.isNotBlank() }?.let { keepClip(dir, it) }
            val record = LapRecord(
                schemaVersion = 2,
                capturedAt = session.createdAt,
                lapTimeMs = lap.lapTimeMs,
                lapTimeSource = LapTimeSource.Reported,
                trackId = "",
                trackLabel = session.trackLabel,
                carCode = session.carCode,
                carName = session.carName,
                startTickNanos = lap.startNanos,
                endTickNanos = lap.endNanos,
                leadInMs = lap.leadInMs,
                syncOffsetMs = lap.syncOffsetMs,
                driverPsnId = lap.driverPsnId,
                event = lap.event,
            )
            val target = File(lapsDir, id)
            if (target.exists()) return false
            target.mkdirs()
            File(dir, telemetryRel).copyTo(File(target, "lap.gt7lap"), overwrite = true)
            when {
                lap.needsCut && lap.videoFile.isNotBlank() -> {
                    File(dir, lap.videoFile).takeIf { it.isFile }?.copyTo(File(target, "source.mp4"), overwrite = true)
                }
                lap.videoFile.isNotBlank() && lap.videoFile == session.rawFile -> {
                    File(dir, lap.videoFile).takeIf { it.isFile }?.copyTo(File(target, "raw.mp4"), overwrite = true)
                }
                lap.videoFile.isNotBlank() -> {
                    File(dir, lap.videoFile).takeIf { it.isFile }?.copyTo(File(target, "replay.mp4"), overwrite = true)
                }
            }
            lap.sourceFile?.takeIf { it.isNotBlank() }?.let { rel ->
                File(dir, rel).takeIf { it.isFile }?.copyTo(File(target, "source.mp4"), overwrite = true)
            }
            raw?.let { rel ->
                if (lap.videoFile != session.rawFile) {
                    File(dir, rel).takeIf { it.isFile }?.copyTo(File(target, "raw.mp4"), overwrite = true)
                }
            }
            AtomicFiles.writeText(File(target, "lap.json"), json.encodeToString(record))
            dir.deleteRecursively()
            true
        } else {
            false
        }
    }

    private fun keepClip(dir: File, relative: String): String? {
        if (!CLIP_PATH.matches(relative) || !relative.endsWith(".mp4", ignoreCase = true)) return null
        if (relative.split('/').any { it == ".." || it == "." }) return null
        return relative.takeIf { File(dir, it).isFile }
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
        val (sessionId, index) = parseLegacyId(id.value) ?: return null
        val dir = File(rootDir, "sessions/$sessionId")
        val sessionFile = File(dir, "session.json")
        if (!sessionFile.isFile) return null
        val session = runCatching { SessionJsonCodec.decodeSession(sessionFile.readText()) }.getOrNull() ?: return null
        val lap = session.laps.firstOrNull { it.index == index } ?: return null
        val telemetryFile = File(dir, "laps/lap-%03d.gt7lap".format(lap.index))
        if (!telemetryFile.isFile) return null
        val media = LinkedHashMap<String, File>()
        media["lap.gt7lap"] = telemetryFile
        fun add(relative: String, file: File) { if (file.isFile) media[relative] = file }
        when {
            lap.needsCut && lap.videoFile.isNotBlank() -> add("source.mp4", File(dir, lap.videoFile))
            lap.videoFile.isNotBlank() && lap.videoFile == session.rawFile -> add("raw.mp4", File(dir, lap.videoFile))
            lap.videoFile.isNotBlank() -> add("replay.mp4", File(dir, lap.videoFile))
        }
        lap.sourceFile?.takeIf { it.isNotBlank() }?.let { add("source.mp4", File(dir, it)) }
        if (session.rawFile.isNotBlank() && session.rawFile != lap.videoFile) {
            add("raw.mp4", File(dir, session.rawFile))
        }
        val record = LapRecord(
            schemaVersion = 2,
            capturedAt = session.createdAt,
            lapTimeMs = lap.lapTimeMs,
            lapTimeSource = LapTimeSource.Reported,
            trackId = "",
            trackLabel = session.trackLabel,
            carCode = session.carCode,
            carName = session.carName,
            startTickNanos = lap.startNanos,
            endTickNanos = lap.endNanos,
            leadInMs = lap.leadInMs,
            syncOffsetMs = lap.syncOffsetMs,
            driverPsnId = lap.driverPsnId,
            event = lap.event,
        )
        return Entry(id = id.value, lapJson = json.encodeToString(record), files = media.map { ArchivedFile(it.key, it.value) })
    }

    private fun parseLegacyId(id: String): Pair<String, Int>? {
        val marker = "-l"
        val pos = id.lastIndexOf(marker)
        return if (pos >= 0 && pos + marker.length < id.length) {
            val index = id.substring(pos + marker.length).toIntOrNull() ?: return null
            id.substring(0, pos) to index
        } else {
            id to 1
        }
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
