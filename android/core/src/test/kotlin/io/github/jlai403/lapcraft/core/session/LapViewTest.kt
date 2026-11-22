package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.tire.TyreCompound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LapViewTest {
    private fun lap(lapTimeMs: Int, spanMs: Long) = LapView(
        lapTimeMs = lapTimeMs,
        startNanos = 1_000_000_000L,
        endNanos = 1_000_000_000L + spanMs * 1_000_000L,
        leadInMs = 0,
    )

    @Test
    fun `span comes from the lap's boundary ticks`() {
        // GT7 counts in 60ths; a 59.94 Hz capture runs the lap about 0.1% longer.
        val lap = lap(lapTimeMs = 123_333, spanMs = 123_456)
        assertEquals(123_456, lap.lapSpanMs)
        assertEquals(123_456L, lap.lapClock().trimOutMs)
    }

    @Test
    fun `missing ticks fall back to the reported lap time`() {
        val lap = LapView(lapTimeMs = 90_000, startNanos = 0, endNanos = 0, leadInMs = 0)
        assertEquals(90_000, lap.lapSpanMs)
    }

    @Test
    fun `implausible ticks fall back to the reported lap time`() {
        assertEquals(90_000, lap(lapTimeMs = 90_000, spanMs = 5_000).lapSpanMs)
        assertEquals(90_000, lap(lapTimeMs = 90_000, spanMs = 600_000).lapSpanMs)
    }

    @Test
    fun `setup metadata projects into the lap view`() {
        val record = LapRecord(
            capturedAt = "2026-11-22T10:00:00Z",
            lapTimeMs = 90_000,
            tyre = TyreCompound.RACING_SOFT,
            tcs = 2,
            brakeBalance = -1,
        )

        val view = record.toLapView()
        assertEquals(TyreCompound.RACING_SOFT, view.tyre)
        assertEquals(2, view.tcs)
        assertEquals(-1, view.brakeBalance)
    }
}
