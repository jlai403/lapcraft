package io.github.jlai403.lapcraft.core.lap

import io.github.jlai403.lapcraft.core.model.Sample
import kotlin.math.abs

data class LapBoundary(
    val lapNumber: Int,
    val startNanos: Long,
    val endNanos: Long,
    val lapTimeMs: Int,
    val lapTimeSource: LapTimeSource = LapTimeSource.Reported,
)

sealed interface CurrentLap {
    data object None : CurrentLap
    data class FromLine(val startNanos: Long) : CurrentLap
    data class MidLap(val startNanos: Long) : CurrentLap
}

class LapDetector {
    private var previousLapCount: Int? = null
    private var lastPacketId: Int? = null
    private var lastTimeNanos: Long = Long.MIN_VALUE

    @Volatile
    var currentLap: CurrentLap = CurrentLap.None
        private set

    fun onSample(sample: Sample): LapBoundary? {
        if (sample.packetId == lastPacketId) return null
        if (sample.timeNanos <= lastTimeNanos) return null

        val previous = previousLapCount
        if (previous == null) {
            previousLapCount = sample.lapCount
            currentLap = CurrentLap.MidLap(sample.timeNanos)
            lastPacketId = sample.packetId
            lastTimeNanos = sample.timeNanos
            return null
        }

        if (sample.lapCount < previous) {
            previousLapCount = sample.lapCount
            currentLap = CurrentLap.MidLap(sample.timeNanos)
            lastPacketId = sample.packetId
            lastTimeNanos = sample.timeNanos
            return null
        }

        var boundary: LapBoundary? = null
        if (sample.lapCount > previous) {
            val lapNumber = sample.lapCount - 1
            if (lapNumber > 0) {
                val startNanos = (currentLap as? CurrentLap.MidLap)?.startNanos
                    ?: (currentLap as? CurrentLap.FromLine)?.startNanos
                    ?: sample.timeNanos
                val measuredMs = ((sample.timeNanos - startNanos) / 1_000_000L).toInt()
                val reported = sample.lastLapTimeMs
                val lapTimeMs = if (reported > 0) reported else measuredMs
                val source = if (reported > 0) LapTimeSource.Reported else LapTimeSource.Measured
                val complete = reported <= 0 ||
                    abs(measuredMs - reported) <= LAP_SPAN_TOLERANCE_MS
                if (complete && lapTimeMs in MIN_PLAUSIBLE_LAP_MS..MAX_PLAUSIBLE_LAP_MS) {
                    boundary = LapBoundary(
                        lapNumber = lapNumber,
                        startNanos = startNanos,
                        endNanos = sample.timeNanos,
                        lapTimeMs = lapTimeMs,
                        lapTimeSource = source,
                    )
                }
            }
            currentLap = CurrentLap.FromLine(sample.timeNanos)
        }

        previousLapCount = sample.lapCount
        lastPacketId = sample.packetId
        lastTimeNanos = sample.timeNanos
        return boundary
    }

    private companion object {
        const val MIN_PLAUSIBLE_LAP_MS = 10_000
        const val MAX_PLAUSIBLE_LAP_MS = 1_800_000
        const val LAP_SPAN_TOLERANCE_MS = 2_000
    }
}
