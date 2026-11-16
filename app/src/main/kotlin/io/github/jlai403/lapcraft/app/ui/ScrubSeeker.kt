package io.github.jlai403.lapcraft.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Exact seeks re-decode from the previous keyframe, so the wait is deliberately longer than a typical decode.
internal const val EXACT_FRAME_WAIT_MS = 400L

internal const val SCRUB_EXACT_WAIT_MS = 120L

internal class ScrubSeeker(
    scope: CoroutineScope,
    private val exactWaitMs: Long = EXACT_FRAME_WAIT_MS,
    private val seek: (Long) -> Unit,
) {
    private val targets = Channel<Long>(Channel.CONFLATED)
    private val rendered = Channel<Unit>(Channel.CONFLATED)
    private var lastSeeked: Long? = null

    init {
        scope.launch {
            for (target in targets) {
                if (target == lastSeeked) continue
                lastSeeked = target
                while (rendered.tryReceive().isSuccess) Unit
                seek(target)
                withTimeoutOrNull(exactWaitMs) { rendered.receive() }
            }
        }
    }

    fun submit(targetMs: Long) {
        targets.trySend(targetMs)
    }

    fun onFrameRendered() {
        rendered.trySend(Unit)
    }

    fun clearPending() {
        while (targets.tryReceive().isSuccess) Unit
        lastSeeked = null
    }
}
