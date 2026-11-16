package io.github.jlai403.lapcraft.core.clip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LapClockTest {
    @Test
    fun `pre-roll maps to zero and the line maps to lap start`() {
        val clock = LapClock.forTrimmedLap(2_000, 90_000)
        assertEquals(0, clock.telemetryMs(0))
        assertEquals(0, clock.telemetryMs(2_000))
        assertEquals(1_500, clock.telemetryMs(3_500))
    }

    @Test
    fun `clamps to the lap duration`() {
        val clock = LapClock.forTrimmedLap(2_000, 90_000)
        assertEquals(90_000, clock.telemetryMs(200_000))
        assertEquals(0, clock.telemetryMs(-5_000))
    }

    @Test
    fun `clip time inverts the telemetry cursor`() {
        val clock = LapClock.forTrimmedLap(2_000, 90_000)
        assertEquals(2_000L, clock.clipMs(0))
        assertEquals(32_000L, clock.clipMs(30_000))
    }

    @Test
    fun `trimmed and lap-start clocks agree`() {
        val trimmed = LapClock.forTrimmedLap(2_000, 90_000)
        val anchored = LapClock.forLapStart(2_000L, 90_000)
        assertEquals(trimmed.telemetryMs(3_500), anchored.telemetryMs(3_500))
        assertEquals(trimmed.clipMs(30_000), anchored.clipMs(30_000))
        assertEquals(trimmed.trimInMs, anchored.trimInMs)
        assertEquals(trimmed.trimOutMs, anchored.trimOutMs)
    }

    @Test
    fun `trim range spans lead-in to lead-in plus lap`() {
        val clock = LapClock.forTrimmedLap(2_000, 90_000)
        assertEquals(2_000L, clock.trimInMs)
        assertEquals(92_000L, clock.trimOutMs)
        assertEquals(2_000L, clock.clipMs(0))
    }

    @Test
    fun `round trip is identity`() {
        val clock = LapClock.forTrimmedLap(2_000, 90_000)
        assertEquals(3_100, clock.telemetryMs(clock.clipMs(3_100)))
    }
}