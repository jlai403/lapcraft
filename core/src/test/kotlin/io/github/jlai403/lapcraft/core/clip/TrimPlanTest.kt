package io.github.jlai403.lapcraft.core.clip

import io.github.jlai403.lapcraft.core.clip.LapClock


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
