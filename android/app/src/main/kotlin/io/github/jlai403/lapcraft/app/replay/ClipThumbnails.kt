package io.github.jlai403.lapcraft.app.replay

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

data class ClipFrame(val timeMs: Long, val bitmap: Bitmap)

/** Evenly spaced frame times across [startMs, endMs], inclusive of both ends. */
fun filmstripTimes(startMs: Long, endMs: Long, count: Int): List<Long> {
    if (count < 2 || endMs <= startMs) return emptyList()
    val span = endMs - startMs
    return (0 until count).map { i ->
        startMs + (i.toLong() * span / (count - 1))
    }
}

fun readThumbnails(file: File, timesMs: List<Long>, widthPx: Int, heightPx: Int): List<ClipFrame> {
    if (!file.isFile || timesMs.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        timesMs.mapNotNull { ms ->
            runCatching {
                retriever.getScaledFrameAtTime(ms * 1_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, widthPx, heightPx)
            }.getOrNull()?.let { ClipFrame(ms, it) }
        }
    } catch (_: Throwable) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}