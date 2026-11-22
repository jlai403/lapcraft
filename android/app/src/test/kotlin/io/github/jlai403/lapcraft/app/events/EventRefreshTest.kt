package io.github.jlai403.lapcraft.app.events

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.catalog.lapStore
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.ui.SessionFixtures
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EventRefreshTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val northAmericaR4 = """
        {"result":[{"championship_title_map":{"GB":"World Series 2026 - Exhibition 3"},
          "season_title_map":{"GB":"Manufacturers Cup"},
          "season_id":1801,"series_id":22,
          "rounds":[{"round_id":4,"car_class":"CAR_CLASS_GR3",
                     "start_date":"2026-11-21T15:00:00Z","end_date":"2026-11-22T04:59:59Z"}]}]}
    """.trimIndent()

    @Before
    fun reset() {
        SettingsStore(context).eventTags = false
        DriverProfileStore(context).clear()
        EventCache(context).save(EventCacheFile())
    }

    @Test
    fun `the CDN url follows the device region`() = runBlocking {
        var requested = ""

        EventRefresh.refresh(
            context,
            force = true,
            fetchCdn = { url -> requested = url; "{}" },
            fetchApi = { _, _ -> null },
            regionFolder = PolyphonyRegion.NORTH_AMERICA,
        )

        assertTrue(requested.endsWith("/championship/seasons/6/active.json"))
        assertEquals(6, EventCache(context).load().polyphonyFolder)
    }

    @Test
    fun `a region change refetches a fresh cache`() = runBlocking {
        SettingsStore(context).eventTags = true
        EventCache(context).save(
            EventCacheFile(
                polyphonyFetchedAtMs = System.currentTimeMillis(),
                polyphonyFolder = PolyphonyRegion.EUROPE,
            )
        )

        val result = EventRefresh.refresh(
            context,
            force = false,
            fetchCdn = { "{}" },
            fetchApi = { _, _ -> null },
            regionFolder = PolyphonyRegion.NORTH_AMERICA,
        )

        assertTrue(result.polyphonyWritten)
        assertEquals(6, EventCache(context).load().polyphonyFolder)
    }

    @Test
    fun `a lap tagged from another region is remapped on refresh`() = runBlocking {
        val old = EventTag(
            id = "champ-22-1800-r4",
            kind = EventKind.Championship,
            label = "World Series 2026 - Exhibition 3 · R4",
            series = "Manufacturers Cup",
            round = 4,
            auto = true,
            startMs = Instant.parse("2026-11-21T08:00:00Z").toEpochMilli(),
            endMs = Instant.parse("2026-11-21T21:59:59Z").toEpochMilli(),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                createdAt = "2026-11-21T18:00:00Z",
                lapTimesMs = listOf(90_000),
                event = old,
            ),
        )

        EventRefresh.refresh(
            context,
            force = true,
            fetchCdn = { northAmericaR4 },
            fetchApi = { _, _ -> null },
            regionFolder = PolyphonyRegion.NORTH_AMERICA,
        )

        val repaired = lapStore(context).summaries().single().event!!
        assertEquals("champ-22-1801-r4", repaired.id)
        assertEquals(Instant.parse("2026-11-22T04:59:59Z").toEpochMilli(), repaired.endMs)
    }

    @Test
    fun `a forced refresh fills the schedule cache with event tags off`() = runBlocking {
        val result = EventRefresh.refresh(
            context,
            force = true,
            fetchCdn = { "{}" },
            fetchApi = { _, _ -> null },
        )

        assertTrue(result.polyphonyWritten)
        assertFalse(result.gridStatsWritten)
        assertTrue(EventCache(context).load().polyphonyFetchedAtMs > 0)
        assertTrue(EventCache(context).load().lastFetchedAtMs > 0)
    }

    @Test
    fun `a background refresh still waits for event tags`() = runBlocking {
        val result = EventRefresh.refresh(
            context,
            force = false,
            fetchCdn = { "{}" },
            fetchApi = { _, _ -> null },
        )

        assertFalse(result.polyphonyWritten)
        assertEquals(0L, EventCache(context).load().lastFetchedAtMs)
    }

    @Test
    fun `gridstats stamps its own feed when a key is set`() = runBlocking {
        SettingsStore(context).eventTags = true
        DriverProfileStore(context).apiKey = "test-key"

        val result = EventRefresh.refresh(
            context,
            force = true,
            fetchCdn = { "{}" },
            fetchApi = { _, _ -> "{}" },
        )

        assertTrue(result.polyphonyWritten)
        assertTrue(result.gridStatsWritten)
        assertTrue(EventCache(context).load().gridstatsFetchedAtMs > 0)
    }
}
