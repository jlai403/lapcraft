package io.github.jlai403.lapcraft.core.clip

/**
 * The real presentation timestamps of a clip's video frames, in microseconds.
 *
 * Stepping by [targetUs] uses frame indices, so a press-and-hold walks exactly one frame per
 * repeat and never accumulates a duration error. Seek targets are aimed at the middle of the
 * target frame, which is unambiguous under EXACT seek and therefore can never land back on the
 * frame we just left.
 */
class FrameTimeline private constructor(private val pts: LongArray) {
    val size: Int get() = pts.size

    fun indexAt(us: Long): Int {
        if (size == 0) return 0
        if (us < pts[0]) return 0
        val found = pts.binarySearch(us)
        return if (found >= 0) found else (-found - 2).coerceIn(0, size - 1)
    }

    fun frameStartUs(index: Int): Long = pts[index.coerceIn(0, size - 1)]

    fun frameAt(us: Long): Long = if (size == 0) us else frameStartUs(indexAt(us))

    fun midUs(index: Int): Long {
        val i = index.coerceIn(0, size - 1)
        val span = if (i < size - 1) pts[i + 1] - pts[i]
        else if (size >= 2) pts[size - 1] - pts[size - 2]
        else 16_683L
        return pts[i] + span / 2
    }

    fun targetUs(us: Long, frames: Int): Long? {
        if (size == 0) return null
        val i = (indexAt(us) + frames).coerceIn(0, size - 1)
        return midUs(i)
    }

    companion object {
        fun of(ptsUs: LongArray): FrameTimeline? {
            if (ptsUs.isEmpty()) return null
            val sorted = ptsUs.sorted().distinct().toLongArray()
            if (sorted.isEmpty()) return null
            return FrameTimeline(sorted)
        }
    }
}