package io.github.jlai403.lapcraft.core.clip

import kotlin.math.abs

sealed interface SyncCheck {
    data object Ok : SyncCheck
    data class ClipTooShort(val missingMs: Long) : SyncCheck
    data class EndNotAtLine(val trackPercent: Float) : SyncCheck
}

object SyncValidator {
    fun check(
        clock: LapClock,
        sourceDurationMs: Long,
        endTrackPercent: Float,
        tolerancePercent: Float = 2f,
    ): SyncCheck {
        if (clock.trimInMs < 0) return SyncCheck.ClipTooShort(-clock.trimInMs)
        if (clock.trimOutMs > sourceDurationMs) return SyncCheck.ClipTooShort(clock.trimOutMs - sourceDurationMs)
        if (abs(endTrackPercent) > tolerancePercent &&
            abs(endTrackPercent - 100f) > tolerancePercent
        ) {
            return SyncCheck.EndNotAtLine(endTrackPercent)
        }
        return SyncCheck.Ok
    }
}
