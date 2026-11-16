package io.github.jlai403.lapcraft.core.clip

data class LapClock(
    val startFrameMs: Long,
    val lapTimeMs: Int,
) {
    fun telemetryMs(clipMs: Long): Int =
        (clipMs - startFrameMs).toInt().coerceIn(0, lapTimeMs)

    fun clipMs(lapMs: Int): Long = lapMs + startFrameMs

    val trimInMs: Long get() = startFrameMs
    val trimOutMs: Long get() = startFrameMs + lapTimeMs

    companion object {
        fun forTrimmedLap(leadInMs: Int, lapTimeMs: Int): LapClock =
            LapClock(leadInMs.toLong(), lapTimeMs)

        fun forLapStart(startMs: Long, lapTimeMs: Int): LapClock =
            LapClock(startMs, lapTimeMs)
    }
}
