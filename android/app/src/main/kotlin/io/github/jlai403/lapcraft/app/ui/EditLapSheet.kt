package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.app.events.EventCache
import io.github.jlai403.lapcraft.app.events.EventRefresh
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.profile.GridStatsProfileClient
import io.github.jlai403.lapcraft.app.track.TrackNamer
import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventMatcher
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.events.eventRangeLabel
import io.github.jlai403.lapcraft.core.events.toTag
import io.github.jlai403.lapcraft.core.events.weekStartLabel
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.core.session.BRAKE_BALANCE_LEVELS
import io.github.jlai403.lapcraft.core.session.LapSummary
import io.github.jlai403.lapcraft.core.session.TCS_LEVELS
import io.github.jlai403.lapcraft.core.tire.TyreCompound
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditLapSheet(
    lap: LapSummary,
    driver: DriverProfile?,
    owner: DriverProfile?,
    profiles: List<DriverProfile>,
    candidates: List<TrackCandidate>?,
    onDismiss: () -> Unit,
    onSaveTrack: (String) -> Unit,
    onAssignDriver: (DriverProfile?) -> Unit,
    onAddReplay: () -> Unit,
    onOpenPlayStation: () -> Unit,
    onResumeSync: () -> Unit,
    developerTools: Boolean = false,
    reencoding: Boolean = false,
    reencodeResult: Boolean? = null,
    onReencode: () -> Unit = {},
    events: List<CachedEvent> = emptyList(),
    onAssignEvent: (EventTag?) -> Unit = {},
    tyre: io.github.jlai403.lapcraft.core.tire.TyreCompound? = null,
    onAssignTyre: (io.github.jlai403.lapcraft.core.tire.TyreCompound?) -> Unit = {},
    tcs: Int? = null,
    onAssignTcs: (Int?) -> Unit = {},
    brakeBalance: Int? = null,
    onAssignBrakeBalance: (Int?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val trackFocus = remember { FocusRequester() }

    val storedTrack = lap.trackLabel.takeUnless { it == TrackNamer.UNKNOWN }.orEmpty()
    var track by remember(lap.trackLabel) { mutableStateOf(storedTrack) }
    var trackDraft by remember(lap.trackLabel) { mutableStateOf(storedTrack) }
    var renamingTrack by remember(lap.trackLabel) { mutableStateOf(lap.trackLabel == TrackNamer.UNKNOWN) }
    var hadTrackFocus by remember { mutableStateOf(false) }
    var cancellingTrack by remember { mutableStateOf(false) }

    var driverPsnId by remember(lap.driverPsnId) { mutableStateOf(lap.driverPsnId) }
    var localProfiles by remember(profiles) { mutableStateOf(profiles) }
    var removingDriver by remember { mutableStateOf<DriverProfile?>(null) }
    var addingDriver by remember { mutableStateOf(false) }

    var localEvents by remember(events) { mutableStateOf(events) }
    var eventQuery by remember(lap.event?.id) { mutableStateOf(lap.event?.label.orEmpty()) }
    var suggestionsOpen by remember { mutableStateOf(false) }
    var refreshingEvents by remember { mutableStateOf(false) }
    var tyrePickerOpen by remember { mutableStateOf(false) }
    var localTyre by remember(tyre) { mutableStateOf(tyre) }
    var tcsPickerOpen by remember { mutableStateOf(false) }
    var localTcs by remember(tcs) { mutableStateOf(tcs) }
    var brakeBalancePickerOpen by remember { mutableStateOf(false) }
    var localBrakeBalance by remember(brakeBalance) { mutableStateOf(brakeBalance) }

    fun assignTyre(compound: TyreCompound?) {
        localTyre = compound
        onAssignTyre(compound)
    }

    fun assignTcs(level: Int?) {
        localTcs = level
        onAssignTcs(level)
    }

    fun assignBrakeBalance(level: Int?) {
        localBrakeBalance = level
        onAssignBrakeBalance(level)
    }

    val selectedDriver = localProfiles.firstOrNull { it.psnId.equals(driverPsnId, true) }
        ?: driver?.takeIf { it.psnId.equals(driverPsnId, true) }
        ?: owner?.takeIf { it.psnId.equals(driverPsnId, true) }

    fun assign(profile: DriverProfile?) {
        driverPsnId = profile?.psnId
        if (profile != null && localProfiles.none { it.psnId.equals(profile.psnId, true) }) {
            localProfiles = localProfiles + profile
        }
        onAssignDriver(profile)
    }

    fun commitTrack() {
        renamingTrack = false
        val name = trackDraft.trim().ifBlank { TrackNamer.UNKNOWN }
        trackDraft = name.takeUnless { it == TrackNamer.UNKNOWN }.orEmpty()
        if (name == lap.trackLabel) return
        track = trackDraft
        onSaveTrack(name)
    }

    fun cancelTrack() {
        cancellingTrack = true
        renamingTrack = false
        trackDraft = track
    }

    fun pickEvent(event: CachedEvent) {
        eventQuery = event.label
        suggestionsOpen = false
        focusManager.clearFocus()
        onAssignEvent(event.toTag())
    }

    fun commitEvent() {
        suggestionsOpen = false
        val typed = eventQuery.trim()
        if (typed.isEmpty() || typed == lap.event?.label) return
        val exact = localEvents.firstOrNull { it.label.equals(typed, ignoreCase = true) }
        onAssignEvent(
            exact?.toTag() ?: EventTag(
                id = "manual-${System.currentTimeMillis()}",
                kind = EventKind.Championship,
                label = typed,
                auto = false,
            ),
        )
    }

    fun clearEvent() {
        eventQuery = ""
        suggestionsOpen = false
        focusManager.clearFocus()
        onAssignEvent(null)
    }

    fun refreshEvents() {
        if (refreshingEvents) return
        refreshingEvents = true
        scope.launch {
            runCatching { EventRefresh.refresh(context, force = true) }
            localEvents = withContext(Dispatchers.IO) { EventCache(context).events() }
            refreshingEvents = false
        }
    }

    val suggestions = if (!suggestionsOpen || eventQuery.isBlank()) {
        emptyList()
    } else {
        val query = EventMatcher.normalize(eventQuery)
        localEvents.filter { event ->
            listOfNotNull(event.label, event.trackName, event.series, event.carClass)
                .any { EventMatcher.normalize(it).contains(query) }
        }.take(6)
    }

    LaunchedEffect(renamingTrack) {
        if (renamingTrack) trackFocus.requestFocus()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                lapTimeLabel(lap.lapTimeMs),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("edit-lap-title"),
            )
            Text(
                listOfNotNull(
                    storedTrack.ifBlank { "Unknown track" },
                    lap.carName?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EditRow(
                label = "Track",
                value = {
                    Text(
                        track.ifBlank { "Unnamed track" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (renamingTrack) {
                        GhostPill("Cancel", { cancelTrack() })
                    } else {
                        GhostPill(
                            text = if (track.isBlank()) "Name" else "Rename",
                            onClick = {
                                cancellingTrack = false
                                renamingTrack = true
                            },
                            modifier = Modifier.testTag("edit-track-rename"),
                        )
                    }
                },
            )
            if (renamingTrack) {
                OutlinedTextField(
                    value = trackDraft,
                    onValueChange = { trackDraft = it },
                    singleLine = true,
                    placeholder = { Text("Track name") },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            commitTrack()
                            keyboard?.hide()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(trackFocus)
                        .onFocusChanged {
                            if (hadTrackFocus && !it.isFocused) {
                                if (cancellingTrack) cancellingTrack = false else commitTrack()
                            }
                            hadTrackFocus = it.isFocused
                        }
                        .testTag("edit-track"),
                )
                if (lap.trackLabel == TrackNamer.UNKNOWN) {
                    when {
                        candidates == null -> Text(
                            "Finding tracks…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> candidates.forEach { candidate ->
                            Text(
                                candidate.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        trackDraft = candidate.name
                                        commitTrack()
                                    }
                                    .padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            EditRow(
                label = "Event",
                value = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = eventQuery,
                            onValueChange = {
                                eventQuery = it
                                suggestionsOpen = true
                            },
                            singleLine = true,
                            placeholder = { Text("Event name (e.g. World Series · R3)") },
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = if (lap.event != null) {
                                {
                                    IconButton(
                                        onClick = { clearEvent() },
                                        modifier = Modifier.testTag("edit-event-clear"),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Clear event",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    commitEvent()
                                    keyboard?.hide()
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit-event"),
                        )
                        if (lap.event?.auto == true) {
                            Text(
                                "Auto-tagged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        suggestions.forEach { event ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pickEvent(event) }
                                    .padding(vertical = 6.dp)
                                    .testTag("event-suggestion-${event.id}"),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(event.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    eventSubtitle(event),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (localEvents.isEmpty()) {
                        GhostPill(
                            text = if (refreshingEvents) "Refreshing…" else "Refresh events",
                            onClick = { refreshEvents() },
                            modifier = Modifier.testTag("edit-event-refresh"),
                        )
                    }
                },
            )

            EditRow(
                label = "Driver",
                value = {
                    DriverPickerRow(
                        driverPsnId = driverPsnId,
                        profile = selectedDriver,
                        owner = owner,
                        profiles = localProfiles,
                        onChange = ::assign,
                        onAddDriver = { addingDriver = true },
                        onOpenLink = null,
                        onRemoveProfile = { removingDriver = it },
                    )
                },
            )

            EditRow(
                label = "Replay",
                value = {
                    Text(
                        when {
                            lap.replayPending -> "Sync pending"
                            lap.hasReplay -> "Saved"
                            else -> "No replay"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                actions = {
                    if (lap.replayPending) {
                        GhostPill(
                            text = "Resume sync",
                            onClick = {
                                onDismiss()
                                onResumeSync()
                            },
                        )
                    }
                    ReplaySourcePill(
                        text = if (lap.hasReplay || lap.replayPending) "Replace replay" else "Add a replay",
                        primary = false,
                        onFiles = {
                            onDismiss()
                            onAddReplay()
                        },
                        onPlayStation = {
                            onDismiss()
                            onOpenPlayStation()
                        },
                    )
                },
            )

            EditRow(
                label = "Tyres",
                value = {
                    val compound = localTyre
                    Text(
                        if (compound == null) "Not set" else compound.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (compound == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    GhostPill(
                        text = if (localTyre == null) "Set" else "Change",
                        onClick = { tyrePickerOpen = true },
                        modifier = Modifier.testTag("edit-tyre-change"),
                    )
                },
            )

            if (tyrePickerOpen) {
                TyrePickerDialog(
                    selected = localTyre,
                    onPick = {
                        assignTyre(it)
                        tyrePickerOpen = false
                    },
                    onClear = {
                        assignTyre(null)
                        tyrePickerOpen = false
                    },
                    onDismiss = { tyrePickerOpen = false },
                )
            }

            EditRow(
                label = "Traction control",
                value = {
                    Text(
                        tcsRowValue(localTcs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (localTcs == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    GhostPill(
                        text = if (localTcs == null) "Set" else "Change",
                        onClick = { tcsPickerOpen = true },
                        modifier = Modifier.testTag("edit-tcs-change"),
                    )
                },
            )

            if (tcsPickerOpen) {
                SetupPickerDialog(
                    title = "Traction control",
                    levels = TCS_LEVELS,
                    selected = localTcs,
                    label = { if (it == 0) "Off" else it.toString() },
                    onPick = {
                        assignTcs(it)
                        tcsPickerOpen = false
                    },
                    onClear = {
                        assignTcs(null)
                        tcsPickerOpen = false
                    },
                    onDismiss = { tcsPickerOpen = false },
                )
            }

            EditRow(
                label = "Brake balance",
                value = {
                    Text(
                        brakeBalanceRowValue(localBrakeBalance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (localBrakeBalance == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    GhostPill(
                        text = if (localBrakeBalance == null) "Set" else "Change",
                        onClick = { brakeBalancePickerOpen = true },
                        modifier = Modifier.testTag("edit-brake-balance-change"),
                    )
                },
            )

            if (brakeBalancePickerOpen) {
                SetupPickerDialog(
                    title = "Brake balance",
                    levels = BRAKE_BALANCE_LEVELS,
                    selected = localBrakeBalance,
                    label = { if (it > 0) "+$it" else it.toString() },
                    onPick = {
                        assignBrakeBalance(it)
                        brakeBalancePickerOpen = false
                    },
                    onClear = {
                        assignBrakeBalance(null)
                        brakeBalancePickerOpen = false
                    },
                    onDismiss = { brakeBalancePickerOpen = false },
                )
            }

            if (developerTools && lap.hasReplay) {
                EditRow(
                    label = "Developer",
                    value = {
                        Text(
                            when (reencodeResult) {
                                true -> "Re-encoded with 0.1 s keyframes."
                                false -> "Re-encode failed. The clip is unchanged."
                                null -> "Rebuilds the clip with a 0.1 s keyframe interval so exact frame seeking is cheap."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (reencodeResult == false) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    actions = {
                        if (reencoding) {
                            Row(
                                modifier = Modifier.testTag("reencode-progress"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("Re-encoding…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            GhostPill(text = "Re-encode clip", onClick = onReencode)
                        }
                    },
                )
            }

            EditRow(
                label = "Data",
                value = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        NumericText(
                            formatBytes(lap.videoBytes + lap.telemetryBytes + lap.pendingBytes) + " total",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            listOfNotNull(
                                lap.videoBytes.takeIf { it > 0 }?.let { "Replay ${formatBytes(it)}" },
                                lap.pendingBytes.takeIf { it > 0 }?.let { "Pending ${formatBytes(it)}" },
                                "Telemetry ${formatBytes(lap.telemetryBytes)}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            PrimaryPill(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("edit-done"),
            )
        }
    }

    removingDriver?.let { target ->
        RemoveDriverDialog(
            profile = target,
            onConfirm = {
                DriverProfileStore(context).remove(target.psnId)
                localProfiles = localProfiles.filterNot { it.psnId.equals(target.psnId, true) }
                if (driverPsnId.equals(target.psnId, true)) assign(null)
                removingDriver = null
            },
            onDismiss = { removingDriver = null },
        )
    }

    if (addingDriver) {
        AddDriverDialog(
            lookupProfile = {
                GridStatsProfileClient { DriverProfileStore(context).apiKey }.fetch(it)
            },
            onSave = {
                addingDriver = false
                assign(it)
            },
            onDismiss = { addingDriver = false },
        )
    }
}

@Composable
private fun RemoveDriverDialog(profile: DriverProfile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${profile.nickname}?") },
        text = {
            Text("Laps keep the PSN ID; only the cached profile is deleted. Refresh profiles later to fetch the rating again.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("edit-driver-remove-confirm"),
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep") }
        },
    )
}

private fun eventSubtitle(event: CachedEvent): String = listOfNotNull(
    event.trackName?.takeIf { it.isNotBlank() },
    event.carClass?.takeIf { it.isNotBlank() },
    if (event.kind == EventKind.Daily) {
        weekStartLabel(event.startMs)
    } else {
        eventRangeLabel(event.startMs, event.endMs)
    },
    event.ownerCarName?.takeIf { it.isNotBlank() },
).distinct().joinToString(" · ")
