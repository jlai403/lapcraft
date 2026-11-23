package io.github.jlai403.lapcraft.core.events

import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class EventGroupsTest {
    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()
    private val now = epoch("2026-11-17T12:00:00Z")

    private fun lap(
        id: String,
        timeMs: Int,
        createdAt: String = "2026-11-16T10:00:00Z",
        track: String = "Suzuka",
        event: EventTag? = null,
    ) = LapSummary(
        lapId = LapId(id),
        lapTimeMs = timeMs,
        createdAt = createdAt,
        trackLabel = track,
        carName = "BRZ GT300 '21",
        hasReplay = true,
        replayPending = false,
        event = event,
    )

    private fun tag(
        id: String,
        label: String,
        kind: EventKind = EventKind.Daily,
        start: Long,
        end: Long,
        auto: Boolean = true,
    ) = EventTag(id = id, kind = kind, label = label, auto = auto, startMs = start, endMs = end)

    private val currentEvent = tag(
        id = "daily-Daily Race A-2026-11-16",
        label = "Daily Race A · W47",
        start = epoch("2026-11-16T00:00:00Z"),
        end = epoch("2026-11-22T23:59:59Z"),
    )
    private val pastEvent = tag(
        id = "daily-Daily Race B-2026-11-02",
        label = "Daily Race B · W45",
        start = epoch("2026-11-02T00:00:00Z"),
        end = epoch("2026-11-08T23:59:59Z"),
    )

    @Test
    fun `laps without a tag share one group`() {
        val groups = listOf(lap("a", 90_000), lap("b", 91_000)).eventGroups(nowMs = now)
        assertEquals(1, groups.size)
        assertEquals("untagged", groups[0].key)
        assertEquals(2, groups[0].laps.size)
        assertEquals(90_000, groups[0].bestLapMs)
    }

    @Test
    fun `current events come before untagged laps and lapsed events last`() {
        val groups = listOf(
            lap("past", 88_000, createdAt = "2026-11-03T10:00:00Z", event = pastEvent),
            lap("untagged", 90_000, createdAt = "2026-11-16T11:00:00Z"),
            lap("current", 89_000, event = currentEvent),
        ).eventGroups(nowMs = now)

        assertEquals(
            listOf(currentEvent.id, "untagged", pastEvent.id),
            groups.map { it.key },
        )
        assertEquals(EventStatus.Current, groups[0].status)
        assertEquals(EventStatus.Lapsed, groups[2].status)
        assertEquals(89_000, groups[0].bestLapMs)
    }

    @Test
    fun `an old daily window resolves against the sport mode reset`() {
        val old = tag(
            id = "daily-Daily Race C-2026-11-16 00:00:00",
            label = "Daily Race C · W47",
            start = epoch("2026-11-16T00:00:00Z"),
            end = epoch("2026-11-22T23:59:59Z"),
        )
        val groups = listOf(lap("c", 90_000, createdAt = "2026-11-16T22:31:13Z", event = old))
            .eventGroups(nowMs = epoch("2026-11-23T05:00:00Z"))

        assertEquals(EventStatus.Current, groups.single().status)
        assertEquals(epoch("2026-11-16T07:00:00Z"), groups.single().tag!!.startMs)
        assertEquals(epoch("2026-11-23T06:59:59Z"), groups.single().tag!!.endMs)
    }

    @Test
    fun `a windowless daily tag uses the date in its id`() {
        val legacy = EventTag(
            id = "daily-Daily Race C-2026-11-16 00:00:00",
            kind = EventKind.Daily,
            label = "Daily Race C",
            auto = false,
        )
        val groups = listOf(lap("legacy", 90_000, createdAt = "2026-11-16T22:31:13Z", event = legacy))
            .eventGroups(nowMs = epoch("2026-11-23T05:00:00Z"))

        assertEquals(EventStatus.Current, groups.single().status)
        assertEquals(epoch("2026-11-16T07:00:00Z"), groups.single().tag!!.startMs)
        assertEquals(epoch("2026-11-23T06:59:59Z"), groups.single().tag!!.endMs)
    }

    @Test
    fun `a windowed tag in the group wins over a legacy tag`() {
        val legacy = EventTag(id = "daily-A", kind = EventKind.Daily, label = "Daily Race A", auto = false)
        val windowed = EventTag(
            id = "daily-A",
            kind = EventKind.Daily,
            label = "Daily Race A",
            auto = false,
            startMs = epoch("2026-11-16T07:00:00Z"),
            endMs = epoch("2026-11-23T06:59:59Z"),
        )
        val groups = listOf(
            lap("old", 90_000, createdAt = "2026-11-16T10:00:00Z", event = legacy),
            lap("new", 91_000, createdAt = "2026-11-17T10:00:00Z", event = windowed),
        ).eventGroups(nowMs = epoch("2026-11-23T03:00:00Z"))

        assertEquals(EventStatus.Current, groups.single().status)
    }

    @Test
    fun `manual tags with the same label share a group`() {
        val first = EventTag(id = "manual-1", kind = EventKind.Championship, label = "World Series · R3", auto = false)
        val second = EventTag(id = "manual-2", kind = EventKind.Championship, label = "World Series · R3", auto = false)
        val groups = listOf(lap("a", 90_000, event = first), lap("b", 91_000, event = second))
            .eventGroups(nowMs = now)
 
        assertEquals(1, groups.size)
        assertEquals("manual-worldseriesr3", groups[0].key)
        assertEquals(2, groups[0].laps.size)
        assertEquals(EventStatus.Unknown, groups[0].status)
    }

    @Test
    fun `untagged laps stay in the feed however old`() {
        val groups = listOf(
            lap("old", 90_000, createdAt = "2026-11-10T10:00:00Z"),
            lap("older", 91_000, createdAt = "2026-10-05T10:00:00Z"),
        ).eventGroups(nowMs = now)

        assertEquals(listOf("untagged"), groups.map { it.key })
        assertEquals(EventStatus.Unknown, groups[0].status)
        assertEquals(2, groups[0].laps.size)
    }

    @Test
    fun `a manual group older than this week is lapsed`() {
        val tag = EventTag(id = "manual-1", kind = EventKind.Championship, label = "World Series", auto = false)
        val groups = listOf(lap("old", 90_000, createdAt = "2026-11-10T10:00:00Z", event = tag))
            .eventGroups(nowMs = now)
        assertEquals(1, groups.size)
        assertEquals(EventStatus.Lapsed, groups[0].status)
    }

    @Test
    fun `a manual group with a current-week lap stays current`() {
        val tag = EventTag(id = "manual-1", kind = EventKind.Championship, label = "World Series", auto = false)
        val groups = listOf(lap("new", 90_000, createdAt = "2026-11-16T10:00:00Z", event = tag))
            .eventGroups(nowMs = now)
        assertEquals(1, groups.size)
        assertEquals(EventStatus.Unknown, groups[0].status)
    }
}