package io.github.jlai403.lapcraft.app.ui

import android.content.Context
import io.github.jlai403.lapcraft.core.lap.LapTimeSource
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapRecord
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LapTelemetryCodec
import java.io.File

object SessionFixtures {

    data class SessionSpec(
        val id: String = "session-1",
        val trackLabel: String = "Suzuka",
        val carCode: Int = 3474,
        val carName: String = "BRZ GT300 '21",
        val createdAt: String = "2026-11-10T10:00:00Z",
        val lapTimesMs: List<Int> = listOf(90_000, 91_500),
        val needsCut: Boolean = false,
        val withReplay: Boolean = true,
        val pendingSource: Boolean = false,
        val driverPsnId: String? = null,
    )

    // Backwards-compatible alias for tests that call SessionFixtures.session(...)
    fun session(
        id: String = "session-1",
        trackLabel: String = "Suzuka",
        carCode: Int = 3474,
        carName: String = "BRZ GT300 '21",
        createdAt: String = "2026-11-10T10:00:00Z",
        lapTimesMs: List<Int> = listOf(90_000, 91_500),
        needsCut: Boolean = false,
        withReplay: Boolean = true,
        pendingSource: Boolean = false,
        driverPsnId: String? = null,
    ): SessionSpec = SessionSpec(
        id = id,
        trackLabel = trackLabel,
        carCode = carCode,
        carName = carName,
        createdAt = createdAt,
        lapTimesMs = lapTimesMs,
        needsCut = needsCut,
        withReplay = withReplay,
        pendingSource = pendingSource,
        driverPsnId = driverPsnId,
    )

    fun install(context: Context, metadata: SessionSpec) {
        val store = LapStore(context.filesDir)
        metadata.lapTimesMs.forEachIndexed { index, lapTime ->
            val lapId = LapId(if (metadata.lapTimesMs.size == 1) metadata.id else "${metadata.id}-l${index + 1}")
            val startNanos = index * 100_000_000_000L
            val endNanos = startNanos + lapTime * 1_000_000L
            val record = LapRecord(
                schemaVersion = 3,
                capturedAt = metadata.createdAt,
                lapTimeMs = lapTime,
                lapTimeSource = LapTimeSource.Reported,
                trackId = "",
                trackLabel = metadata.trackLabel,
                carCode = metadata.carCode,
                carName = metadata.carName,
                startTickNanos = startNanos,
                endTickNanos = endNanos,
                leadInMs = 2_000,
                driverPsnId = metadata.driverPsnId,
            )
            store.save(lapId, record)
            store.writeSamples(lapId, samplesFor(startNanos, endNanos, lapTime))
            val dir = store.lapDir(lapId)
            if (metadata.withReplay && !metadata.needsCut) {
                File(dir, "replay.mp4").writeText("video")
            }
            if (metadata.needsCut) {
                File(dir, "source.mp4").writeText("raw")
            }
            if (metadata.pendingSource) {
                File(dir, "source.mp4").writeText("source")
            }
        }
    }

    fun sample(
        timeNanos: Long,
        speedMps: Float = 50f,
        throttle: Int = 200,
        brake: Int = 10,
        gear: Int = 4,
        rpm: Float = 7_000f,
        x: Float = 0f,
        z: Float = 0f,
        steering: Float? = null,
    ) = Sample(
        timeNanos = timeNanos,
        speedMps = speedMps,
        throttle = throttle,
        brake = brake,
        gear = gear,
        rpm = rpm,
        positionX = x,
        positionY = 0f,
        positionZ = z,
        lapCount = 1,
        bestLapTimeMs = 90_000,
        lastLapTimeMs = 90_000,
        flags = 1,
        carCode = 3474,
        fuel = 40f,
        tyreTempFL = 80f,
        tyreTempFR = 81f,
        tyreTempRL = 82f,
        tyreTempRR = 83f,
        packetId = 1,
        steering = steering,
    )

    private fun samplesFor(startNanos: Long, endNanos: Long, lapTimeMs: Int): List<Sample> {
        val span = (endNanos - startNanos).coerceAtLeast(1_000_000L)
        return (0 until 100).map { index ->
            val fraction = index / 99f
            val angle = fraction * Math.PI.toFloat() * 2f
            sample(
                timeNanos = startNanos + (span * index / 99),
                speedMps = 40f + 30f * fraction,
                brake = if (fraction in 0.2f..0.35f) 200 else 0,
                x = 100f * kotlin.math.cos(angle),
                z = 100f * kotlin.math.sin(angle),
            )
        }
    }
}

