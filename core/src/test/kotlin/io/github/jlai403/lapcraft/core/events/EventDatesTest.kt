package io.github.jlai403.lapcraft.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class EventDatesTest {
    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `ISO week of a Monday start`() {
        assertEquals(47, isoWeekOf(epoch("2026-11-16T07:00:00Z")))
    }

    @Test
    fun `ISO week is stable across the week`() {
        assertEquals(47, isoWeekOf(epoch("2026-11-19T23:00:00Z")))
    }

    @Test
    fun `week label names the Monday`() {
        assertEquals("Week of Nov 16", weekStartLabel(epoch("2026-11-17T09:00:00Z")))
    }

    @Test
    fun `range label joins both dates`() {
        assertEquals(
            "Nov 16 – Nov 23",
            eventRangeLabel(epoch("2026-11-16T07:00:00Z"), epoch("2026-11-23T07:00:00Z")),
        )
    }

    @Test
    fun `a missing end collapses to one date`() {
        assertEquals(
            "Nov 16",
            eventRangeLabel(epoch("2026-11-16T07:00:00Z"), epoch("2026-11-16T07:00:00Z")),
        )
    }
}
