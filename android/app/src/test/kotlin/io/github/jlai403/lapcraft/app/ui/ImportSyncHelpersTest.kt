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
}