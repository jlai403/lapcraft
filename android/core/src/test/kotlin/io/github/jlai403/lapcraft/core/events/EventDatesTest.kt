package io.github.jlai403.lapcraft.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
            eventRangeLabel(
                epoch("2026-11-16T07:00:00Z"),
                epoch("2026-11-23T07:00:00Z"),
                ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun `range label follows the device calendar`() {
        // North American R4 runs 15:00Z to 04:59Z the next UTC day but ends on the same local day.
        assertEquals(
            "Nov 21",
            eventRangeLabel(
                epoch("2026-11-21T15:00:00Z"),
                epoch("2026-11-22T04:59:59Z"),
                ZoneId.of("America/Edmonton"),
            ),
        )
    }

    @Test
    fun `sport mode week starts Monday at seven UTC`() {
        val start = sportModeWeekStart(LocalDate.parse("2026-11-16"))

        assertEquals(epoch("2026-11-16T07:00:00Z"), start)
        assertEquals(epoch("2026-11-23T06:59:59Z"), sportModeWeekEnd(start))
    }

    @Test
    fun `a missing end collapses to one date`() {
        assertEquals(
            "Nov 16",
            eventRangeLabel(
                epoch("2026-11-16T07:00:00Z"),
                epoch("2026-11-16T07:00:00Z"),
                ZoneOffset.UTC,
            ),
        )
    }
}
