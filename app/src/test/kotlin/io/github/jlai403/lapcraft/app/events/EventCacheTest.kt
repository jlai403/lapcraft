package io.github.jlai403.lapcraft.app.events

import org.junit.Assert.assertEquals
import org.junit.Test

class EventCacheTest {
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
}
