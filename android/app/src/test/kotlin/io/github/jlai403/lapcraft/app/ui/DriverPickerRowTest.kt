package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.core.profile.DriverProfile
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
class DriverPickerRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearProfiles() {
        DriverProfileStore(context).clear()
    }

    @Test
    fun `use my profile assigns the owner`() {
        val owner = DriverProfile("skino2024", "Skino", "GB")
        var assigned: DriverProfile? = null

        compose.setContent {
            LapCraftTheme {
                DriverPickerRow(
                    driverPsnId = null,
                    profile = null,
                    owner = owner,
                    profiles = listOf(owner),
                    onChange = { assigned = it },
                    onAddDriver = {},
                    onOpenLink = null,
                )
            }
        }

        compose.onNodeWithTag("driver-use-owner").performClick()

        assertEquals(owner, assigned)
    }

    @Test
    fun `manual driver entry saves the profile`() {
        var saved: DriverProfile? = null

        compose.setContent {
            LapCraftTheme {
                AddDriverDialog(
                    lookupProfile = { DriverProfile(it, it, null) },
                    onSave = { saved = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("driver-add-psn").performTextInput("friend")
        compose.onNodeWithTag("driver-add-manual").performClick()
        compose.onNodeWithTag("driver-add-name").performTextInput("Friend")
        compose.onNodeWithTag("driver-add-country").performTextInput("US")
        compose.onNodeWithTag("driver-add-save").performClick()

        assertEquals(DriverProfile("friend", "Friend", "US"), saved)
    }

    @Test
    fun `driver row opens the public profile for the assigned driver`() {
        var opened: String? = null

        compose.setContent {
            LapCraftTheme {
                DriverPickerRow(
                    driverPsnId = "friend",
                    profile = DriverProfile("friend", "Friend", "US"),
                    owner = null,
                    profiles = emptyList(),
                    onChange = {},
                    onAddDriver = {},
                    onOpenLink = { opened = it },
                )
            }
        }

        compose.onNodeWithTag("driver-profile").performClick()
        compose.onNodeWithText("GT GridStats").performClick()

        assertEquals("https://gt-gridstats.com/player/friend", opened)
    }

    @Test
    fun `driver row hides profile links when the caller does not want them`() {
        compose.setContent {
            LapCraftTheme {
                DriverPickerRow(
                    driverPsnId = "friend",
                    profile = DriverProfile("friend", "Friend", "US"),
                    owner = null,
                    profiles = emptyList(),
                    onChange = {},
                    onAddDriver = {},
                    onOpenLink = null,
                )
            }
        }

        compose.onNodeWithTag("driver-menu").performClick()
        assertEquals(0, compose.onAllNodesWithText("GT GridStats").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("GTSH-Rank").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("driver-profile").fetchSemanticsNodes().size)
    }

    @Test
    fun `the saved rating is shown in the picker`() {
        compose.setContent {
            LapCraftTheme {
                DriverPickerRow(
                    driverPsnId = "friend",
                    profile = DriverProfile("friend", "Friend", "US", dr = "B", sr = "S"),
                    owner = null,
                    profiles = emptyList(),
                    onChange = {},
                    onAddDriver = {},
                    onOpenLink = null,
                )
            }
        }

        compose.onNodeWithText("DR (B) · SR (S)", substring = true).assertExists()
    }
}
