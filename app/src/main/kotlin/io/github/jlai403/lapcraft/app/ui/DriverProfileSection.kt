package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.app.catalog.sessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DriverProfileSection(
    lookupProfile: (suspend (String) -> DriverProfile)? = null,
) {
    val context = LocalContext.current
    val store = remember { DriverProfileStore(context) }
    val settings = remember { io.github.jlai403.lapcraft.app.settings.SettingsStore(context) }
    val sessionStore = remember { sessionStore(context) }
    val resolvedLookup: suspend (String) -> DriverProfile =
        lookupProfile ?: { GridStatsProfileClient { store.apiKey }.fetch(it) }
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(store.owner()) }
    var psnId by remember { mutableStateOf(profile?.psnId.orEmpty()) }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var missingCount by remember {
        mutableStateOf(missingProfileCount(store, sessionStore))
    }
    var fetchingMissing by remember { mutableStateOf(false) }
    var missingProgress by remember { mutableStateOf("") }
    var missingError by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf(store.apiKey.orEmpty()) }
    var keyStatus by remember { mutableStateOf<String?>(null) }

    fun refreshMissing() {
        missingCount = sessionStore.summaries()
            .mapNotNull { it.driverPsnId }
            .distinct()
            .count { store.profile(it) == null }
    }

    fun fetchMissing() {
        if (fetchingMissing) return
        val targets = sessionStore.summaries()
            .mapNotNull { it.driverPsnId }
            .distinct()
            .filter { store.profile(it) == null }
        if (targets.isEmpty()) {
            refreshMissing()
            return
        }
        fetchingMissing = true
        missingError = null
        scope.launch {
            var done = 0
            for (psn in targets) {
                missingProgress = "${done + 1} of ${targets.size}"
                try {
                    val fetched = resolvedLookup(psn)
                    store.save(fetched)
                    done++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    missingError = e.message ?: "Profile lookup failed."
                }
            }
            missingProgress = ""
            fetchingMissing = false
            refreshMissing()
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
                    val drSr = listOfNotNull(
                        loaded.dr?.let { "DR $it" },
                        loaded.sr?.let { "SR $it" },
                    ).joinToString(" · ")
                    if (drSr.isNotEmpty()) {
                        Text(
                            drSr,
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
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (missingCount > 0 || fetchingMissing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostPill(
                    text = if (fetchingMissing) {
                        "Fetching $missingProgress…"
                    } else {
                        "Fetch missing profiles ($missingCount)"
                    },
                    onClick = { fetchMissing() },
                    enabled = !fetchingMissing,
                    modifier = Modifier.testTag("settings-profile-fetch-missing"),
                )
                missingError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                "Laps imported without a cached profile need a one-time fetch.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "GT GridStats API key (optional)",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Enables DR/SR and car names. Get one at gt-gridstats.com; stored only on this device.",
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
}

private fun missingProfileCount(store: DriverProfileStore, sessionStore: LapStore): Int =
    sessionStore.summaries()
        .mapNotNull { it.driverPsnId }
        .distinct()
        .count { store.profile(it) == null }
