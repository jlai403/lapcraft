package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.session.LapSort
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapSummary
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.app.catalog.lapStore
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.profile.countryFlagEmoji
import io.github.jlai403.lapcraft.core.session.applySort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImportPickScreen(
    uri: String,
    onPick: (LapId) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { lapStore(context) }
    val profileStore = remember { DriverProfileStore(context) }
    var laps by remember { mutableStateOf<List<LapSummary>?>(null) }
    var profiles by remember { mutableStateOf<Map<String, DriverProfile>>(emptyMap()) }

    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) {
            store.summaries().applySort(LapSort.Newest)
        }
        laps = loaded
        val psns = loaded.mapNotNull { it.driverPsnId }.distinct()
        if (psns.isNotEmpty()) {
            val map = withContext(Dispatchers.IO) {
                psns.mapNotNull { psn -> profileStore.profile(psn)?.let { psn to it } }.toMap()
            }
            profiles = map
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Choose a lap",
            subtitle = "Replay received · pick the lap it belongs to",
            actions = { GhostPill("Close", onClick = onClose) },
        )

        val data = laps
        when {
            data == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            data.isEmpty() -> SectionCard {
                Text("No laps yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Capture a lap first, then attach this replay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GhostPill("Close", onClick = onClose)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(data) { lap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            NumericText(
                                lapTimeLabel(lap.lapTimeMs),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                listOfNotNull(
                                    lap.trackLabel,
                                    lap.carName,
                                    lap.driverPsnId?.let { psn ->
                                        val profile = profiles[psn]
                                        if (profile != null) {
                                            listOfNotNull(countryFlagEmoji(profile.countryCode), profile.nickname)
                                                .joinToString(" ").ifBlank { psn }
                                        } else {
                                            psn
                                        }
                                    },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatDate(lap.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                when {
                                    lap.hasReplay -> "Replaces current replay"
                                    lap.replayPending -> "Replaces the pending import"
                                    else -> "No replay"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PrimaryPill(
                            text = if (lap.hasReplay || lap.replayPending) "Replace" else "Attach",
                            onClick = { onPick(lap.lapId) },
                        )
                    }
                }
            }
        }
    }
}
