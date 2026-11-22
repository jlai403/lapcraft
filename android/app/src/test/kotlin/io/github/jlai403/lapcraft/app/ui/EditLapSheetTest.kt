package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapSummary
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class EditLapSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val lap = LapSummary(
        lapId = LapId("session-1"),
        lapTimeMs = 90_000,
        createdAt = "2026-11-11T10:00:00Z",
        trackLabel = "Suzuka",
        carName = "BRZ GT300 '21",
        hasReplay = false,
        replayPending = false,
    )

    private val daily = CachedEvent(
        id = "daily-Daily Race A-2026-11-16",
        kind = EventKind.Daily,
        label = "Daily Race A · W47",
        trackName = "Suzuka Circuit",
        carClass = "Gr.4",
        startMs = 1_763_276_400_000,
        endMs = 1_763_880_800_000,
        source = "gridstats",
        fetchedAtMs = 1_763_276_400_000,
    )

    @Before
    fun clearProfiles() {
        DriverProfileStore(context).clear()
    }

    @Test
    fun `shows the lap data volume`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(
                        hasReplay = true,
                        videoBytes = 34_200_000,
                        telemetryBytes = 1_800_000,
                    ),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("36.0 MB total").performScrollTo().assertExists()
        compose.onNodeWithText("Replay 34.2 MB · Telemetry 1.8 MB").performScrollTo().assertExists()
    }

    @Test
    fun `renames and saves the trimmed track name`() {
        var saved: String? = null

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = { saved = it },
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Suzuka").assertExists()
        compose.onNodeWithTag("edit-track-rename").performClick()
        compose.onNodeWithTag("edit-track").performTextReplacement("  Suzuka East  ")
        compose.onNodeWithTag("edit-track").performImeAction()

        assertEquals("Suzuka East", saved)
    }

    @Test
    fun `committing a track does not close the sheet`() {
        var dismissed = false

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = { dismissed = true },
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithTag("edit-track-rename").performClick()
        compose.onNodeWithTag("edit-track").performTextReplacement("Suzuka East")
        compose.onNodeWithTag("edit-track").performImeAction()
        compose.onNodeWithTag("edit-done").performScrollTo()

        assertEquals(false, dismissed)
    }

    @Test
    fun `cancelling a rename keeps the old track`() {
        var saved: String? = null

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = { saved = it },
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithTag("edit-track-rename").performClick()
        compose.onNodeWithTag("edit-track").performTextReplacement("Suzuka East")
        compose.onNodeWithText("Cancel").performClick()

        assertNull(saved)
        compose.onNodeWithText("Suzuka").assertExists()
    }

    @Test
    fun `unknown laps start with the track field open`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(trackLabel = "Unknown"),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = listOf(TrackCandidate("Road Atlanta", "a", 1f)),
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithTag("edit-track").assertExists()
        compose.onNodeWithText("Road Atlanta").assertExists()
    }

    @Test
    fun `a track candidate commits straight away`() {
        var saved: String? = null

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(trackLabel = "Unknown"),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = listOf(TrackCandidate("Road Atlanta", "a", 1f)),
                    onDismiss = {},
                    onSaveTrack = { saved = it },
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Road Atlanta").performClick()

        assertEquals("Road Atlanta", saved)
    }

    @Test
    fun `typing an event saves a manual championship tag`() {
        var assigned: EventTag? = null

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                    onAssignEvent = { assigned = it },
                )
            }
        }

        compose.onNodeWithTag("edit-event").performTextReplacement("World Series · R3")
        compose.onNodeWithTag("edit-event").performImeAction()

        assertEquals(EventKind.Championship, assigned?.kind)
        assertEquals("World Series · R3", assigned?.label)
        assertEquals(false, assigned?.auto)
    }

    @Test
    fun `picking a suggestion stores the real cached event`() {
        var assigned: EventTag? = null

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                    events = listOf(daily),
                    onAssignEvent = { assigned = it },
                )
            }
        }

        compose.onNodeWithTag("edit-event").performTextReplacement("daily")
        compose.onNodeWithTag("event-suggestion-${daily.id}").performClick()

        assertEquals(daily.id, assigned?.id)
        assertEquals(EventKind.Daily, assigned?.kind)
        assertEquals(false, assigned?.auto)
    }

    @Test
    fun `an empty cache offers a refresh instead of a dead end`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithTag("edit-event-refresh").performScrollTo().assertExists()
    }

    @Test
    fun `shows the assigned driver without profile links`() {
        val friend = DriverProfile("friend", "Friend", "US")

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(driverPsnId = "friend"),
                    driver = friend,
                    owner = null,
                    profiles = listOf(friend),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Friend").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("driver-profile").fetchSemanticsNodes().size)
        compose.onNodeWithTag("driver-menu").performClick()
        assertEquals(0, compose.onAllNodesWithText("GT GridStats").fetchSemanticsNodes().size)
    }

    @Test
    fun `removing a saved driver clears the cache and the lap`() {
        val store = DriverProfileStore(context)
        store.save(DriverProfile("friend", "Friend", "US"))
        var assigned: DriverProfile? = DriverProfile("friend", "Friend", "US")

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(driverPsnId = "friend"),
                    driver = DriverProfile("friend", "Friend", "US"),
                    owner = null,
                    profiles = listOf(DriverProfile("friend", "Friend", "US")),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = { assigned = it },
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithTag("driver-menu").performClick()
        compose.onNodeWithTag("driver-remove-friend").performClick()
        compose.onNodeWithTag("edit-driver-remove-confirm").performClick()

        assertNull(store.profile("friend"))
        assertNull(assigned)
    }

    @Test
    fun `replace replay opens the playstation app from its own row`() {
        var addReplay = 0
        var playStation = 0

        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(hasReplay = true),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = { addReplay++ },
                    onOpenPlayStation = { playStation++ },
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Replace replay").performClick()
        compose.onNodeWithText("PlayStation App").performClick()

        assertEquals(0, addReplay)
        assertEquals(1, playStation)
    }

    @Test
    fun `offers to add a replay when there is none`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(lap, null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("No replay").assertExists()
        compose.onNodeWithText("Add a replay").assertExists()
    }

    @Test
    fun `offers to resume a pending sync`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(lap.copy(replayPending = true), null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("Sync pending").assertExists()
        compose.onNodeWithText("Resume sync").assertExists()
    }

    @Test
    fun `a pending sync still offers a replacement`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap.copy(hasReplay = true, replayPending = true),
                    null,
                    null,
                    emptyList(),
                    null,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("Resume sync").assertExists()
        compose.onNodeWithText("Replace replay").assertExists()
    }

    @Test
    fun `hides the remove action when there is no replay`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(lap, null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onAllNodesWithTag("edit-replay-remove").fetchSemanticsNodes().let {
            org.junit.Assert.assertTrue(it.isEmpty())
        }
    }

    @Test
    fun `shows the remove action when a replay exists`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(lap.copy(hasReplay = true), null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("edit-replay-remove").assertExists()
    }

    @Test
    fun `confirming remove fires the callback`() {
        var removed = 0
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(hasReplay = true),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                    onRemoveReplay = { removed++ },
                )
            }
        }

        compose.onNodeWithTag("edit-replay-remove").performScrollTo().performClick()
        compose.onNodeWithTag("edit-replay-remove-confirm").performClick()

        assertEquals(1, removed)
    }

    @Test
    fun `cancelling remove does not fire the callback`() {
        var removed = 0
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(hasReplay = true),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                    onRemoveReplay = { removed++ },
                )
            }
        }

        compose.onNodeWithTag("edit-replay-remove").performScrollTo().performClick()
        compose.onNodeWithTag("edit-replay-remove-cancel").performClick()

        assertEquals(0, removed)
    }

    @Test
    fun `an auto tag is marked as auto`() {
        compose.setContent {
            LapCraftTheme {
                EditLapSheet(
                    lap = lap.copy(
                        event = EventTag(
                            id = daily.id,
                            kind = EventKind.Daily,
                            label = daily.label,
                            auto = true,
                        ),
                    ),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Auto-tagged").assertExists()
        assertTrue(compose.onAllNodesWithText(daily.label).fetchSemanticsNodes().isNotEmpty())
    }
}
