package io.github.jlai403.lapcraft.core.capture

import io.github.jlai403.lapcraft.core.session.LapId

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Capturing : CaptureState
    data class Saved(val lapId: LapId, val lapTimeMs: Int) : CaptureState
    data class Rejected(val reason: String) : CaptureState
}
