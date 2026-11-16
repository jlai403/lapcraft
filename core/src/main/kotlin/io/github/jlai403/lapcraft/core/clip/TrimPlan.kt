package io.github.jlai403.lapcraft.core.clip

data class TrimRange(val startMs: Long, val endMs: Long)

object TrimPlan {
    /** Null when the clip does not contain the whole lap. */
    fun forSync(clock: LapClock, sourceDurationMs: Long): TrimRange? {
        if (clock.trimInMs < 0) return null
        if (clock.trimOutMs > sourceDurationMs) return null
        if (clock.trimOutMs <= clock.trimInMs) return null
        return TrimRange(clock.trimInMs, clock.trimOutMs)
    }
}
