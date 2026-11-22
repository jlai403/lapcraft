package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.lap.LapTimeSource

import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.io.AtomicFiles
import io.github.jlai403.lapcraft.core.model.Sample
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

open class LapStore(
    private val rootDir: File,
    private val carName: (Int) -> String? = { null },
) {
    private val lapsDir: File get() = File(rootDir, "laps")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    init { LayoutMigration.migrate(rootDir, carName) }

    fun lapDir(id: LapId): File = File(lapsDir, id.value)

    fun list(): List<LapId> {
        LayoutMigration.migrate(rootDir, carName)
        return (lapsDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { LapId(it.name) }
            .sortedByDescending { load(it)?.capturedAt ?: "" }
    }

    fun load(id: LapId): LapRecord? {
        LayoutMigration.migrate(rootDir, carName)
        val file = File(lapDir(id), "lap.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<LapRecord>(file.readText()) }.getOrNull()
    }

    fun save(id: LapId, record: LapRecord) {
        val dir = lapDir(id).apply { mkdirs() }
        AtomicFiles.writeText(File(dir, "lap.json"), json.encodeToString(record))
    }

    fun readSamples(id: LapId): List<Sample> {
        val file = File(lapDir(id), "lap.gt7lap")
        require(file.isFile) { "missing telemetry: ${file.path}" }
        return LapTelemetryCodec.decode(file.readBytes())
    }

    fun writeSamples(id: LapId, samples: List<Sample>) {
        val dir = lapDir(id).apply { mkdirs() }
        AtomicFiles.writeBytes(File(dir, "lap.gt7lap"), LapTelemetryCodec.encode(samples))
    }

    fun renameTrack(id: LapId, trackLabel: String): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(trackLabel = trackLabel)
        save(id, updated)
        return updated
    }

    fun setTrack(id: LapId, trackId: String, trackLabel: String): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(
            trackId = trackId.ifBlank { record.trackId },
            trackLabel = trackLabel.ifBlank { record.trackLabel },
        )
        save(id, updated)
        return updated
    }

    fun setDriver(id: LapId, psnId: String?): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(driverPsnId = psnId?.trim()?.takeIf { it.isNotEmpty() })
        save(id, updated)
        return updated
    }

    fun setEvent(id: LapId, event: EventTag?): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(event = event)
        save(id, updated)
        return updated
    }

    fun setTyre(id: LapId, tyre: io.github.jlai403.lapcraft.core.tire.TyreCompound?): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(tyre = tyre)
        save(id, updated)
        return updated
    }

    fun setTcs(id: LapId, tcs: Int?): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(tcs = tcs?.coerceIn(TCS_LEVELS))
        save(id, updated)
        return updated
    }

    fun setBrakeBalance(id: LapId, brakeBalance: Int?): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(brakeBalance = brakeBalance?.coerceIn(BRAKE_BALANCE_LEVELS))
        save(id, updated)
        return updated
    }

    fun attachClip(id: LapId, leadInMs: Int): LapRecord? {
        val record = load(id) ?: return null
        val updated = record.copy(leadInMs = leadInMs)
        save(id, updated)
        val dir = lapDir(id)
        File(dir, "source.mp4").delete()
        deleteScrubArtifacts(dir)
        return updated
    }

    fun setSource(id: LapId, sourceFile: String?): LapRecord? {
        val dir = lapDir(id)
        if (sourceFile.isNullOrBlank()) {
            File(dir, "source.mp4").delete()
        }
        return load(id)
    }

    fun delete(id: LapId): Boolean {
        val dir = lapDir(id)
        if (!dir.exists()) return false
        File(dir, "lap.gt7lap").delete()
        File(dir, "lap.json").delete()
        deleteReplayArtifacts(dir)
        return dir.deleteRecursively()
    }

    fun removeReplay(id: LapId): Boolean {
        val dir = lapDir(id)
        if (!dir.isDirectory) return false
        val hadClip =
            File(dir, "replay.mp4").isFile || File(dir, "source.mp4").isFile ||
                File(dir, "raw.mp4").isFile
        deleteReplayArtifacts(dir)
        load(id)?.let { save(id, it.copy(leadInMs = 0)) }
        return hadClip
    }

    fun summaries(): List<LapSummary> {
        LayoutMigration.migrate(rootDir, carName)
        return list().mapNotNull { id ->
            val record = load(id) ?: return@mapNotNull null
            val dir = lapDir(id)
            val replayFile = File(dir, "replay.mp4").takeIf { it.isFile }
            val telemetryFile = File(dir, "lap.gt7lap").takeIf { it.isFile }
            val pendingFile = File(dir, "source.mp4").takeIf { it.isFile }
            LapSummary(
                lapId = id,
                lapTimeMs = record.lapTimeMs,
                createdAt = record.capturedAt,
                trackId = record.trackId,
                trackLabel = record.trackLabel,
                carName = carName(record.carCode) ?: record.carName,
                hasReplay = replayFile != null,
                replayPending = pendingFile != null,
                driverPsnId = record.driverPsnId,
                event = record.event,
                tyre = record.tyre,
                tcs = record.tcs,
                brakeBalance = record.brakeBalance,
                videoBytes = replayFile?.length() ?: 0,
                telemetryBytes = telemetryFile?.length() ?: 0,
                pendingBytes = pendingFile?.length() ?: 0,
            )
        }.sortedByDescending { parseInstant(it.createdAt) }
    }

    fun deleteScrubArtifacts(dir: File) {
        listOf(".tmp", ".bak", ".compact.mp4", ".reencode.mp4").forEach { suffix ->
            File(dir, "replay.mp4" + suffix).delete()
            File(dir, "source.mp4" + suffix).delete()
        }
        File(dir, "replay.preview").deleteRecursively()
        File(dir, "replay.preview.tmp").deleteRecursively()
        File(dir, "source.preview").deleteRecursively()
        File(dir, "source.preview.tmp").deleteRecursively()
    }

    fun deleteReplayArtifacts(dir: File) {
        File(dir, "replay.mp4").delete()
        File(dir, "source.mp4").delete()
        File(dir, "raw.mp4").delete()
        deleteScrubArtifacts(dir)
    }

    private fun parseInstant(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
}