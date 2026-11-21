package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.profile.GridStatsProfileClient
import io.github.jlai403.lapcraft.app.profile.countryFlagEmoji
import io.github.jlai403.lapcraft.app.profile.needsRefresh
import io.github.jlai403.lapcraft.app.profile.ratingWithAgeLabel
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.app.catalog.lapStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DriverProfileSection(
    lookupProfile: (suspend (String) -> DriverProfile)? = null,
) {
    val context = LocalContext.current
    val store = remember { DriverProfileStore(context) }
    val settings = remember { io.github.jlai403.lapcraft.app.settings.SettingsStore(context) }
    val sessionStore = remember { lapStore(context) }
    val resolvedLookup: suspend (String) -> DriverProfile =
        lookupProfile ?: { GridStatsProfileClient { store.apiKey }.fetch(it) }
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(store.owner()) }
    var psnId by remember { mutableStateOf(profile?.psnId.orEmpty()) }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedProfiles by remember { mutableStateOf(store.profiles()) }
    var removingProfile by remember { mutableStateOf<DriverProfile?>(null) }
    var pendingProfiles by remember { mutableIntStateOf(staleDriverCount(store, sessionStore)) }
    var refreshingProfiles by remember { mutableStateOf(false) }
    var refreshProgress by remember { mutableStateOf("") }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf(store.apiKey.orEmpty()) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var ratingTarget by remember { mutableStateOf<DriverProfile?>(null) }
    var ratingDr by remember { mutableStateOf("") }
    var ratingSr by remember { mutableStateOf("") }

    fun refreshPending() {
        pendingProfiles = staleDriverCount(store, sessionStore)
        savedProfiles = store.profiles()
    }

    fun fetchPending() {
        if (refreshingProfiles) return
        val targets = staleDriverPsns(store, sessionStore)
        if (targets.isEmpty()) {
            refreshPending()
            return
        }
        refreshingProfiles = true
        refreshError = null
        scope.launch {
            var done = 0
            for (psn in targets) {
                refreshProgress = "${done + 1} of ${targets.size}"
                try {
                    val fetched = resolvedLookup(psn)
                    store.save(fetched)
                    done++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    refreshError = e.message ?: "Profile lookup failed."
                }
            }
            refreshProgress = ""
            refreshingProfiles = false
            refreshPending()
        }
    }

    fun refreshOne(psn: String) {
        if (refreshingProfiles) return
        refreshingProfiles = true
        refreshError = null
        scope.launch {
            try {
                store.save(resolvedLookup(psn))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshError = e.message ?: "Profile lookup failed."
            } finally {
                refreshingProfiles = false
                refreshPending()
            }
        }
    }

    fun fetch() {
        val target = psnId.trim()
        if (target.isEmpty()) {
            error = "Enter a PSN ID."
            return
        }
        if (fetching) return
        fetching = true
        error = null
        scope.launch {
            try {
                val fetched = resolvedLookup(target)
                profile = fetched
                psnId = fetched.psnId
                store.setOwner(fetched)
                savedProfiles = store.profiles()
                refreshPending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Profile lookup failed."
            } finally {
                fetching = false
            }
        }
    }

    SectionCard(title = "Driver profile") {
        Text(
            "Default driver for new laps. Change a lap's driver from the lap screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = psnId,
            onValueChange = {
                psnId = it
                error = null
            },
            label = { Text("PSN ID") },
            placeholder = { Text("Your PlayStation Network ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings-profile-psn"),
            shape = RoundedCornerShape(14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostPill(
                text = when {
                    fetching -> "Fetching…"
                    profile == null -> "Fetch profile"
                    else -> "Refresh profile"
                },
                onClick = { fetch() },
                enabled = !fetching,
                modifier = Modifier.testTag("settings-profile-fetch"),
            )
            if (profile != null) {
                GhostPill(
                    text = "Remove",
                    onClick = {
                        profile?.let { store.remove(it.psnId) }
                        profile = null
                        psnId = ""
                        error = null
                        refreshPending()
                    },
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings-profile-remove"),
                )
            }
        }
        profile?.let { loaded ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                countryFlagEmoji(loaded.countryCode)?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmall)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(loaded.nickname, style = MaterialTheme.typography.titleMedium)
                    Text(
                        loaded.psnId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    loaded.ratingWithAgeLabel()?.let { rating ->
                        Text(
                            rating,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Data from GT GridStats.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val others = savedProfiles.filterNot { it.psnId.equals(profile?.psnId, ignoreCase = true) }
        if (others.isNotEmpty()) {
            SectionLabel("Saved drivers")
            others.forEach { saved ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    countryFlagEmoji(saved.countryCode)?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(saved.nickname, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(saved.psnId, saved.ratingWithAgeLabel()).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GhostPill(
                        text = "Rating",
                        onClick = {
                            ratingTarget = saved
                            ratingDr = saved.dr.orEmpty()
                            ratingSr = saved.sr.orEmpty()
                        },
                        modifier = Modifier.testTag("settings-driver-rating-${saved.psnId.lowercase()}"),
                    )
                    if (saved.needsRefresh(System.currentTimeMillis(), !store.apiKey.isNullOrBlank())) {
                        GhostPill(
                            text = "Refresh",
                            onClick = { refreshOne(saved.psnId) },
                            enabled = !refreshingProfiles,
                            modifier = Modifier.testTag("settings-driver-refresh-${saved.psnId.lowercase()}"),
                        )
                    }
                    GhostPill(
                        text = "Remove",
                        onClick = { removingProfile = saved },
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("settings-driver-remove-${saved.psnId.lowercase()}"),
                    )
                }
            }
        }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (pendingProfiles > 0 || refreshingProfiles) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostPill(
                    text = if (refreshingProfiles) {
                        if (refreshProgress.isBlank()) "Refreshing…" else "Refreshing $refreshProgress…"
                    } else {
                        "Refresh profiles ($pendingProfiles)"
                    },
                    onClick = { fetchPending() },
                    enabled = !refreshingProfiles,
                    modifier = Modifier.testTag("settings-profile-fetch-missing"),
                )
                refreshError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                "Refresh fetches missing profiles and ratings older than a week from GT GridStats.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionLabel("GT GridStats API key (optional)")
        Text(
            "Enables DR/SR and car names. The key stays on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                keyStatus = null
            },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("settings-gridstats-key"),
            shape = RoundedCornerShape(14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostPill(
                text = "Save key",
                onClick = {
                    store.apiKey = apiKey
                    keyStatus = if (apiKey.isBlank()) "Cleared" else "Saved"
                },
                modifier = Modifier.testTag("settings-gridstats-key-save"),
            )
            if (store.apiKey != null) {
                GhostPill(
                    text = "Clear",
                    onClick = {
                        store.apiKey = null
                        apiKey = ""
                        keyStatus = "Cleared"
                    },
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings-gridstats-key-clear"),
                )
            }
        }
        keyStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Fetching sends only your PSN ID to gt-gridstats.com.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    removingProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { removingProfile = null },
            title = { Text("Remove ${target.nickname}?") },
            text = { Text("Laps keep the PSN ID; only the cached profile is deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.remove(target.psnId)
                        removingProfile = null
                        refreshPending()
                    },
                    modifier = Modifier.testTag("settings-driver-remove-confirm"),
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removingProfile = null }) { Text("Keep") }
            },
        )
    }

    ratingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { ratingTarget = null },
            title = { Text("Set rating for ${target.nickname}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Manual entry for drivers without a GT GridStats API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = ratingDr,
                        onValueChange = { ratingDr = it },
                        label = { Text("DR (A+, A, B, ...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ratingSr,
                        onValueChange = { ratingSr = it },
                        label = { Text("SR (S, A, B, ...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.save(
                        target.copy(
                            dr = ratingDr.trim().uppercase().takeIf { it.isNotEmpty() },
                            sr = ratingSr.trim().uppercase().takeIf { it.isNotEmpty() },
                            fetchedAtMs = null,
                            notFound = false,
                        ),
                    )
                    savedProfiles = store.profiles()
                    ratingTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { ratingTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/** Drivers on saved laps whose cached profile is missing or carries a stale rating. */
private fun staleDriverPsns(store: DriverProfileStore, sessionStore: LapStore): List<String> {
    val expectRating = !store.apiKey.isNullOrBlank()
    val now = System.currentTimeMillis()
    return sessionStore.summaries()
        .mapNotNull { it.driverPsnId }
        .distinct()
        .filter { psn ->
            val cached = store.profile(psn)
            cached == null || cached.needsRefresh(now, expectRating)
        }
}

private fun staleDriverCount(store: DriverProfileStore, sessionStore: LapStore): Int =
    staleDriverPsns(store, sessionStore).size
