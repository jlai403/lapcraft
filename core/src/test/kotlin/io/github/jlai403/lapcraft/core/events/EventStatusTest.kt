package io.github.jlai403.lapcraft.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class EventStatusTest {
    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()
    private val now = epoch("2026-11-17T12:00:00Z")

    private fun tag(
        start: Long? = null,
        end: Long? = null,
        auto: Boolean = true,
        id: String = "daily-Daily Race A-2026-11-16",
    ) = EventTag(id = id, kind = EventKind.Daily, label = "Daily Race A · W47", auto = auto, startMs = start, endMs = end)

    @Test
    fun `a tag inside its window is current`() {
        val event = tag(start = epoch("2026-11-16T00:00:00Z"), end = epoch("2026-11-22T23:59:59Z"))
        assertEquals(EventStatus.Current, event.status(now))
    }

    @Test
    fun `a tag past its end is lapsed`() {
        val event = tag(start = epoch("2026-11-02T00:00:00Z"), end = epoch("2026-11-08T23:59:59Z"))
        assertEquals(EventStatus.Lapsed, event.status(now))
    }

    @Test
    fun `a tag before its start is upcoming`() {
        val event = tag(start = epoch("2026-11-23T00:00:00Z"), end = epoch("2026-11-29T23:59:59Z"))
        assertEquals(EventStatus.Upcoming, event.status(now))
    }

    @Test
    fun `an auto tag without dates follows cache membership`() {
        val event = tag()
        assertEquals(EventStatus.Current, event.status(now, setOf(event.id)))
        assertEquals(EventStatus.Lapsed, event.status(now, emptySet()))
    }

    @Test
    fun `a manual tag without dates is unknown`() {
        val event = tag(auto = false, id = "manual-1700000000000")
        assertEquals(EventStatus.Unknown, event.status(now, emptySet()))
    }
}