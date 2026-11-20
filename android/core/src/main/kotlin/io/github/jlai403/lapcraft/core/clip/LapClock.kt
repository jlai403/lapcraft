package io.github.jlai403.lapcraft.core.clip

/**
 * Maps between a clip's milliseconds and the lap's two time domains.
 *
 * [lapTimeMs] is GT7's own timer, which counts in 60ths of a second. A 59.94 Hz capture makes
 * the lap slightly longer than that (about 1 ms per second, 120 ms on a two minute lap), so the
 * clip window is [spanMs], the real elapsed time between the lap's boundary ticks. Using
 * [lapTimeMs] for the window leaves the last few frames of the lap out of the replay.
 */
data class LapClock(
    val startFrameMs: Long,
    val lapTimeMs: Int,
    val spanMs: Int = lapTimeMs,
) {
    private val span: Int get() = spanMs.coerceAtLeast(1)

    /** Real milliseconds since the lap start at [clipMs]; matches telemetry sample times. */
    fun telemetryMs(clipMs: Long): Int =
        (clipMs - startFrameMs).toInt().coerceIn(0, span)

    /** GT7's timer at [clipMs]. It reaches [lapTimeMs] exactly at the end of the lap. */
    fun lapMs(clipMs: Long): Int =
        (telemetryMs(clipMs).toLong() * lapTimeMs / span).toInt().coerceIn(0, lapTimeMs)

    /** Clip milliseconds for real elapsed [telemetryMs]. */
    fun clipMs(telemetryMs: Int): Long = telemetryMs + startFrameMs

    /** Clip milliseconds for a GT7 timer value; the inverse of [lapMs]. */
    fun clipAtLapMs(lapMs: Int): Long =
        startFrameMs + lapMs.toLong() * span / lapTimeMs.coerceAtLeast(1)

    val trimInMs: Long get() = startFrameMs

    val trimOutMs: Long get() = startFrameMs + span

    /** Last clip millisecond the lap covers. */
    val endClipMs: Long get() = trimOutMs

    companion object {
        fun forTrimmedLap(leadInMs: Int, lapTimeMs: Int, spanMs: Int = lapTimeMs): LapClock =
            LapClock(leadInMs.toLong(), lapTimeMs, spanMs)

        fun forLapStart(startMs: Long, lapTimeMs: Int, spanMs: Int = lapTimeMs): LapClock =
            LapClock(startMs, lapTimeMs, spanMs)
    }
}
