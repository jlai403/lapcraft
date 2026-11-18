package io.github.jlai403.lapcraft.app.events

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EventRefreshTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        SettingsStore(context).eventTags = false
        DriverProfileStore(context).clear()
        EventCache(context).save(EventCacheFile())
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
