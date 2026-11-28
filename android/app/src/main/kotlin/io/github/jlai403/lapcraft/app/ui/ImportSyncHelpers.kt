package io.github.jlai403.lapcraft.app.ui

import io.github.jlai403.lapcraft.core.clip.SyncOffset

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