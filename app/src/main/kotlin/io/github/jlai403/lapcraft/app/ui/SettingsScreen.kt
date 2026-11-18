package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.jlai403.lapcraft.BuildConfig
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.app.profile.DriverProfileLinks
import io.github.jlai403.lapcraft.app.profile.GridStatsProfileClient
import io.github.jlai403.lapcraft.app.events.EventCache
import io.github.jlai403.lapcraft.app.events.EventRefresh
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.updater.UpdateManager
import io.github.jlai403.lapcraft.core.network.Ipv4

@Composable
fun SettingsScreen(
    update: UpdateManager.State = UpdateManager.State.Idle,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    lookupProfile: (suspend (String) -> DriverProfile)? = null,
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val resolvedLookup: suspend (String) -> DriverProfile =
        lookupProfile ?: { GridStatsProfileClient { io.github.jlai403.lapcraft.app.profile.DriverProfileStore(context).apiKey }.fetch(it) }

    var ps5Ip by remember { mutableStateOf(settings.ps5Ip) }
    var ipDialog by remember { mutableStateOf(false) }
    var ipStatus by remember { mutableStateOf<String?>(null) }
    var imperial by remember { mutableStateOf(settings.imperialUnits) }
    var haptics by remember { mutableStateOf(settings.hapticsEnabled) }
    var defaultSpeed by remember { mutableStateOf(settings.defaultPlaybackSpeed) }
    var speedMenu by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf(settings.telemetryHud) }
    var chartWindowSeconds by remember { mutableStateOf(settings.chartWindowSeconds) }
    var chartWindowMenu by remember { mutableStateOf(false) }
    var previewEmpty by remember { mutableStateOf(settings.previewEmptyLibrary) }
    var developerTools by remember { mutableStateOf(settings.developerTools) }
    var eventTags by remember { mutableStateOf(settings.eventTags) }
    var refreshingEvents by remember { mutableStateOf(false) }
    var eventStatus by remember { mutableStateOf<String?>(null) }
    var replayPreset by remember { mutableStateOf(settings.replayPreset) }
    var replayKeyframes by remember { mutableStateOf(settings.replayKeyframes) }
    var replayAudio by remember { mutableStateOf(settings.replayAudio) }
    var replayResolution by remember { mutableStateOf(settings.replayResolution) }
    var replayCodec by remember { mutableStateOf(settings.replayCodec) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Display, console, replay, and data",
        )

        SectionCard(title = "Display & playback") {
            SettingToggleRow(
                label = "Imperial units",
                checked = imperial,
                modifier = Modifier.testTag("imperial"),
            ) {
                imperial = it
                settings.imperialUnits = it
            }
            SettingToggleRow(
                label = "Frame ruler haptics",
                supporting = "Ticks as you drag the ruler.",
                checked = haptics,
                modifier = Modifier.testTag("haptics"),
            ) {
                haptics = it
                settings.hapticsEnabled = it
            }
            SettingToggleRow(
                label = "Telemetry HUD",
                supporting = "Speed, gear, and pedal sparklines over the replay.",
                checked = hud,
                modifier = Modifier.testTag("telemetry-hud"),
            ) {
                hud = it
                settings.telemetryHud = it
            }
            Box {
                SettingRow(
                    label = "Default playback speed",
                    value = playbackSpeedLabel(defaultSpeed),
                    trailing = { ChevronDown() },
                    modifier = Modifier.testTag("default-playback-speed"),
                    onClick = { speedMenu = true },
                )
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    PLAYBACK_SPEEDS.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(playbackSpeedLabel(speed)) },
                            onClick = {
                                defaultSpeed = speed
                                settings.defaultPlaybackSpeed = speed
                                speedMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                SettingRow(
                    label = "Chart window",
                    value = "\u00b1${chartWindowSeconds}s",
                    trailing = { ChevronDown() },
                    modifier = Modifier.testTag("chart-window"),
                    onClick = { chartWindowMenu = true },
                )
                DropdownMenu(expanded = chartWindowMenu, onDismissRequest = { chartWindowMenu = false }) {
                    listOf(2, 3, 5, 10).forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text("\u00b1${seconds}s") },
                            onClick = {
                                chartWindowSeconds = seconds
                                settings.chartWindowSeconds = seconds
                                chartWindowMenu = false
                            },
                        )
                    }
                }
            }
        }

        SectionCard(title = "Console") {
            SettingRow(
                label = "PS5 address",
                supporting = ipStatus,
                value = ps5Ip.ifBlank { "Not set" },
                modifier = Modifier.testTag("settings-ps5-edit"),
                onClick = { ipDialog = true },
                trailing = { ChevronDown() },
            )
        }

        SectionCard(title = "Replay compression") {
            Text(
                "New imports use these. The import screen can override them for one lap; existing laps keep their clips.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReplayCompressionControls(
                preset = replayPreset,
                keyframes = replayKeyframes,
                audio = replayAudio,
                resolution = replayResolution,
                codec = replayCodec,
                highlightChanges = true,
                onPresetChange = {
                    replayPreset = it
                    settings.replayPreset = it
                },
                onResolutionChange = {
                    replayResolution = it
                    settings.replayResolution = it
                },
                onCodecChange = {
                    replayCodec = it
                    settings.replayCodec = it
                },
                onKeyframesChange = {
                    replayKeyframes = it
                    settings.replayKeyframes = it
                },
                onAudioChange = {
                    replayAudio = it
                    settings.replayAudio = it
                },
            )
        }

        DriverProfileSection(lookupProfile = resolvedLookup)

        LibrarySection()

        SectionCard(title = "Event tags") {
            SettingToggleRow(
                label = "Event tags",
                supporting = "Tag laps with Daily Races, Time Trials, and World Series rounds. Uses your GT GridStats key and the official GT championship schedule.",
                checked = eventTags,
                modifier = Modifier.testTag("event-tags"),
            ) {
                eventTags = it
                settings.eventTags = it
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostPill(
                    text = if (refreshingEvents) "Refreshing…" else "Refresh events",
                    onClick = {
                        if (refreshingEvents) return@GhostPill
                        refreshingEvents = true
                        eventStatus = null
                        scope.launch {
                            try {
                                val result = EventRefresh.refresh(context, force = true)
                                val cache = EventCache(context)
                                val msg = mutableListOf<String>()
                                msg += "CDN ${result.polyphonyCount}"
                                if (DriverProfileStore(context).apiKey?.isNotBlank() == true) {
                                    msg += "GridStats ${result.gridStatsCount}"
                                }
                                eventStatus = "Updated · ${cache.events().size} events (${msg.joinToString(", ")})"
                                if (result.errors.isNotEmpty()) {
                                    eventStatus = (eventStatus ?: "") + " · " + result.errors.joinToString("; ")
                                }
                            } catch (e: Exception) {
                                eventStatus = e.message ?: "Refresh failed."
                            } finally {
                                refreshingEvents = false
                            }
                        }
                    },
                    enabled = !refreshingEvents,
                    modifier = Modifier.testTag("refresh-events"),
                )
                eventStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val cache = remember { EventCache(context) }
            Text(
                "Last updated: ${cache.load().gridstatsFetchedAtMs.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "never"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (BuildConfig.UPDATE_CHANNEL != "stable") {
            SectionCard(title = "Developer") {
                SettingToggleRow(
                    label = "Developer tools",
                    checked = developerTools,
                    modifier = Modifier.testTag("dev-developer-tools"),
                ) {
                    developerTools = it
                    settings.developerTools = it
                }
                SettingToggleRow(
                    label = "Preview empty library",
                    supporting = "Shows the empty state while your laps stay saved.",
                    checked = previewEmpty,
                    modifier = Modifier.testTag("dev-preview-empty"),
                ) {
                    previewEmpty = it
                    settings.previewEmptyLibrary = it
                }
                if (developerTools) {
                    SectionLabel("Updates")
                    UpdatesContent(
                        update = update,
                        onCheckUpdate = onCheckUpdate,
                        onDownloadUpdate = onDownloadUpdate,
                        onInstallUpdate = onInstallUpdate,
                    )
                }
            }
        }

        SectionCard(title = "About") {
            SettingRow(
                label = "Version",
                value = "${BuildConfig.VERSION_NAME} · ${BuildConfig.UPDATE_CHANNEL} · ${BuildConfig.GIT_SHA}",
                modifier = Modifier.testTag("about-expand"),
                onClick = { aboutExpanded = !aboutExpanded },
                trailing = { Chevron(expanded = aboutExpanded) },
            )
            if (aboutExpanded) {
                BuildRow("Version", BuildConfig.VERSION_NAME)
                BuildRow("Version code", BuildConfig.VERSION_CODE.toString())
                BuildRow("Update channel", BuildConfig.UPDATE_CHANNEL)
                BuildRow("Build number", BuildConfig.BUILD_NUMBER)
                BuildRow("Commit", BuildConfig.GIT_SHA)
            }
            AcknowledgementsRow()
        }
    }

    if (ipDialog) {
        var draft by remember { mutableStateOf(ps5Ip) }
        var draftError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { ipDialog = false },
            title = { Text("PS5 address") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            draftError = false
                        },
                        label = { Text("IPv4 address") },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        isError = draftError,
                        modifier = Modifier.fillMaxWidth().testTag("settings-ps5-ip"),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text(
                        "The app sends heartbeats to this address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (draftError) {
                        Text(
                            "Enter a valid IPv4 address",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = Ipv4.normalize(draft)
                    if (normalized == null) {
                        draftError = true
                    } else {
                        ps5Ip = normalized
                        settings.savePs5Ip(normalized)
                        ipStatus = "Saved $normalized"
                        ipDialog = false
                    }
                }) { Text("Save IP") }
            },
            dismissButton = {
                TextButton(onClick = { ipDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChevronDown() {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Chevron(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UpdatesContent(
    update: UpdateManager.State,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            updateSummary(update),
            style = MaterialTheme.typography.bodySmall,
            color = if (update is UpdateManager.State.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        when (update) {
            is UpdateManager.State.Available -> GhostPill(
                text = "Download ${update.manifest.versionName}",
                onClick = onDownloadUpdate,
            )
            is UpdateManager.State.Ready -> GhostPill(
                text = "Install update",
                onClick = onInstallUpdate,
            )
            is UpdateManager.State.Downloading -> GhostPill(
                text = "Downloading ${update.percent}%",
                enabled = false,
                onClick = {},
            )
            is UpdateManager.State.Checking -> GhostPill(
                text = "Checking…",
                enabled = false,
                onClick = {},
            )
            is UpdateManager.State.Installing -> GhostPill(
                text = "Installing…",
                enabled = false,
                onClick = {},
            )
            else -> GhostPill("Check for updates", onClick = onCheckUpdate)
        }
    }
}

@Composable
private fun AcknowledgementsRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DriverProfileLinks.gridStatsHome))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
            .testTag("ack-gridstats"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("GT GridStats ↗", style = MaterialTheme.typography.titleMedium)
            Text(
                "Driver profiles, DR/SR, daily races, time trials, and car names.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun updateSummary(update: UpdateManager.State): String = when (update) {
    is UpdateManager.State.Idle -> "Checks the rolling debug build once a day."
    is UpdateManager.State.Checking -> "Checking for updates…"
    is UpdateManager.State.UpToDate -> "You are on the latest build."
    is UpdateManager.State.Available -> buildString {
        append("Version ${update.manifest.versionName} (build ${update.manifest.versionCode}) is available.")
        if (update.manifest.notes.isNotBlank()) append(" ${update.manifest.notes}")
    }
    is UpdateManager.State.Downloading -> "Downloading ${update.percent}%…"
    is UpdateManager.State.Ready -> "Downloaded. Install opens Android's installer."
    is UpdateManager.State.Installing -> "Waiting for the system installer…"
    is UpdateManager.State.Failed -> update.message
}

@Composable
private fun BuildRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumericText(value, style = MaterialTheme.typography.bodySmall)
    }
}
