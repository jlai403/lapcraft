package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.tire.TyreCompound
import org.junit.Assert.assertEquals
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
class LapPlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPlaybackPreferences() {
        SettingsStore(context).defaultPlaybackSpeed = 1f
        SettingsStore(context).telemetryHud = true
    }

    @Test
    fun `lap without a clip still shows charts and add replay`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), withReplay = false),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No replay attached").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").assertExists()
        compose.onNodeWithText("Speed / Gear").assertExists()
        compose.onNodeWithText("Track map").assertExists()
        compose.onNodeWithText("1:30.000").assertExists()
        compose.onNodeWithContentDescription("Frame ruler").assertExists()
        compose.onNodeWithText("1×").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("frame-back").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("frame-forward").fetchSemanticsNodes().size)
    }

    @Test
    fun `a lap without a clip plays its telemetry`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), withReplay = false),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("0:00.000 / 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Play").performClick()
        repeat(60) { compose.mainClock.advanceTimeBy(50) }

        val moved = compose.onAllNodesWithText("0:00.000 / 1:30.000")
            .fetchSemanticsNodes().isEmpty()
        val pause = compose.onAllNodesWithContentDescription("Pause")
            .fetchSemanticsNodes().isNotEmpty()
        org.junit.Assert.assertTrue("moved=$moved pause=$pause", moved && pause)
    }

    @Test
    fun `the replay source button offers files and the PlayStation app`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), withReplay = false),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Add replay").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").performScrollTo().performClick()
        compose.onNodeWithText("Files").assertExists()
        compose.onNodeWithText("PlayStation App").assertExists()
        compose.onNodeWithText("Pick a video from this phone").assertExists()
    }

    @Test
    fun `lap with a clip does not ask for a replay`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").assertDoesNotExist()
        compose.onNodeWithContentDescription("Frame ruler").assertExists()
        compose.onNodeWithText("1×").assertExists()
    }

    @Test
    fun `the speed stepper moves through the playback rates`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1×").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("speed-up").performClick()
        compose.onNodeWithText("1.5×").assertExists()
        compose.onNodeWithTag("speed-down").performClick()
        compose.onNodeWithText("1×").assertExists()
    }

    @Test
    fun `the track map follows by default`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Follow").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Follow").assertIsSelected()
    }

    @Test
    fun `edit lap opens from the header and offers replay replacement`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit lap").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Replace replay").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the lap header shows the assigned driver`() {
        DriverProfileStore(context).setOwner(DriverProfile("skino2024", "Skino", "GB"))
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), driverPsnId = "skino2024"),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Expand details").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka · 🇬🇧 Skino", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a pending import can be resumed or replaced`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                lapTimesMs = listOf(90_000),
                withReplay = false,
                pendingSource = true,
            ),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit lap").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Resume sync").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Replace replay").assertExists()
    }

    @Test
    fun `an existing clip survives a pending replacement import`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Newer clip waiting to be synced.").assertExists()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit lap").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Sync pending").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Resume sync").assertExists()
    }

    @Test
    fun `the telemetry hud shows over the replay by default`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(LapId("session-1"), onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the hud stays hidden when disabled in settings`() {
        SettingsStore(context).telemetryHud = false
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(LapId("session-1"), onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().size)
    }

    @Test
    fun `an unknown driver shows the psn instead of the owner`() {
        DriverProfileStore(context).setOwner(DriverProfile("owner", "Owner", "GB"))
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), driverPsnId = "other"),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(LapId("session-1"), onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Expand details").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka · other", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `share video is disabled without a clip`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), withReplay = false),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(LapId("session-1"), onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Share video").assertIsNotEnabled()
    }

    @Test
    fun `compare offers other laps on the same track`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "session-2", lapTimesMs = listOf(91_000)),
        )
        var compared: List<Any>? = null

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                    onCompare = { a, b -> compared = listOf(a, b) },
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("lap-compare").fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        compose.onNodeWithTag("lap-compare").performClick()
        compose.onNodeWithText("Compare with").assertExists()
        compose.onNodeWithText("1:31.000").performClick()

        assertEquals(listOf(LapId("session-1"), LapId("session-2")), compared)
    }

    @Test
    fun `compare offers only laps from the same event`() {
        val eventA = EventTag(
            id = "daily-A-2026-11-16",
            kind = EventKind.Daily,
            label = "Daily Race A · W47",
            auto = true,
        )
        val eventB = EventTag(
            id = "daily-B-2026-11-16",
            kind = EventKind.Daily,
            label = "Daily Race B · W47",
            auto = true,
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), event = eventA),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "session-2", lapTimesMs = listOf(91_000), event = eventA),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "session-3", lapTimesMs = listOf(92_000), event = eventB),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("lap-compare").fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        compose.onNodeWithTag("lap-compare").performClick()

        compose.onNodeWithText("Compare with").assertExists()
        compose.onNodeWithText("1:31.000").assertExists()
        compose.onNodeWithText("1:32.000").assertDoesNotExist()
    }

    @Test
    fun `a tagged lap shows its tyre and setup beside the speed row`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                lapTimesMs = listOf(90_000),
                withReplay = false,
                tyre = TyreCompound.RACING_SOFT,
                tcs = 2,
                brakeBalance = 1,
            ),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Speed").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, compose.onAllNodesWithTag("tyre-badge", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("TCS 2 · BB +1").fetchSemanticsNodes().size)
    }

    @Test
    fun `an untagged lap shows no tyre badge`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000), withReplay = false))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    lapId = LapId("session-1"),
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Speed").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithTag("tyre-badge", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("TCS off").fetchSemanticsNodes().size)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
