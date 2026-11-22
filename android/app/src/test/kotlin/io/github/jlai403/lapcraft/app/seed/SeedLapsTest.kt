package io.github.jlai403.lapcraft.app.seed

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LibraryArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SeedLapsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun shouldSeedTruthTable() {
        assertTrue(SeedLaps.shouldSeed(0, 0, false))
        assertFalse(SeedLaps.shouldSeed(1, 0, false))
        assertFalse(SeedLaps.shouldSeed(0, 1, false))
        assertFalse(SeedLaps.shouldSeed(0, 0, true))
    }

    @Test
    fun assetImportsCleanly() {
        val root = tmp.newFolder("root")
        val store = LapStore(root)
        context.assets.open("seed-laps.zip").use { input ->
            val report = LibraryArchive.import(root, input)
            assertEquals(2, report.laps)
            assertEquals(0, report.skipped)
            assertEquals(0, report.failed)
        }
        val ids = store.list()
        assertEquals(2, ids.size)
        var totalReplay = 0L
        for (id in ids) {
            val record = store.load(id)!!
            assertNull("driverPsnId must be cleared", record.driverPsnId)
            assertNull("event must be cleared", record.event)
            assertEquals("6e378b", record.trackId)
            assertTrue(record.lapTimeMs > 0)
            val dir = store.lapDir(id)
            val replay = File(dir, "replay.mp4")
            assertTrue("missing replay.mp4 for $id", replay.isFile)
            assertTrue("replay too large: ${replay.length()}", replay.length() < 14L * 1024 * 1024)
            totalReplay += replay.length()
            val samples = store.readSamples(id)
            assertTrue("no samples for $id", samples.isNotEmpty())
        }
        assertTrue("total replay too large: $totalReplay", totalReplay < 27L * 1024 * 1024)

        // second import skips both
        context.assets.open("seed-laps.zip").use { input ->
            val report = LibraryArchive.import(root, input)
            assertEquals(0, report.laps)
            assertEquals(2, report.skipped)
            assertEquals(0, report.failed)
        }
        assertEquals(2, store.list().size)
    }

    @Test
    fun garbageInputLeavesStoreEmpty() {
        val root = tmp.newFolder("root2")
        val store = LapStore(root)
        runCatching { "not a zip".byteInputStream().use { LibraryArchive.import(root, it) } }
        assertEquals(0, store.list().size)
    }

    @Test
    fun importIfNeededStampsVersionAndCaptureTime() {
        val root = tmp.newFolder("root3")
        val store = LapStore(root)
        val settings = SettingsStore(context)
        settings.seedVersion = 0
        // simulate empty library by ensuring no laps
        assertEquals(0, store.list().size)
        val report = runBlocking { SeedLaps.importIfNeeded(context, store, settings, previewEmpty = false, rootDir = root) }
        assertNotNull(report)
        assertEquals(2, store.list().size)
        assertEquals(SeedLaps.VERSION, settings.seedVersion)
        for (id in store.list()) {
            val record = store.load(id)!!
            assertNull(record.driverPsnId)
            assertNull(record.event)
            // capturedAt stamped within the last hour
            val parsed = java.time.Instant.parse(record.capturedAt)
            assertTrue(parsed.isAfter(java.time.Instant.now().minusSeconds(3600)))
        }
        // not re-seeded while version is stamped
        val second = runBlocking { SeedLaps.importIfNeeded(context, store, settings, previewEmpty = false, rootDir = root) }
        assertNull(second)
    }

    @Test
    fun importIfNeededSkipsWhenPreviewEmpty() {
        val root = tmp.newFolder("root4")
        val store = LapStore(root)
        val settings = SettingsStore(context)
        settings.seedVersion = 0
        assertNull(runBlocking { SeedLaps.importIfNeeded(context, store, settings, previewEmpty = true, rootDir = root) })
        assertEquals(0, store.list().size)
        assertEquals(0, settings.seedVersion)
    }
}
