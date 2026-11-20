package io.github.jlai403.lapcraft.core.clip

/**
 * Decides when a trim has read everything it needs from a source clip.
 *
 * MediaExtractor returns samples in decode order, so presentation times are not monotonic: a
 * video frame past the trim end can be followed by B-frames inside it. Stopping on that first
 * late frame drops the tail of the lap, so reading continues until the video is a guard interval
 * past the end, which is wider than any B-frame reorder window.
 */
class TrimTail(endUs: Long) {
    private val stopUs = endUs + TAIL_GUARD_US
    private var lastVideoUs = Long.MIN_VALUE

    /** Returns true once the video track is [TAIL_GUARD_US] past the trim end. */
    fun onSample(video: Boolean, timeUs: Long): Boolean {
        if (!video) return false
        if (timeUs > lastVideoUs) lastVideoUs = timeUs
        return lastVideoUs > stopUs
    }

    companion object {
        const val TAIL_GUARD_US = 1_000_000L
    }
}
