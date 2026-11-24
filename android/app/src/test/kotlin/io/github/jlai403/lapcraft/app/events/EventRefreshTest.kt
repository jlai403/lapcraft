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
        assertEquals("Manufacturers Cup 2026 - Exhibition 3 · R4", repaired.label)
    }

    @Test
    fun `an auto championship tag is relabelled when its event is still cached`() = runBlocking {
        val old = EventTag(
            id = "champ-22-1801-r4",
            kind = EventKind.Championship,
            label = "World Series 2026 - Exhibition 3 · R4",
            series = "Manufacturers Cup",
            round = 4,
            auto = true,
            startMs = Instant.parse("2026-11-21T15:00:00Z").toEpochMilli(),
            endMs = Instant.parse("2026-11-22T04:59:59Z").toEpochMilli(),
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
        assertEquals("Manufacturers Cup 2026 - Exhibition 3 · R4", repaired.label)
    }

    @Test
    fun `a daily tag moves to the sport mode window`() {
        val old = EventTag(
            id = "daily-Daily Race C-2026-11-16 00:00:00",
            kind = EventKind.Daily,
            label = "Daily Race C · W47",
            auto = true,
            startMs = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli(),
            endMs = Instant.parse("2026-11-22T23:59:59Z").toEpochMilli(),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                createdAt = "2026-11-17T07:35:27Z",
                lapTimesMs = listOf(90_000),
                event = old,
            ),
        )

        val changed = EventRefresh.repairDailyTags(context, lapStore(context).summaries())

        assertTrue(changed)
        val repaired = lapStore(context).summaries().single().event!!
        assertEquals(Instant.parse("2026-11-16T07:00:00Z").toEpochMilli(), repaired.startMs)
        assertEquals(Instant.parse("2026-11-23T06:59:59Z").toEpochMilli(), repaired.endMs)
    }

    @Test
    fun `an auto daily tag outside the corrected week is dropped`() {
        val early = EventTag(
            id = "daily-Daily Race C-2026-11-23 00:00:00",
            kind = EventKind.Daily,
            label = "Daily Race C · W48",
            auto = true,
            startMs = Instant.parse("2026-11-23T00:00:00Z").toEpochMilli(),
            endMs = Instant.parse("2026-11-29T23:59:59Z").toEpochMilli(),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                createdAt = "2026-11-23T01:00:00Z",
                lapTimesMs = listOf(90_000),
                event = early,
            ),
        )

        val changed = EventRefresh.repairDailyTags(context, lapStore(context).summaries())

        assertTrue(changed)
        assertEquals(null, lapStore(context).summaries().single().event)
    }

    @Test
    fun `a legacy daily tag derives its window from the id`() {
        val legacy = EventTag(
            id = "daily-Daily Race C-2026-11-16 00:00:00",
            kind = EventKind.Daily,
            label = "Daily Race C",
            auto = false,
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                createdAt = "2026-11-16T22:31:13Z",
                lapTimesMs = listOf(90_000),
                event = legacy,
            ),
        )

        val changed = EventRefresh.repairDailyTags(context, lapStore(context).summaries())

        assertTrue(changed)
        val repaired = lapStore(context).summaries().single().event!!
        assertEquals(Instant.parse("2026-11-16T07:00:00Z").toEpochMilli(), repaired.startMs)
        assertEquals(Instant.parse("2026-11-23T06:59:59Z").toEpochMilli(), repaired.endMs)
    }

    @Test
    fun `a corrected daily tag needs no rewrite`() {
        val current = EventTag(
            id = "daily-Daily Race C-2026-11-16 00:00:00",
            kind = EventKind.Daily,
            label = "Daily Race C · W47",
            auto = true,
            startMs = Instant.parse("2026-11-16T07:00:00Z").toEpochMilli(),
            endMs = Instant.parse("2026-11-23T06:59:59Z").toEpochMilli(),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                createdAt = "2026-11-17T07:35:27Z",
                lapTimesMs = listOf(90_000),
                event = current,
            ),
        )

        val changed = EventRefresh.repairDailyTags(context, lapStore(context).summaries())

        assertFalse(changed)
        assertEquals(current, lapStore(context).summaries().single().event)
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
