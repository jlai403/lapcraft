package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearState() {
        DriverProfileStore(context).clear()
        SettingsStore(context).previewEmptyLibrary = false
        java.io.File(context.filesDir, "laps").deleteRecursively()
        java.io.File(context.filesDir, "events").deleteRecursively()
    }

    @Test
    fun `shows the library header with counts and the best lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)))

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Library").assertExists()
        compose.onNodeWithText("2 laps · 1 track · Best 1:30.000").assertExists()
    }

    @Test
    fun `tapping a card opens the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened: Pair<String, Int>? = null

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { id -> opened = id.value to 1 }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1:30.000").performClick()

        assertEquals("session-1" to 1, opened)
    }

    @Test
    fun `marks the fastest lap per track and shows the delta`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)))

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, compose.onAllNodesWithText("Fastest lap").fetchSemanticsNodes().size)
        compose.onNodeWithText("+1.500").assertExists()
    }

    @Test
    fun `filters to laps without a replay`() {
        SessionFixtures.install(context, SessionFixtures.session(id = "with", lapTimesMs = listOf(90_000)))
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "without",
                trackLabel = "Nurburgring",
                createdAt = "2026-11-16T09:00:00Z",
                lapTimesMs = listOf(120_000),
                needsCut = true,
            ),
        )

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("2:00.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No replay").performClick()

        assertEquals(0, compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().size)
        compose.onNodeWithText("2:00.000").assertExists()
    }

    @Test
    fun `shows every lap driver with name and flag`() {
        val profiles = DriverProfileStore(context)
        profiles.setOwner(DriverProfile("skino2024", "Skino", "GB"))
        profiles.save(DriverProfile("friend", "Friend", "US"))
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "mine", createdAt = "2026-11-16T08:00:00Z", lapTimesMs = listOf(90_000), driverPsnId = "skino2024"),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "theirs",
                trackLabel = "Monza",
                createdAt = "2026-11-16T09:30:00Z",
                lapTimesMs = listOf(100_000),
                driverPsnId = "friend",
            ),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "legacy",
                trackLabel = "Spa",
                createdAt = "2026-11-16T10:00:00Z",
                lapTimesMs = listOf(110_000),
            ),
        )

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("lap-card").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lap-list").performScrollToIndex(0)
        compose.onNodeWithText("Driver not set").assertExists()
        compose.onNodeWithTag("lap-list").performScrollToIndex(1)
        compose.onNodeWithText("Friend").assertExists()
        compose.onNodeWithText("🇺🇸").assertExists()
        compose.onNodeWithTag("lap-list").performScrollToIndex(2)
        compose.onNodeWithText("Skino").assertExists()
        compose.onNodeWithText("🇬🇧").assertExists()
    }

    @Test
    fun `unknown laps never claim a fastest badge`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(trackLabel = "Unknown", lapTimesMs = listOf(90_000, 91_000)),
        )

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithText("Fastest lap").fetchSemanticsNodes().size)
    }

    @Test
    fun `empty library explains where to capture`() {
        var record = 0

        compose.setContent {
            LapCraftTheme {
                LibraryScreen(onOpenLap = { _ -> }, onOpenRecord = { record++ })
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No laps yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(
            "Connect to GT7 on the Record tab to start capturing a lap. " +
                "A replay can be added later to the recorded lap.",
        ).assertExists()
        compose.onNodeWithText("Go to Record").performClick()
        assertEquals(1, record)
    }

    @Test
    fun `developer preview shows the empty state and turns off`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SettingsStore(context).previewEmptyLibrary = true

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Previewing the empty library").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No laps yet").assertExists()
        assertEquals(0, compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().size)

        compose.onNodeWithText("Turn off").performClick()

        assertFalse(SettingsStore(context).previewEmptyLibrary)
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `swipe right opens the edit sheet without opening the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened = 0

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> opened++ }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lap-card").performTouchInput { swipeRight() }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("edit-lap-title").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, opened)
    }

    @Test
    fun `swipe left asks to delete without opening the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened = 0

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> opened++ }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lap-card").performTouchInput { swipeLeft() }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Delete lap?").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, opened)
    }

    @Test
    fun `shows the saved driver chip and profile links`() {
        DriverProfileStore(context).setOwner(DriverProfile("skino2024", "Skino", "GB"))

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.onNodeWithTag("library-profile-menu").performClick()
        compose.onNodeWithText("GT GridStats").assertExists()
        compose.onNodeWithText("GTSH-Rank").assertExists()
        compose.onNodeWithText("Edit profile").assertExists()
    }

    @Test
    fun `add profile opens settings`() {
        var edits = 0

        compose.setContent {
            LapCraftTheme {
                LibraryScreen(onOpenLap = { _ -> }, onOpenSettings = { edits++ })
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Add GT profile").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library-add-profile").performClick()

        assertEquals(1, edits)
    }

    @Test
    fun `separates laps with a full-width divider`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)))

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            1,
            compose.onAllNodesWithTag("lap-divider", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `compare picks the first lap as primary and the second as reference`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "session-2", lapTimesMs = listOf(91_000)),
        )
        var compared: List<Any>? = null

        compose.setContent {
            LapCraftTheme {
                LibraryScreen(
                    onOpenLap = { _ -> },
                    onCompare = { a, b -> compared = listOf(a, b) },
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:31.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library-compare").performClick()
        compose.onNodeWithText("1:30.000").performClick()
        compose.onNodeWithText("Primary").assertExists()
        compose.onNodeWithTag("lap-list").performScrollToIndex(1)
        compose.onNodeWithText("1:31.000").performClick()
        compose.onNodeWithTag("library-compare-go").performClick()

        assertEquals(listOf(LapId("session-1"), LapId("session-2")), compared)
    }

    @Test
    fun `shows an event header with the window and best lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)))
        val store = LapStore(context.filesDir)
        val now = System.currentTimeMillis()
        val tag = EventTag(
            id = "daily-A",
            kind = EventKind.Daily,
            label = "Daily Race A · W47",
            carClass = "Gr.4",
            auto = true,
            startMs = now - 86_400_000L,
            endMs = now + 86_400_000L,
        )
        store.list().forEach { store.setEvent(it, tag) }

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Daily Race A · W47").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Daily Race A · W47").assertExists()
        val titleLeft = compose.onNodeWithText("Daily Race A · W47").fetchSemanticsNode().boundsInRoot.left
        val detailLeft = compose.onNodeWithText("Gr.4", substring = true).fetchSemanticsNode().boundsInRoot.left
        assertEquals(titleLeft, detailLeft, 0.5f)
    }

    @Test
    fun `keeps lapsed events collapsed under Past events`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        val store = LapStore(context.filesDir)
        val now = System.currentTimeMillis()
        val past = EventTag(
            id = "daily-B",
            kind = EventKind.Daily,
            label = "Daily Race B · W45",
            auto = true,
            startMs = now - 14L * 86_400_000L,
            endMs = now - 7L * 86_400_000L,
        )
        store.list().forEach { store.setEvent(it, past) }

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Past events", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "lapsed event header should be collapsed",
            compose.onAllNodesWithText("Daily Race B · W45").fetchSemanticsNodes().isEmpty(),
        )
        compose.onNodeWithText("Past events", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Daily Race B · W45").assertExists()
    }

    @Test
    fun `old untagged laps stay collapsed under Past events`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "old", createdAt = "2026-11-10T10:00:00Z", lapTimesMs = listOf(90_000)),
        )

        compose.setContent {
            LapCraftTheme { LibraryScreen(onOpenLap = { _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Past events", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "old untagged lap should be hidden while collapsed",
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isEmpty(),
        )
        compose.onNodeWithText("Past events", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1:30.000").assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}