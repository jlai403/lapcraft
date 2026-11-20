package io.github.jlai403.lapcraft.core.clip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameTimelineTest {
    private fun cfr(fps: Double, count: Int): LongArray {
        val us = 1_000_000.0 / fps
        return LongArray(count) { (it * us).roundToLong() }
    }

    @Test
    fun `empty input is rejected`() {
        assertNull(FrameTimeline.of(LongArray(0)))
    }

    @Test
    fun `indexAt floors to the containing frame`() {
        val t = FrameTimeline.of(longArrayOf(0, 16_683, 33_366))!!
        assertEquals(0, t.indexAt(-1))
        assertEquals(0, t.indexAt(0))
        assertEquals(0, t.indexAt(16_682))
        assertEquals(1, t.indexAt(16_683))
        assertEquals(2, t.indexAt(100_000))
    }

    @Test
    fun `frameAt returns the containing frame start`() {
        val t = FrameTimeline.of(longArrayOf(0, 16_683, 33_366))!!
        assertEquals(0L, t.frameAt(100))
        assertEquals(16_683L, t.frameAt(20_000))
    }

    @Test
    fun `plus one always lands in the next frame for cfr 59 94`() {
        val t = FrameTimeline.of(cfr(59.94, 120))!!
        for (i in 0 until t.size - 1) {
            val mid = t.midUs(i)
            val idx = t.indexAt(t.targetUs(mid, 1)!!)
            assertEquals(i + 1, idx, "frame $i")
        }
    }

    @Test
    fun `plus one always lands in the next frame for cfr 60`() {
        val t = FrameTimeline.of(cfr(60.0, 120))!!
        for (i in 0 until t.size - 1) {
            val mid = t.midUs(i)
            val idx = t.indexAt(t.targetUs(mid, 1)!!)
            assertEquals(i + 1, idx, "frame $i")
        }
    }

    @Test
    fun `plus one lands in the next frame for a vfr clip`() {
        val pts = longArrayOf(0, 12_000, 40_000, 41_000, 70_000)
        val t = FrameTimeline.of(pts)!!
        for (i in 0 until t.size - 1) {
            val mid = t.midUs(i)
            val idx = t.indexAt(t.targetUs(mid, 1)!!)
            assertEquals(i + 1, idx, "frame $i")
        }
    }

    @Test
    fun `target clamps at both ends`() {
        val t = FrameTimeline.of(cfr(60.0, 5))!!
        val first = t.targetUs(0, -10)!!
        val last = t.targetUs(Long.MAX_VALUE, 10)!!
        assertEquals(t.midUs(0), first)
        assertEquals(t.midUs(t.size - 1), last)
    }

    @Test
    fun `empty timeline returns null target`() {
        assertNull(FrameTimeline.of(LongArray(0))?.targetUs(0, 1))
    }

    @Test
    fun `a ten step burst advances exactly ten frames`() {
        val t = FrameTimeline.of(cfr(59.94, 120))!!
        var us = t.midUs(0)
        repeat(10) { us = t.targetUs(us, 1)!! }
        assertEquals(10, t.indexAt(us))
    }

    @Test
    fun `mid frame aim cannot be defeated by a nominal 60us duration`() {
        // A frame duration that is 16us short (60.0 reported for a 59.94 stream) must not make
        // +1 land on the current frame. Because we never use the duration, this holds by construction.
        val t = FrameTimeline.of(cfr(59.94, 120))!!
        val mid = t.midUs(5)
        val next = t.targetUs(mid, 1)!!
        assertTrue(t.indexAt(next) > t.indexAt(mid))
    }
}

private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()