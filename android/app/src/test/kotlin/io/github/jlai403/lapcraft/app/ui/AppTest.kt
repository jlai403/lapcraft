package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.updater.UpdateManager
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
class AppTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearState() {
        SettingsStore(context).clearPs5Ip()
        SettingsStore(context).previewEmptyLibrary = true
        java.io.File(context.filesDir, "laps").deleteRecursively()
    }

    @Test
    fun `a console address saved in settings reaches the record screen`() {
        compose.setContent {
            LapCraftTheme {
                App(
                    capture = CaptureUiState(),
                    onConnect = {},
                    onDisconnect = {},
                    onCapture = {},
                    onCancelCapture = {},
                    sharedVideoUri = null,
                    onSharedVideoHandled = {},
                    update = UpdateManager.State.Idle,
                    onCheckUpdate = {},
                )
            }
        }

        compose.onNodeWithTag("tab-record").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("STREAM").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Set address").assertExists()

        compose.onNodeWithText("Set address").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("settings-ps5-edit").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("settings-ps5-edit").performScrollTo().performClick()
        compose.onNodeWithTag("settings-ps5-ip").performTextInput("192.168.1.50")
        compose.onNodeWithText("Save IP").performClick()

        compose.onNodeWithTag("tab-record").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("STREAM").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Connect").assertExists()
    }
}