package io.github.jlai403.lapcraft.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LapPlayerStartTest {
    @Test
    fun `initial clip position is the lap lead-in when duration is known`() {
        assertEquals(2_000L, initialClipMs(2_000, 90_000, 100_000))
    }

    @Test
    fun `unknown duration does not clamp the lead-in to zero`() {
        assertEquals(2_000L, initialClipMs(2_000, 90_000, 0))
    }

    @Test
    fun `lead-in beyond the clip clamps to the duration`() {
        assertEquals(100_000L, initialClipMs(120_000, 90_000, 100_000))
    }

    @Test
    fun `zero lead-in starts at clip zero`() {
        assertEquals(0L, initialClipMs(0, 90_000, 100_000))
    }
}