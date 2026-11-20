package io.github.jlai403.lapcraft.app.ui

import io.github.jlai403.lapcraft.core.clip.FrameTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayControlsTest {
    @Test
    fun `drag distance carries whole frames`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(2, steps.framesFor(25f))
        assertEquals(1, steps.framesFor(5f))
    }

    @Test
    fun `dragging back cancels pending progress`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(-2, steps.framesFor(-25f))
        assertEquals(-1, steps.framesFor(-5f))
    }

    @Test
    fun `reset drops a partial frame`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(1, steps.framesFor(19f))
        steps.reset()
        assertEquals(0, steps.framesFor(9f))
    }

    @Test
    fun `drag totals accumulate across callbacks`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(2, steps.framesFor(25f))
        assertEquals(2, steps.totalFrames)
        assertEquals(1, steps.framesFor(5f))
        assertEquals(3, steps.totalFrames)
    }

    @Test
    fun `reset clears the drag total`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        steps.framesFor(25f)
        steps.reset()

        assertEquals(0, steps.totalFrames)
    }

    @Test
    fun `dragging back lowers the drag total`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        steps.framesFor(25f)

        assertEquals(-1, steps.framesFor(-15f))
        assertEquals(1, steps.totalFrames)
    }

    @Test
    fun `ruler labels show tenths`() {
        assertEquals("0:00.0", formatRulerLabel(0))
        assertEquals("0:32.5", formatRulerLabel(32_500))
        assertEquals("1:30.0", formatRulerLabel(90_000))
    }

    @Test
    fun `frame steps use the exact frame duration`() {
        val frameUs = 16_683L

        assertEquals(17, frameStepTargetMs(0, 1, frameUs))
        assertEquals(34, frameStepTargetMs(16_683, 1, frameUs))
        assertEquals(17, frameStepTargetMs(33_366, -1, frameUs))
        assertEquals(0, frameStepTargetMs(0, -1, frameUs))
    }

    @Test
    fun `playback speed labels drop trailing zeros`() {
        assertEquals("0.5×", playbackSpeedLabel(0.5f))
        assertEquals("1×", playbackSpeedLabel(1f))
        assertEquals("1.5×", playbackSpeedLabel(1.5f))
        assertEquals("2×", playbackSpeedLabel(2f))
    }

    @Test
    fun `frameStepMs prefers the timeline when present`() {
        val pts = LongArray(5) { it * 16_683L }
        val tl = FrameTimeline.of(pts)!!
        assertEquals(17, frameStepMs(0, 1, 16_683, null))
        val stepped = frameStepMs(0, 1, 16_683, tl)
        assertTrue(stepped > 0 && stepped < 34)
    }
}
