package io.github.jlai403.lapcraft.app.replay

import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.jlai403.lapcraft.core.clip.FrameTimeline
import java.io.File

/**
 * Reads the video track's real frame presentation timestamps from the container's sample table.
 * Pure extraction, no decoding; returns null on any failure so callers fall back to the estimate.
 */
fun readFrameTimes(file: File): FrameTimeline? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(file.absolutePath)
        var videoIndex = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) { videoIndex = i; break }
        }
        if (videoIndex < 0) return null
        extractor.selectTrack(videoIndex)
        val times = ArrayList<Long>()
        while (true) {
            val time = extractor.sampleTime
            if (time < 0) break
            times.add(time)
            if (!extractor.advance()) break
        }
        FrameTimeline.of(times.toLongArray())
    } catch (_: Exception) {
        null
    } finally {
        runCatching { extractor.release() }
    }
}