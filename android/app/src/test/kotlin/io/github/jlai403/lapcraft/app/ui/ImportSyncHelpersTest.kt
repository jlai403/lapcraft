package io.github.jlai403.lapcraft.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSyncHelpersTest {
    @Test
    fun `flip target goes to the other end`() {
        assertEquals(100L, flipTargetMs(0, 0, 100))
        assertEquals(0L, flipTargetMs(100, 0, 100))
        assertEquals(100L, flipTargetMs(40, 0, 100))
        assertEquals(0L, flipTargetMs(60, 0, 100))
    }

    @Test
    fun `flip label is the destination`() {
        assertEquals("Go to end", flipLabel(0, 0, 100))
        assertEquals("Go to start", flipLabel(100, 0, 100))
    }

    @Test
    fun `parseTimer accepts blank as zero and rejects bad format`() {
        assertEquals(0L, parseTimer(""))
        assertEquals(19L, parseTimer("0.019"))
        assertEquals(null, parseTimer("19"))
        assertEquals(null, parseTimer("0.1234"))
    }

    @Test
    fun `frame gap label formats one decimal`() {
        assertEquals("16.7", frameGapLabel(16_683))
        assertEquals("33.4", frameGapLabel(33_366))
    }

    @Test
    fun `frame position label is one based`() {
        assertEquals("frame 1 / 10", framePositionLabel(1, 10))
        assertEquals("frame 10 / 10", framePositionLabel(10, 10))
    }

    @Test
    fun `suggested lap start snaps to a real frame when one is available`() {
        val t = io.github.jlai403.lapcraft.core.clip.FrameTimeline.of(longArrayOf(0, 20_000, 40_000))!!
        assertEquals(0L, suggestedLapStartMs(30, 96, t, 33))
        assertEquals(20L, suggestedLapStartMs(60, 40, t, 33))
    }

    @Test
    fun `suggested lap start falls back to the frame grid without a timeline`() {
        assertEquals(4L, suggestedLapStartMs(100, 96, null, 4))
    }

    @Test
    fun `suggested lap start clamps when the clip is shorter than the lap`() {
        assertEquals(0L, suggestedLapStartMs(30, 96, null, 4))
    }

    @Test
    fun `coarse scrub target is proportional and clamped`() {
        assertEquals(50L, coarseScrubTargetMs(0, 200f, 400, 100))
        assertEquals(100L, coarseScrubTargetMs(90, 100f, 400, 100))
        assertEquals(0L, coarseScrubTargetMs(5, -1000f, 400, 100))
        assertEquals(5L, coarseScrubTargetMs(5, 0f, 0, 100))
    }
}