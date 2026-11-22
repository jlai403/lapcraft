package io.github.jlai403.lapcraft.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class EventRepairTest {
    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun event(
        id: String,
        start: String = "2026-11-21T15:00:00Z",
        end: String = "2026-11-22T04:59:59Z",
    ) = CachedEvent(
        id = id,
        kind = EventKind.Championship,
        label = "World Series 2026 - Exhibition 3 · R4",
        series = "Manufacturers Cup",
        round = 4,
        startMs = epoch(start),
        endMs = epoch(end),
        source = "polyphony-cdn",
        fetchedAtMs = 0,
    )

    private fun tag(id: String = "champ-22-1800-r4", auto: Boolean = true) = EventTag(
        id = id,
        kind = EventKind.Championship,
        label = "World Series 2026 - Exhibition 3 · R4",
        series = "Manufacturers Cup",
        round = 4,
        auto = auto,
        startMs = epoch("2026-11-21T08:00:00Z"),
        endMs = epoch("2026-11-21T21:59:59Z"),
    )

    private val capturedAt = epoch("2026-11-21T18:00:00Z")

    @Test
    fun `a tag from another region folder moves to the matching round`() {
        val repaired = tag().repairedFrom(listOf(event("champ-22-1801-r4")), capturedAt)

        assertEquals("champ-22-1801-r4", repaired?.id)
        assertEquals(epoch("2026-11-22T04:59:59Z"), repaired?.endMs)
    }

    @Test
    fun `a tag already in the cache is left alone`() {
        assertNull(tag(id = "champ-22-1801-r4").repairedFrom(listOf(event("champ-22-1801-r4")), capturedAt))
    }

    @Test
    fun `a lap outside every window is left alone`() {
        val previousSeason = event("champ-22-1800-r4", "2026-10-31T08:00:00Z", "2026-10-31T21:59:59Z")

        assertNull(tag().repairedFrom(listOf(previousSeason), capturedAt))
    }

    @Test
    fun `a manual tag is left alone`() {
        assertNull(tag(auto = false).repairedFrom(listOf(event("champ-22-1801-r4")), capturedAt))
    }
}
