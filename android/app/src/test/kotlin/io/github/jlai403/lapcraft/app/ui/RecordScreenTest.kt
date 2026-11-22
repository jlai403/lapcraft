package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.jlai403.lapcraft.app.service.TelemetryEvent
import io.github.jlai403.lapcraft.core.capture.CaptureState
import io.github.jlai403.lapcraft.core.session.LapId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun content(
        state: CaptureUiState,
        onConnect: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onCapture: () -> Unit = {},
        onCancelCapture: () -> Unit = {},
        onOpenLap: (LapId) -> Unit = { _ -> },
    ) {
        compose.setContent {
            LapCraftTheme {
                RecordScreen(
                    state = state,
                    onConnect = onConnect,
                    onDisconnect = {},
                    onCapture = onCapture,
                    onCancelCapture = onCancelCapture,
                    onOpenLap = onOpenLap,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("STREAM").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `capture is enabled once a line has been observed`() {
        var captures = 0
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
            ),
            onCapture = { captures++ },
        )

        compose.onNodeWithText("Capture this lap").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Capture this lap").performClick()
        assertEquals(1, captures)
    }

    @Test
    fun `capture waits for the start line`() {
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = false,
            ),
        )

        compose.onNodeWithText("Capture this lap").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cross the start line, then capture.").assertExists()
    }

    @Test
    fun `capturing shows progress and cancel`() {
        var cancelled = 0
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
                capture = CaptureState.Capturing,
            ),
            onCancelCapture = { cancelled++ },
        )

        compose.onNodeWithText("Capturing · ends at the next line").assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `disconnect stays disabled while capturing`() {
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
                capture = CaptureState.Capturing,
            ),
        )

        compose.onNodeWithText("Disconnect").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `saved lap can be opened`() {
        var opened: Pair<String, Int>? = null
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
                capture = CaptureState.Saved(lapId = LapId("lap-7"), lapTimeMs = 159_831),
            ),
            onOpenLap = { id -> opened = id.toString() to 1 },
        )

        compose.onNodeWithText("Lap saved").assertExists()
        compose.onNodeWithText("2:39.831").assertExists()
        compose.onNodeWithText("View lap").performScrollTo().performClick()
        assertEquals("lap-7" to 1, opened)
    }

    @Test
    fun `missing address points at settings`() {
        var openedSettings = 0
        content(
            state = CaptureUiState(ps5Ip = ""),
            onOpenSettings = { openedSettings++ },
        )

        compose.onNodeWithText("Add the console address in Settings, then connect.")
            .assertExists()
        compose.onNodeWithText("Set address").performScrollTo().performClick()
        assertEquals(1, openedSettings)
    }

    @Test
    fun `foreign packets warn without expanding details`() {
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                foreignPackets = 10,
                lastForeignSource = "192.168.1.99",
            ),
        )

        compose.onNodeWithText("Packets come from", substring = true).assertExists()
    }

    @Test
    fun `stream details show decoded fields and lap events`() {
        content(
            state = CaptureUiState(
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapCount = 3,
                speedMps = 50f,
                gear = 4,
                throttle = 200,
                rpm = 7_000f,
                packetId = 84_213,
                carCode = 3474,
                flags = 1,
                lastLapTimeMs = 90_456,
                acceptedPackets = 12_345,
                completedLaps = 2,
                events = listOf(
                    TelemetryEvent(1_760_000_000_000L, "Lap 2 complete · 1:30.456"),
                ),
            ),
        )

        compose.onNodeWithText("Streaming").assertExists()
        compose.onNodeWithText("Show details").performScrollTo().performClick()

        compose.onNodeWithText("Subaru BRZ GT300 '21").assertExists()
        compose.onNodeWithText("On track").assertExists()
        compose.onNodeWithText("Gear 4 · throttle 78% · brake 0% · 7,000 rpm").assertExists()
        compose.onNodeWithText("Lap 2 complete · 1:30.456").assertExists()
    }

    @Test
    fun `the stream card shows and changes the lap setup`() {
        var tcs by mutableStateOf<Int?>(2)
        var balance by mutableStateOf<Int?>(-1)
        compose.setContent {
            LapCraftTheme {
                RecordScreen(
                    state = CaptureUiState(
                        ps5Ip = "192.168.1.50",
                        telemetryRunning = true,
                        connected = true,
                        packetsPerSecond = 59.8,
                    ),
                    onConnect = {},
                    onDisconnect = {},
                    onCapture = {},
                    onCancelCapture = {},
                    onOpenLap = {},
                    tcs = tcs,
                    onTcsChange = { tcs = it },
                    brakeBalance = balance,
                    onBrakeBalanceChange = { balance = it },
                )
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("STREAM").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Traction control").performScrollTo().assertExists()
        compose.onNodeWithText("Brake balance").performScrollTo().assertExists()

        compose.onNodeWithTag("record-tcs-change").performScrollTo().performClick()
        compose.onNodeWithTag("setup-pick-4").performClick()
        assertEquals(4, tcs)
        compose.onNodeWithText("4").performScrollTo().assertExists()

        compose.onNodeWithTag("record-brake-balance-change").performScrollTo().performClick()
        compose.onNodeWithTag("setup-pick--3").performClick()
        assertEquals(-3, balance)
        compose.onNodeWithText("-3").performScrollTo().assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
