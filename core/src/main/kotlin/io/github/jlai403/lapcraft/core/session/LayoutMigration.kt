package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.io.AtomicFiles
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * One-shot, versioned migration of the on-disk layout. Runs at `LapStore` construction and
 * before every list/load. Each step deletes the legacy source files once its conversion has
 * succeeded, so a successful run leaves nothing for a later launch to reprocess.
 */
object LayoutMigration {
    private const val TARGET_VERSION = 3
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun migrate(rootDir: File, carName: (Int) -> String?) {
        val lapsDir = File(rootDir, "laps")
        val versionFile = File(lapsDir, ".layout-version")
        val version = if (versionFile.isFile) versionFile.readText().trim().toIntOrNull() ?: 0 else 0
        if (version >= TARGET_VERSION) return

        if (version < 1) migrateSessions(rootDir, carName)
        if (version < 2) migrateLapJson(rootDir)
        if (version < 3) dedupeLegacyV1(rootDir)

        lapsDir.mkdirs()
        versionFile.writeText(TARGET_VERSION.toString())
    }

    private fun migrateSessions(rootDir: File, carName: (Int) -> String?) {
        val legacySessions = File(rootDir, "sessions")
        if (!legacySessions.isDirectory) return
        val lapsDir = File(rootDir, "laps").apply { mkdirs() }

        for (sessionDir in legacySessions.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }) {
            val sessionFile = File(sessionDir, "session.json")
            if (!sessionFile.isFile) continue
            val session = runCatching { SessionJsonCodec.decodeSession(sessionFile.readText()) }.getOrNull() ?: continue

            var sessionOk = true
            for (lap in session.laps) {
                val newId = if (session.laps.size == 1) session.sessionId else "${session.sessionId}-l${lap.index}"
                val newDir = File(lapsDir, newId)
                if (newDir.exists()) continue
                newDir.mkdirs()

                val srcTelemetry = File(sessionDir, "laps/lap-%03d.gt7lap".format(lap.index))
                if (srcTelemetry.isFile) {
                    runCatching { srcTelemetry.copyTo(File(newDir, "lap.gt7lap"), overwrite = true) }
                        .onFailure { sessionOk = false; continue }
                }

                when {
                    lap.needsCut && lap.videoFile.isNotBlank() -> {
                        val src = File(sessionDir, lap.videoFile)
                        if (src.isFile) runCatching { src.copyTo(File(newDir, "source.mp4"), overwrite = true) }
                            .onFailure { sessionOk = false }
                    }
                    lap.videoFile.isNotBlank() && lap.videoFile == session.rawFile -> {
                        val src = File(sessionDir, lap.videoFile)
                        if (src.isFile) runCatching { src.copyTo(File(newDir, "raw.mp4"), overwrite = true) }
                            .onFailure { sessionOk = false }
                    }
                    lap.videoFile.isNotBlank() -> {
                        val src = File(sessionDir, lap.videoFile)
                        if (src.isFile) runCatching { src.copyTo(File(newDir, "replay.mp4"), overwrite = true) }
                            .onFailure { sessionOk = false }
                    }
                }
                lap.sourceFile?.takeIf { it.isNotBlank() }?.let { rel ->
                    val src = File(sessionDir, rel)
                    if (src.isFile) runCatching { src.copyTo(File(newDir, "source.mp4"), overwrite = true) }
                        .onFailure { sessionOk = false }
                }

                val record = LapRecord(
                    schemaVersion = 2,
                    capturedAt = session.createdAt,
                    lapTimeMs = lap.lapTimeMs,
                    lapTimeSource = io.github.jlai403.lapcraft.core.lap.LapTimeSource.Reported,
                    trackId = "",
                    trackLabel = session.trackLabel,
                    carCode = session.carCode,
                    carName = session.carName,
                    startTickNanos = lap.startNanos,
                    endTickNanos = lap.endNanos,
                    leadInMs = lap.leadInMs - lap.syncOffsetMs,
                    driverPsnId = lap.driverPsnId,
                    event = lap.event,
                )
                runCatching { AtomicFiles.writeText(File(newDir, "lap.json"), json.encodeToString(record)) }
                    .onFailure { sessionOk = false }
            }

            if (sessionOk) {
                runCatching { sessionDir.deleteRecursively() }
            }
        }
        if (legacySessions.listFiles()?.none { it.isDirectory } == true) {
            runCatching { legacySessions.delete() }
        }
    }

    private fun migrateLapJson(rootDir: File) {
        val lapsDir = File(rootDir, "laps")
        if (!lapsDir.isDirectory) return
        for (dir in lapsDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }) {
            val file = File(dir, "lap.json")
            if (!file.isFile) continue
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: continue
            val obj = element.jsonObject
            val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
            if (version >= 3) continue

            val leadIn = obj["leadInMs"]?.jsonPrimitive?.intOrNull
                ?: obj["videoStartOffsetMs"]?.jsonPrimitive?.intOrNull
                ?: 0
            val offset = obj["syncOffsetMs"]?.jsonPrimitive?.intOrNull ?: 0
            val newLeadIn = leadIn - offset

            // rebuild preserving all other fields, dropping the legacy keys
            val mutable = obj.toMutableMap()
            mutable["leadInMs"] = kotlinx.serialization.json.JsonPrimitive(newLeadIn)
            mutable["schemaVersion"] = kotlinx.serialization.json.JsonPrimitive(3)
            mutable.remove("videoStartOffsetMs")
            mutable.remove("syncOffsetMs")

            val newText = json.encodeToString(kotlinx.serialization.json.JsonObject(mutable))
            runCatching { AtomicFiles.writeText(file, newText) }
        }
    }

    private fun dedupeLegacyV1(rootDir: File) {
        val legacy = File(rootDir, "sessions.legacy-v1")
        if (!legacy.isDirectory) return
        for (sessionDir in legacy.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }) {
            val sessionFile = File(sessionDir, "session.json")
            val session = if (sessionFile.isFile) {
                runCatching { SessionJsonCodec.decodeSession(sessionFile.readText()) }.getOrNull()
            } else null

            val lapIds = session?.laps?.map { lap ->
                if (session.laps.size == 1) session.sessionId else "${session.sessionId}-l${lap.index}"
            } ?: sessionDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

            val allPresent = lapIds.isNotEmpty() && lapIds.all { File(rootDir, "laps/$it").isDirectory && File(rootDir, "laps/$it/lap.json").isFile }
            if (allPresent) {
                runCatching { sessionDir.deleteRecursively() }
            }
        }
        // remove the container if empty
        if (legacy.listFiles()?.isEmpty() == true) {
            legacy.delete()
        }
    }
}
