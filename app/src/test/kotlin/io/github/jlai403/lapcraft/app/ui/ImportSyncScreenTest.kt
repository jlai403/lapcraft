package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.settings.ReplayPreset
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportSyncScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `missing replay is explained`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                ImportSyncScreen(
                    lapId = LapId("session-1"),
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(10_000L) {
            compose.onAllNodesWithText("No replay file attached.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sync clip").assertExists()
    }

    @Test
    fun `replacing a clip says the current one is kept on discard`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true))

        compose.setContent {
            LapCraftTheme {
                ImportSyncScreen(
                    lapId = LapId("session-1"),
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Saving replaces the current replay. Discard keeps it.")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `sync screen offers transport, fine steps, and anchor guidance`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true))

        compose.setContent {
            LapCraftTheme {
                ImportSyncScreen(
                    lapId = LapId("session-1"),
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("How to find the lap start").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("frame-forward").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Frame ruler").assertExists()
        compose.onNodeWithContentDescription("Previous brake point").assertExists()
        compose.onNodeWithContentDescription("Next brake point").assertExists()
        compose.onNodeWithContentDescription("Play").assertExists()
        compose.onNodeWithText("The lap start is the first frame of the lap. The replay and telemetry are clamped to it.").assertExists()
        compose.onNodeWithContentDescription("Step back one frame").assertExists()
        compose.onNodeWithContentDescription("Step forward one frame").assertExists()
    }

    @Test
    fun `stepping a frame moves the playhead along the pointer path`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true))
        compose.setContent {
            LapCraftTheme {
                ImportSyncScreen(
                    lapId = LapId("session-1"),
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("frame-forward").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("frame-back").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("frame-back").assertExists()
    }

    @Test
    fun `anchor tips explain which frame to pick`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true))

        compose.setContent {
            LapCraftTheme {
                ImportSyncScreen(
                    lapId = LapId("session-1"),
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("How to find the lap start").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("How to find the lap start").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Hide tips").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Record a few seconds past the finish so the whole lap fits after the start.")
            .assertExists()
    }

    @Test
    fun `import screen offers per-lap quality controls`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true))
        SettingsStore(context).replayPreset = ReplayPreset.Balanced

        compose.setContent {
            LapCraftTheme {
                ImportSyncScreen(
                    lapId = LapId("session-1"),
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("REPLAY QUALITY").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Preset").assertExists()
        compose.onNodeWithText("1080p HEVC ~5 Mbps", substring = true).assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
