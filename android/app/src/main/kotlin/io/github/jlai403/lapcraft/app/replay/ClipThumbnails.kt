package io.github.jlai403.lapcraft.app.replay

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

data class ClipFrame(val timeMs: Long, val bitmap: Bitmap)

/** Fractional crop of the GT7 replay HUD lap timer (top-centre pill). */
const val HUD_TIMER_LEFT = 0.42f
const val HUD_TIMER_RIGHT = 0.58f
const val HUD_TIMER_TOP = 0.215f
const val HUD_TIMER_BOTTOM = 0.28f

/** Evenly spaced frame times across [startMs, endMs], inclusive of both ends. */
fun filmstripTimes(startMs: Long, endMs: Long, count: Int): List<Long> {
    if (count < 2 || endMs <= startMs) return emptyList()
    val span = endMs - startMs
    return (0 until count).map { i ->
        startMs + (i.toLong() * span / (count - 1))
    }
}

/** Pixel crop rect for the HUD timer within a decoded frame of [width]x[height]. */
fun hudCropRect(width: Int, height: Int): IntArray {
    val left = (width * HUD_TIMER_LEFT).toInt().coerceIn(0, width - 1)
    val right = (width * HUD_TIMER_RIGHT).toInt().coerceIn(left + 1, width)
    val top = (height * HUD_TIMER_TOP).toInt().coerceIn(0, height - 1)
    val bottom = (height * HUD_TIMER_BOTTOM).toInt().coerceIn(top + 1, height)
    return intArrayOf(left, top, right - left, bottom - top)
}

fun readThumbnails(file: File, timesMs: List<Long>, widthPx: Int, heightPx: Int): List<ClipFrame> {
    if (!file.isFile || timesMs.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        timesMs.mapNotNull { ms ->
            runCatching {
                val full = retriever.getScaledFrameAtTime(
                    ms * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    widthPx,
                    heightPx,
                )
                full?.let {
                    val (cx, cy, cw, ch) = hudCropRect(it.width, it.height)
                    val cropped = Bitmap.createBitmap(it, cx, cy, cw, ch)
                    if (cropped !== it) it.recycle()
                    ClipFrame(ms, cropped)
                }
            }.getOrNull()
        }
    } catch (_: Throwable) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}