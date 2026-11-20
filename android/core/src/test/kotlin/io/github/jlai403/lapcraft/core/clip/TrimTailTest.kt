package io.github.jlai403.lapcraft.core.clip

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrimTailTest {
    private fun tail(endMs: Long) = TrimTail(endMs * 1_000)

    @Test
    fun `late P-frame does not end the read before trailing B-frames`() {
        val tail = tail(1_000)
        // Decode order: 0, 50, 33, 16 ms, as a B-frame GOP lays them out.
        assertFalse(tail.onSample(video = true, timeUs = 983_000))
        assertFalse(tail.onSample(video = true, timeUs = 1_016_000))
        assertFalse(tail.onSample(video = true, timeUs = 1_000_000))
        assertFalse(tail.onSample(video = true, timeUs = 983_300))
    }

    @Test
    fun `read stops a guard interval past the end`() {
        val tail = tail(1_000)
        assertFalse(tail.onSample(video = true, timeUs = 1_500_000))
        assertTrue(tail.onSample(video = true, timeUs = 2_001_000))
    }

    @Test
    fun `audio samples never end the read`() {
        val tail = tail(1_000)
        assertFalse(tail.onSample(video = false, timeUs = 9_000_000))
    }

    @Test
    fun `guard covers a B-frame reorder window`() {
        val fourFramesAt60 = 4 * 16_683L
        assertTrue(TrimTail.TAIL_GUARD_US > fourFramesAt60 * 4)
    }
}
