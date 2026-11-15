package io.github.jlai403.lapcraft.app.capture

import io.github.jlai403.lapcraft.core.capture.LapSink
import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapRecord
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LapTimeSource
import java.io.File
import java.time.Instant
import java.util.UUID

/** Writes a captured lap as its own record. Video is attached later. */
class LapSaver(
    private val sessionsDir: File,
    private val idFactory: () -> String = { "lap-" + UUID.randomUUID() },
    private val clock: () -> Instant = { Instant.now() },
    private val trackName: (List<Sample>) -> String? = { null },
    private val driverPsnId: () -> String? = { null },
    private val carName: (Int) -> String? = { null },
    private val eventTag: (Int, String, Long, Int) -> io.github.jlai403.lapcraft.core.events.EventTag? = { _, _, _, _ -> null },
) : LapSink {
    override fun save(boundary: LapBoundary, samples: List<Sample>): String {
        val store = LapStore(sessionsDir, carName = carName)
        val id = idFactory()
        val now = clock()
        val carCode = samples.lastOrNull()?.carCode ?: 0
        val lapSamples = samples.filter { it.timeNanos in boundary.startNanos..boundary.endNanos }
        val resolvedTrack = trackName(lapSamples) ?: "Unknown"
        val record = LapRecord(
            capturedAt = now.toString(),
            lapTimeMs = boundary.lapTimeMs,
            lapTimeSource = LapTimeSource.Reported,
            circuitId = "",
            circuitLabel = resolvedTrack,
            carCode = carCode,
            carName = carName(carCode) ?: "Car #$carCode",
            startTickNanos = boundary.startNanos,
            endTickNanos = boundary.endNanos,
            driverPsnId = driverPsnId()?.trim()?.takeIf { it.isNotEmpty() },
            event = runCatching {
                eventTag(carCode, resolvedTrack, now.toEpochMilli(), boundary.lapTimeMs)
            }.getOrNull(),
        )
        store.save(LapId(id), record)
        val stored = samples.filter {
            it.timeNanos >= boundary.startNanos - 2_000_000_000L &&
                it.timeNanos <= boundary.endNanos + 1_000_000_000L
        }
        store.writeSamples(LapId(id), stored)
        return id
    }
}