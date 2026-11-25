package io.github.jlai403.lapcraft.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.BuildConfig
import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.app.catalog.CarCatalog
import io.github.jlai403.lapcraft.core.capture.CaptureState
import io.github.jlai403.lapcraft.core.network.Ipv4
import io.github.jlai403.lapcraft.core.session.BRAKE_BALANCE_LEVELS
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.TCS_LEVELS
import io.github.jlai403.lapcraft.core.telemetry.TelemetryHealth

@Composable
fun RecordScreen(
    state: CaptureUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onOpenLap: (LapId) -> Unit,
    onEditLap: (LapId) -> Unit = {},
    imperial: Boolean = false,
    ps5Ip: String = "",
    onOpenSettings: () -> Unit = {},
    tyre: io.github.jlai403.lapcraft.core.tire.TyreCompound? = null,
    onTyreChange: (io.github.jlai403.lapcraft.core.tire.TyreCompound?) -> Unit = {},
    tcs: Int? = null,
    onTcsChange: (Int?) -> Unit = {},
    brakeBalance: Int? = null,
    onBrakeBalanceChange: (Int?) -> Unit = {},
) {
    var starting by remember { mutableStateOf(false) }
    val hasAddress = Ipv4.normalize(ps5Ip) != null
    val capturing = state.capture is CaptureState.Capturing
    var tyrePickerOpen by remember { mutableStateOf(false) }
    var tcsPickerOpen by remember { mutableStateOf(false) }
    var brakeBalancePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.telemetryRunning, state.telemetryError) {
        if (state.telemetryRunning || state.telemetryError != null) starting = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Record",
            subtitle = "Connect to GT7, then capture laps.",
        )

        state.telemetryError?.takeIf { it.isNotBlank() }?.let { WarningBanner(it) }
        if (state.foreignPackets > 0 && state.lastForeignSource.isNotBlank()) {
            WarningBanner(
                "Packets come from ${state.lastForeignSource}, not $ps5Ip. " +
                    "Check the console's current address in Settings.",
            )
        }

        StreamCard(
            state = state,
            starting = starting,
            hasAddress = hasAddress,
            ps5Ip = ps5Ip,
            capturing = capturing,
            imperial = imperial,
            tyre = tyre,
            onChangeTyre = { tyrePickerOpen = true },
            tcs = tcs,
            onChangeTcs = { tcsPickerOpen = true },
            brakeBalance = brakeBalance,
            onChangeBrakeBalance = { brakeBalancePickerOpen = true },
            onConnect = {
                starting = true
                onConnect()
            },
            onDisconnect = onDisconnect,
            onOpenSettings = onOpenSettings,
        )

        if (tyrePickerOpen) {
            TyrePickerDialog(
                selected = tyre,
                onPick = {
                    onTyreChange(it)
                    tyrePickerOpen = false
                },
                onClear = {
                    onTyreChange(null)
                    tyrePickerOpen = false
                },
                onDismiss = { tyrePickerOpen = false },
            )
        }

        if (tcsPickerOpen) {
            SetupPickerDialog(
                title = "Traction control",
                levels = TCS_LEVELS,
                selected = tcs,
                label = { if (it == 0) "Off" else it.toString() },
                onPick = {
                    onTcsChange(it)
                    tcsPickerOpen = false
                },
                onClear = {
                    onTcsChange(null)
                    tcsPickerOpen = false
                },
                onDismiss = { tcsPickerOpen = false },
            )
        }

        if (brakeBalancePickerOpen) {
            SetupPickerDialog(
                title = "Brake balance",
                levels = BRAKE_BALANCE_LEVELS,
                selected = brakeBalance,
                label = { if (it > 0) "+$it" else it.toString() },
                onPick = {
                    onBrakeBalanceChange(it)
                    brakeBalancePickerOpen = false
                },
                onClear = {
                    onBrakeBalanceChange(null)
                    brakeBalancePickerOpen = false
                },
                onDismiss = { brakeBalancePickerOpen = false },
            )
        }

        CaptureCard(
            state = state,
            onCapture = onCapture,
            onCancelCapture = onCancelCapture,
            onOpenLap = onOpenLap,
            onEditLap = onEditLap,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StreamCard(
    state: CaptureUiState,
    starting: Boolean,
    hasAddress: Boolean,
    ps5Ip: String,
    capturing: Boolean,
    imperial: Boolean,
    tyre: io.github.jlai403.lapcraft.core.tire.TyreCompound?,
    onChangeTyre: () -> Unit,
    tcs: Int?,
    onChangeTcs: () -> Unit,
    brakeBalance: Int?,
    onChangeBrakeBalance: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val carCatalog = remember { CarCatalog(context) }
    val health = TelemetryHealth.evaluate(
        connected = state.connected,
        packetsPerSecond = state.packetsPerSecond,
        lastPacketAgeMs = state.lastPacketAgeMs,
    )
    val statusColor = when {
        health.healthy -> MaterialTheme.colorScheme.primary
        !state.connected -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    SectionCard(
        title = "Stream",
        action = {
            GhostPill(
                text = when {
                    state.telemetryRunning -> "Disconnect"
                    starting -> "Connecting…"
                    !hasAddress -> "Set address"
                    else -> "Connect"
                },
                enabled = !starting && !(state.telemetryRunning && capturing),
                onClick = when {
                    state.telemetryRunning -> onDisconnect
                    hasAddress -> onConnect
                    else -> onOpenSettings
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(health.healthy)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    health.reason,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                )
                Text(
                    streamSummary(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val hint = when {
            !state.telemetryRunning && !hasAddress ->
                "Add the console address in Settings, then connect."
            !state.telemetryRunning -> "Connect to start listening."
            !state.connected -> "GT7 sends telemetry only while a race or time trial is on screen."
            else -> null
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(
                label = "packets/s",
                value = "%.0f".format(state.packetsPerSecond),
                modifier = Modifier.weight(1f),
                accent = if (health.healthy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            StatChip(
                label = "GT7 lap",
                value = if (state.lapCount < 0) "—" else state.lapCount.toString(),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = speedUnit(imperial),
                value = "%.0f".format(speedValue(state.speedMps, imperial)),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Tyres",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tyre != null) {
                TyreBadge(compound = tyre, size = 20.dp)
                Text(
                    tyre.label,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Not set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            GhostPill(text = "Change", onClick = onChangeTyre)
        }

        SetupRow(
            label = "Traction control",
            value = tcsRowValue(tcs),
            unset = tcs == null,
            onChange = onChangeTcs,
            changeTag = "record-tcs-change",
        )

        SetupRow(
            label = "Brake balance",
            value = brakeBalanceRowValue(brakeBalance),
            unset = brakeBalance == null,
            onChange = onChangeBrakeBalance,
            changeTag = "record-brake-balance-change",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostPill(
                text = if (expanded) "Hide details" else "Show details",
                onClick = { expanded = !expanded },
            )
            if (expanded) {
                GhostPill("Copy diagnostics", onClick = { copyDiagnostics(context, ps5Ip, state) })
            }
        }

        if (!expanded) return@SectionCard

        MetricRow("Car", carLabel(carCatalog, state.carCode), mono = false)
        MetricRow("Track", if (state.flags and 1 != 0) "On track" else "Off track", mono = false)
        MetricRow("Pedals", pedalLabel(state), mono = false)
        MetricRow(
            "Accepted",
            "%s · %d decode errors".format(count(state.acceptedPackets), state.decodeErrors),
        )
        MetricRow(
            "Received",
            "%s · %d from another address".format(count(state.rawPackets), state.foreignPackets),
        )
        MetricRow(
            "Laps",
            "%d captured · GT7 counter %d · last %s".format(
                state.completedLaps,
                state.lapCount,
                lapTimeLabel(state.lastLapTimeMs),
            ),
            mono = false,
        )

        SectionLabel("Recent events")
        if (state.events.isEmpty()) {
            Text(
                "Nothing yet. Connections, laps and car changes show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.events.take(5).forEach { event ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericText(
                        formatEventTime(event.atEpochMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(event.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    state: CaptureUiState,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onOpenLap: (LapId) -> Unit,
    onEditLap: (LapId) -> Unit = {},
) {
    val canCapture = state.connected && state.lapStartObserved
    val hint = when {
        state.capture is CaptureState.Capturing -> null
        state.capture is CaptureState.Saved && canCapture -> "Ready for the next lap."
        !state.telemetryRunning -> "Connect first."
        !state.connected -> "Waiting for telemetry."
        !state.lapStartObserved -> "Cross the start line, then capture."
        else -> "Saves this lap when you cross the line again."
    }

    SectionCard(title = "Capture") {
        when (val capture = state.capture) {
            is CaptureState.Saved -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Lap saved", style = MaterialTheme.typography.titleMedium)
                    NumericText(
                        lapTimeLabel(capture.lapTimeMs),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPill(
                        text = "Capture another lap",
                        onClick = onCapture,
                        modifier = Modifier.weight(1f),
                        enabled = canCapture,
                    )
                    GhostPill(
                        text = "View lap",
                        onClick = { onOpenLap(capture.lapId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                GhostPill(
                    text = "Edit lap",
                    onClick = { onEditLap(capture.lapId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CaptureState.Capturing -> {
                Text(
                    "Capturing · ends at the next line",
                    style = MaterialTheme.typography.titleMedium,
                )
                GhostPill("Cancel", onCancelCapture)
            }

            is CaptureState.Rejected -> {
                Text(
                    capture.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                PrimaryPill(
                    text = "Capture this lap",
                    onClick = onCapture,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCapture,
                )
            }

            CaptureState.Idle -> {
                PrimaryPill(
                    text = "Capture this lap",
                    onClick = onCapture,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCapture,
                )
            }
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, mono: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (mono) {
            NumericText(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun streamSummary(state: CaptureUiState): String {
    val age = when {
        state.lastPacketAgeMs < 0 -> "no packets yet"
        state.lastPacketAgeMs < 1_000 -> "last packet ${state.lastPacketAgeMs} ms ago"
        else -> "last packet %.1f s ago".format(state.lastPacketAgeMs / 1_000f)
    }
    val errors = if (state.decodeErrors > 0) {
        " · ${count(state.decodeErrors)} decode errors"
    } else {
        ""
    }
    return "%.1f packets/s · %s%s".format(state.packetsPerSecond, age, errors)
}

private fun carLabel(catalog: CarCatalog, carCode: Int): String {
    if (carCode == 0) return "-"
    return catalog.name(carCode) ?: "Code $carCode"
}

private fun pedalLabel(state: CaptureUiState): String {
    val gear = if (state.gear <= 0) "Neutral" else "Gear ${state.gear}"
    return "%s · throttle %d%% · brake %d%% · %,.0f rpm".format(
        gear,
        state.throttle * 100 / 255,
        state.brake * 100 / 255,
        state.rpm,
    )
}

private fun count(value: Long): String = "%,d".format(value)

private fun copyDiagnostics(context: Context, address: String, state: CaptureUiState) {
    val text = buildString {
        appendLine(
            "LapCraft ${BuildConfig.VERSION_NAME} " +
                "(build ${BuildConfig.BUILD_NUMBER}, ${BuildConfig.GIT_SHA})",
        )
        appendLine("Console address: $address")
        appendLine("Connected: ${state.connected}")
        appendLine("Packets/s: %.1f".format(state.packetsPerSecond))
        appendLine("Accepted: ${state.acceptedPackets}, decode errors: ${state.decodeErrors}")
        appendLine("Last decode failure: ${state.lastDecodeFailure.ifBlank { "none" }}")
        appendLine(
            "Datagrams: ${state.rawPackets} received, ${state.foreignPackets} from other hosts " +
                "(${state.lastForeignSource.ifBlank { "none" }})",
        )
        appendLine("Last packet age: ${state.lastPacketAgeMs} ms")
        appendLine(
            "Packet ID: ${state.packetId}, car: ${state.carCode}, " +
                "flags: 0x%04X".format(state.flags),
        )
        appendLine(
            "Laps: ${state.completedLaps} captured, GT7 lap ${state.lapCount}, " +
                "last ${lapTimeLabel(state.lastLapTimeMs)}",
        )
        appendLine(
            "Speed: %.1f m/s, gear ${state.gear}, throttle ${state.throttle}, " +
                "brake ${state.brake}, rpm %.0f".format(state.speedMps, state.rpm),
        )
        appendLine("Capture: ${state.capture}")
        appendLine("Events:")
        state.events.take(5).forEach {
            appendLine("- ${formatEventTime(it.atEpochMs)} ${it.message}")
        }
        appendLine("Recent log:")
        Diagnostics.tail(context, 40).forEach { appendLine(it) }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("GT7 diagnostics", text))
    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
}
