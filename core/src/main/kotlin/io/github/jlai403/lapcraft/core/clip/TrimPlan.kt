package io.github.jlai403.lapcraft.core.clip

data class TrimRange(val startMs: Long, val endMs: Long)

object TrimPlan {
    fun forSync(
        clock: LapClock,
        sourceDurationMs: Long,
        frames: FrameTimeline? = null,
    ): TrimRange? {
        if (clock.trimInMs < 0) return null
        if (clock.trimOutMs > sourceDurationMs) return null
        if (clock.trimOutMs <= clock.trimInMs) return null
        if (frames == null) return TrimRange(clock.trimInMs, clock.trimOutMs)
        return TrimRange(
            (frames.frameAt(clock.trimInMs * 1_000) + 500) / 1_000,
            (frames.frameAt(clock.trimOutMs * 1_000) + 500) / 1_000,
        )
    }
}