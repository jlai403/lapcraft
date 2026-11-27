package io.github.jlai403.lapcraft.app.ui

import io.github.jlai403.lapcraft.core.clip.FrameTimeline
import io.github.jlai403.lapcraft.core.clip.SyncOffset
import kotlin.math.roundToLong

internal fun flipTargetMs(cursorMs: Long, startMs: Long, endMs: Long): Long {
    val mid = (startMs + endMs) / 2
    return if (cursorMs <= mid) endMs else startMs
}

internal fun flipLabel(cursorMs: Long, startMs: Long, endMs: Long): String =
    if (cursorMs <= (startMs + endMs) / 2) "Go to end" else "Go to start"

internal fun parseTimer(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0L
    return SyncOffset.parse(trimmed)
}

internal fun frameGapLabel(frameDurationUs: Long): String =
    "%.1f".format(frameDurationUs / 1000.0)

internal fun framePositionLabel(index: Int, total: Int): String =
    "frame $index / $total"

internal fun suggestedLapStartMs(
    durationMs: Long,
    lapSpanMs: Int,
    frames: FrameTimeline?,
    frameStepMs: Long,
): Long {
    val span = lapSpanMs.toLong().coerceAtLeast(1)
    val suggested = (durationMs - span).coerceIn(0L, durationMs.coerceAtLeast(0L))
    return frames?.snapMs(suggested)
        ?: ((suggested / frameStepMs.coerceAtLeast(1)) * frameStepMs)
}

internal fun coarseScrubTargetMs(baseMs: Long, dragDxPx: Float, widthPx: Int, durationMs: Long): Long {
    if (widthPx <= 0 || durationMs <= 0) return baseMs
    val delta = (dragDxPx / widthPx * durationMs).roundToLong()
    return (baseMs + delta).coerceIn(0L, durationMs)
}