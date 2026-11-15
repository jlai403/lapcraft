package io.github.jlai403.lapcraft.app.capture

import io.github.jlai403.lapcraft.core.capture.LapSink
import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.SessionMetadata
import io.github.jlai403.lapcraft.core.session.SessionWriter
import java.io.File
import java.time.Instant
import java.util.UUID

/** Writes a captured lap as a one-lap session. Video is attached later. */
class LapSaver(
    private val sessionsDir: File,
    private val idFactory: () -> String = { "lap-" + UUID.randomUUID() },
    private val clock: () -> Instant = { Instant.now() },
    private val trackName: (List<Sample>) -> String? = { null },
    private val driverPsnId: () -> String? = { null },
    private val carName: (Int) -> String? = { null },
    private val eventTag: (Int, String, Long, Int) -> io.github.jlai403.lapcraft.core.events.EventTag? = { _, _, _, _ -> null },
) : LapSink {
    override fun save(boundary: LapBoundary, samples: List<Sample>): SessionMetadata {
        val id = idFactory()
        val now = clock()
        val carCode = samples.lastOrNull()?.carCode ?: 0
        val lapSamples = samples.filter { it.timeNanos in boundary.startNanos..boundary.endNanos }
        val resolvedTrack = trackName(lapSamples) ?: "Unknown"
        val metadata = SessionMetadata(
            sessionId = id,
            createdAt = now.toString(),
            trackLabel = resolvedTrack,
            carCode = carCode,
            carName = carName(carCode) ?: "Car #$carCode",
            rawFile = "",
            rawDeleted = true,
        )
        val writer = SessionWriter(File(sessionsDir, id), metadata)
        samples.forEach(writer::addSample)
        writer.writeLap(
            boundary,
            videoFile = "",
            leadInMs = 0,
            driverPsnId = driverPsnId(),
            event = runCatching {
                eventTag(carCode, resolvedTrack, now.toEpochMilli(), boundary.lapTimeMs)
            }.getOrNull(),
        )
        writer.finalize(rawDeleted = true)
        return writer.metadata()
    }
}
