package io.github.jlai403.lapcraft.core.clip

/**
 * Maps clip time to lap time for one clip of one lap.
 *
 * [startFrameMs] is the clip time at which the lap starts. For a trimmed replay that is the
 * lead-in; for a source clip it is the anchor minus the lap time. [syncOffsetMs] is a playback
 * nudge that shifts telemetry later (positive) or earlier (negative). The trim range is the
 * start frame to the end of the lap and deliberately ignores the nudge, because the cut point
 * is the start frame, not the nudged playback position.
 */
data class LapClock(
    val startFrameMs: Long,
    val lapTimeMs: Int,
    val syncOffsetMs: Int = 0,
) {
    fun telemetryMs(clipMs: Long): Int =
        (clipMs - startFrameMs + syncOffsetMs).toInt().coerceIn(0, lapTimeMs)

    fun clipMs(lapMs: Int): Long = lapMs + startFrameMs - syncOffsetMs

    val trimInMs: Long get() = startFrameMs
    val trimOutMs: Long get() = startFrameMs + lapTimeMs

    companion object {
        fun forTrimmedLap(leadInMs: Int, syncOffsetMs: Int, lapTimeMs: Int): LapClock =
            LapClock(leadInMs.toLong(), lapTimeMs, syncOffsetMs)

        fun forAnchor(anchorMs: Long, lapTimeMs: Int): LapClock =
            LapClock(anchorMs - lapTimeMs, lapTimeMs)
    }
}
