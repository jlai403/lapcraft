package io.github.jlai403.lapcraft.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.updater.UpdateManager

@Composable
fun App(
    capture: CaptureUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    sharedVideoUri: String?,
    onSharedVideoHandled: () -> Unit,
    update: UpdateManager.State,
    onCheckUpdate: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore(context) }
    var currentTyre by remember { mutableStateOf(settings.currentTyre) }
    var currentTcs by remember { mutableStateOf(settings.currentTcs) }
    var currentBrakeBalance by remember { mutableStateOf(settings.currentBrakeBalance) }
    val activeTab = when (screen) {
        Screen.Library -> 0
        Screen.Record -> 1
        Screen.Settings -> 2
        else -> null
    }

    LaunchedEffect(sharedVideoUri) {
        val uri = sharedVideoUri ?: return@LaunchedEffect
        screen = Screen.ImportPick(uri)
        onSharedVideoHandled()
    }

    BackHandler(enabled = screen != Screen.Library && screen !is Screen.ImportSync) {
        val current = screen
        screen = when (current) {
            is Screen.Compare -> Screen.Player(current.primaryLapId)
            else -> Screen.Library
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (val current = screen) {
                        Screen.Library -> LibraryScreen(
                            onOpenLap = { lapId -> screen = Screen.Player(lapId) },
                            onOpenRecord = { screen = Screen.Record },
                            onOpenSync = { lapId, uri ->
                                screen = Screen.ImportSync(lapId, uri, returnToLibrary = true)
                            },
                            onOpenSettings = { screen = Screen.Settings },
                            onCompare = { primaryLapId, referenceLapId ->
                                screen = Screen.Compare(primaryLapId, referenceLapId)
                            },
                        )

                        is Screen.ImportPick -> ImportPickScreen(
                            uri = current.uri,
                            onPick = { lapId ->
                                screen = Screen.ImportSync(lapId, current.uri)
                            },
                            onClose = { screen = Screen.Library },
                        )

                        is Screen.ImportSync -> ImportSyncScreen(
                            lapId = current.lapId,
                            sourceUri = current.sourceUri,
                            onDone = {
                                screen = current.returnToCompare ?: if (current.returnToLibrary) {
                                    Screen.Library
                                } else {
                                    Screen.Player(current.lapId)
                                }
                            },
                            onCancel = {
                                screen = current.returnToCompare ?: Screen.Library
                            },
                        )

                        is Screen.Player -> LapPlayerScreen(
                            lapId = current.lapId,
                            onBack = { screen = Screen.Library },
                            onImportSync = {
                                screen = Screen.ImportSync(current.lapId, it)
                            },
                            onCompare = { primaryLapId, referenceLapId ->
                                screen = Screen.Compare(primaryLapId, referenceLapId)
                            },
                        )

                        is Screen.Compare -> LapCompareScreen(
                            primaryLapId = current.primaryLapId,
                            referenceLapId = current.referenceLapId,
                            onBack = {
                                screen = Screen.Player(current.primaryLapId)
                            },
                            onAddClip = { lapId ->
                                screen = Screen.ImportSync(lapId, null, returnToCompare = current)
                            },
                        )

                        Screen.Record -> RecordScreen(
                            state = capture,
                            imperial = settings.imperialUnits,
                            tyre = currentTyre,
                            onTyreChange = {
                                currentTyre = it
                                settings.currentTyre = it
                            },
                            tcs = currentTcs,
                            onTcsChange = {
                                currentTcs = it
                                settings.currentTcs = it
                            },
                            brakeBalance = currentBrakeBalance,
                            onBrakeBalanceChange = {
                                currentBrakeBalance = it
                                settings.currentBrakeBalance = it
                            },
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                            onCapture = onCapture,
                            onCancelCapture = onCancelCapture,
                            onOpenLap = { lapId -> screen = Screen.Player(lapId) },
                            onOpenSettings = { screen = Screen.Settings },
                        )

                        Screen.Settings -> SettingsScreen(
                            update = update,
                            onCheckUpdate = onCheckUpdate,
                        )
                    }
                }
                UpdatePrompt(update = update)
                if (activeTab != null) {
                    AppBottomBar(selected = activeTab) { tab ->
                        screen = when (tab) {
                            0 -> Screen.Library
                            1 -> Screen.Record
                            else -> Screen.Settings
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePrompt(update: UpdateManager.State) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val available = update as? UpdateManager.State.Available ?: return
    if (dismissed) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Update available") },
        text = {
            Text(
                buildString {
                    append(
                        "Version ${available.manifest.versionName} " +
                            "(build ${available.manifest.versionCode}) is available.",
                    )
                    if (available.manifest.notes.isNotBlank()) append(" ${available.manifest.notes}")
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismissed = true
                    UpdateManager.openReleasePage(context)
                },
            ) { Text("Open releases") }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text("Later") }
        },
    )
}

@Composable
private fun AppBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Library") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            label = { Text("Record") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") },
            colors = colors,
        )
    }
}
