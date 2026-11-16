package io.github.jlai403.lapcraft.core.clip

import kotlin.math.abs

sealed interface SyncCheck {
    data object Ok : SyncCheck
    data class ClipTooShort(val missingMs: Long) : SyncCheck
    data class StartNotAtLine(val trackPercent: Float) : SyncCheck
}

object SyncValidator {
    fun check(
        clock: LapClock,
        sourceDurationMs: Long,
        startTrackPercent: Float,
        tolerancePercent: Float = 2f,
    ): SyncCheck {
        if (clock.trimInMs < 0) return SyncCheck.ClipTooShort(-clock.trimInMs)
        if (clock.trimOutMs > sourceDurationMs) return SyncCheck.ClipTooShort(clock.trimOutMs - sourceDurationMs)
        if (abs(startTrackPercent) > tolerancePercent &&
            abs(startTrackPercent - 100f) > tolerancePercent
        ) {
            return SyncCheck.StartNotAtLine(startTrackPercent)
        }
        return SyncCheck.Ok
    }
}
