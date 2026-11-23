package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class EventCacheTest {
    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun daily(id: String, start: String, end: String) = CachedEvent(
        id = id,
        kind = EventKind.Daily,
        label = id,
        startMs = epoch(start),
        endMs = epoch(end),
        source = "gridstats",
        fetchedAtMs = 0,
    )

    private fun cache(): EventCache = EventCache(Files.createTempDirectory("events").toFile())

    @Test
    fun `last fetch is the newer feed`() {
        assertEquals(2_000L, EventCacheFile(gridstatsFetchedAtMs = 1_000, polyphonyFetchedAtMs = 2_000).lastFetchedAtMs)
        assertEquals(3_000L, EventCacheFile(gridstatsFetchedAtMs = 3_000, polyphonyFetchedAtMs = 2_000).lastFetchedAtMs)
    }

    @Test
    fun `last fetch is never before the first refresh`() {
        assertEquals(0L, EventCacheFile().lastFetchedAtMs)
        assertEquals(2_000L, EventCacheFile(polyphonyFetchedAtMs = 2_000).lastFetchedAtMs)
    }

    @Test
    fun `a rolled week stays available for a month`() {
        val cache = cache()
        val outgoing = daily("daily-A-2026-11-16 00:00:00", "2026-11-16T07:00:00Z", "2026-11-23T06:59:59Z")
        val recent = daily("daily-A-2026-11-09 00:00:00", "2026-11-09T07:00:00Z", "2026-11-16T06:59:59Z")
        val stale = daily("daily-A-2026-10-05 00:00:00", "2026-10-05T07:00:00Z", "2026-10-12T06:59:59Z")
        cache.save(EventCacheFile(events = listOf(outgoing, recent, stale)))
        val incoming = daily("daily-A-2026-11-23 00:00:00", "2026-11-23T07:00:00Z", "2026-11-30T06:59:59Z")

        cache.replaceGridstats(listOf(incoming), nowMs = epoch("2026-11-23T00:20:00Z"))

        assertEquals(listOf(outgoing.id, recent.id, incoming.id), cache.events().map { it.id })
    }

    @Test
    fun `a refreshed event replaces its old copy`() {
        val cache = cache()
        val old = daily("daily-A-2026-11-16 00:00:00", "2026-11-16T00:00:00Z", "2026-11-22T23:59:59Z")
        cache.save(EventCacheFile(events = listOf(old)))
        val fresh = daily("daily-A-2026-11-16 00:00:00", "2026-11-16T07:00:00Z", "2026-11-23T06:59:59Z")

        cache.replaceGridstats(listOf(fresh), nowMs = epoch("2026-11-17T12:00:00Z"))

        assertEquals(listOf(fresh), cache.events())
    }
}
