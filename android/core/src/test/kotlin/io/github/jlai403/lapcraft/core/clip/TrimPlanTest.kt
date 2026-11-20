package io.github.jlai403.lapcraft.core.clip

import io.github.jlai403.lapcraft.core.clip.FrameTimeline


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrimPlanTest {
    @Test
    fun `plan rejects a clip shorter than the lap`() {
        val sync = LapClock.forLapStart(-9_831, 159_831)
        assertNull(TrimPlan.forSync(sync, sourceDurationMs = 170_000))
    }

    @Test
    fun `plan returns the exact lap range`() {
        val sync = LapClock.forLapStart(5_269, 159_831)
        assertEquals(
            TrimRange(5_269L, 165_100L),
            TrimPlan.forSync(sync, sourceDurationMs = 170_000),
        )
    }

    @Test
    fun `plan rejects a start past the end of the clip`() {
        val sync = LapClock.forLapStart(20_169, 159_831)
        assertNull(TrimPlan.forSync(sync, sourceDurationMs = 170_000))
    }

    @Test
    fun `snapped start is a real frame and preserves lap zero`() {
        val pts = LongArray(12_001) { it * 16_683L }
        val frames = FrameTimeline.of(pts)!!
        val sync = LapClock.forLapStart(5_269, 159_831)
        val range = TrimPlan.forSync(sync, 170_000, frames)!!
        // snapped start is the rounded PTS of the frame containing the intended start.
        assertEquals((frames.frameAt(5_269 * 1_000) + 500) / 1_000, range.startMs)
        // within one frame of the intended start.
        assertTrue(kotlin.math.abs(range.startMs - 5_269) <= 17)
        // lap zero maps exactly onto the snapped start.
        val clock = LapClock.forLapStart(range.startMs, 159_831)
        assertEquals(0, clock.telemetryMs(range.startMs))
    }

    @Test
    fun `snapped end keeps the crossing frame`() {
        val pts = LongArray(12_001) { it * 16_683L }
        val frames = FrameTimeline.of(pts)!!
        val sync = LapClock.forLapStart(5_269, 159_831)
        val range = TrimPlan.forSync(sync, 170_000, frames)!!
        assertEquals((frames.frameAt(165_100 * 1_000) + 500) / 1_000, range.endMs)
        assertTrue(kotlin.math.abs(range.endMs - 165_100) <= 17)
    }
}
