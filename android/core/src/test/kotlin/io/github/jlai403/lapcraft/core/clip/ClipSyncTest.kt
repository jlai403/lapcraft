package io.github.jlai403.lapcraft.core.clip

import io.github.jlai403.lapcraft.core.clip.LapClock


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClipSyncTest {
    @Test
    fun `start at the line gives a lap-length trim`() {
        val sync = LapClock.forLapStart(5_269, 159_831)
        assertEquals(5_269L, sync.trimInMs)
        assertEquals(165_100L, sync.trimOutMs)
        assertEquals(0, sync.telemetryMs(5_269L))
        assertEquals(159_831, sync.telemetryMs(165_100L))
        assertEquals(5_269L, sync.clipMs(0))
    }

    @Test
    fun `clip shorter than the lap is blocked`() {
        val sync = LapClock.forLapStart(-9_831, 159_831)
        assertEquals(SyncCheck.ClipTooShort(missingMs = 9_831L), SyncValidator.check(sync, 170_000, 0f))
    }

    @Test
    fun `start past the clip end is blocked`() {
        val sync = LapClock.forLapStart(20_169, 159_831)
        assertEquals(SyncCheck.ClipTooShort(missingMs = 10_000L), SyncValidator.check(sync, 170_000, 0f))
    }

    @Test
    fun `finish frame away from the line is blocked`() {
        val sync = LapClock.forLapStart(5_269, 159_831)
        assertEquals(SyncCheck.EndNotAtLine(41f), SyncValidator.check(sync, 170_000, 41f))
        assertEquals(SyncCheck.Ok, SyncValidator.check(sync, 170_000, 0.4f))
        assertEquals(SyncCheck.Ok, SyncValidator.check(sync, 170_000, 99.6f))
    }
}
