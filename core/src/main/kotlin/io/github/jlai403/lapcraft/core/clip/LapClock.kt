package io.github.jlai403.lapcraft.core.clip

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

        fun forLapStart(startMs: Long, lapTimeMs: Int): LapClock =
            LapClock(startMs, lapTimeMs)
    }
}
