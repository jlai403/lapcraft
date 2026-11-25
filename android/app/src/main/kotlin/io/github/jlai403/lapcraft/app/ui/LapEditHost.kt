package io.github.jlai403.lapcraft.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.jlai403.lapcraft.app.catalog.CarCatalog
import io.github.jlai403.lapcraft.app.catalog.lapStore
import io.github.jlai403.lapcraft.app.events.EventCache
import io.github.jlai403.lapcraft.app.events.EventRefresh
import io.github.jlai403.lapcraft.app.events.EventTagger
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.track.TrackCatalog
import io.github.jlai403.lapcraft.app.track.TrackNamer
import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventSuggestion
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LapSummary
import io.github.jlai403.lapcraft.core.tire.TyreCompound
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LapEditHost(
    lapId: LapId,
    onDismiss: () -> Unit,
    onAddReplay: (LapId) -> Unit = {},
    onOpenPlayStation: () -> Unit = {},
    onResumeSync: (LapId) -> Unit = {},
    developerTools: Boolean = false,
    onMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { lapStore(context) }
    val profileStore = remember { DriverProfileStore(context) }
    val namer = remember { TrackNamer(store, TrackCatalog(context)) }
    val tagger = remember { EventTagger(context) }

    var summary by remember { mutableStateOf<LapSummary?>(null) }
    var candidates by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var cachedEvents by remember { mutableStateOf(EventCache(context).events()) }
    var suggestions by remember { mutableStateOf<List<EventSuggestion>>(emptyList()) }
    var reencoding by remember { mutableStateOf(false) }
    var reencodeResult by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(lapId) {
        summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
        cachedEvents = withContext(Dispatchers.IO) { EventCache(context).events() }
        val s = summary ?: return@LaunchedEffect
        if (s.trackLabel == TrackNamer.UNKNOWN) {
            candidates = withContext(Dispatchers.IO) {
                runCatching { namer.candidates(lapId) }.getOrDefault(emptyList())
            }
        }
        suggestions = withContext(Dispatchers.IO) {
            runCatching {
                val trackForMatch = TrackCatalog(context).officialName(s.trackId) ?: s.trackLabel
                tagger.suggestions(s.carCode, trackForMatch, parseInstant(s.createdAt), s.lapCount, s.tyre)
            }.getOrDefault(emptyList())
        }
        val cache = EventCache(context)
        if (cache.isPolyphonyStale() || cache.isGridstatsStale()) {
            runCatching { EventRefresh.refresh(context) }
            cachedEvents = withContext(Dispatchers.IO) { EventCache(context).events() }
        }
    }

    val current = summary ?: return
    val owner = profileStore.owner()
    val profiles = profileStore.profiles()

    EditLapSheet(
        lap = current,
        driver = current.driverPsnId?.let { profileStore.profile(it) },
        owner = owner,
        profiles = profiles,
        candidates = candidates,
        onDismiss = onDismiss,
        onSaveTrack = { name ->
            scope.launch {
                val renamed = withContext(Dispatchers.IO) { runCatching { store.renameTrack(lapId, name) }.getOrNull() }
                if (renamed == null) onMessage("Track name could not be saved.")
                summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
            }
        },
        onAssignDriver = { profile ->
            scope.launch {
                val updated = withContext(Dispatchers.IO) { runCatching { store.setDriver(lapId, profile?.psnId) }.getOrNull() }
                if (updated == null) onMessage("Driver could not be saved.")
                else {
                    profile?.let { profileStore.save(it) }
                    summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
                }
            }
        },
        onAddReplay = { onDismiss(); onAddReplay(lapId) },
        onOpenPlayStation = { onOpenPlayStation() },
        onResumeSync = { onDismiss(); onResumeSync(lapId) },
        developerTools = developerTools,
        reencoding = reencoding,
        reencodeResult = reencodeResult,
        onReencode = {
            if (!reencoding) {
                reencoding = true
                reencodeResult = null
                scope.launch {
                    val reencoded = runCatching {
                        io.github.jlai403.lapcraft.app.replay.LapReencoder(store, io.github.jlai403.lapcraft.app.replay.VideoTranscoder(context)).reencode(lapId)
                    }.getOrDefault(false)
                    reencoding = false
                    reencodeResult = reencoded
                    summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
                }
            }
        },
        onRemoveReplay = {
            scope.launch {
                val removed = withContext(Dispatchers.IO) { store.removeReplay(lapId) }
                if (removed) onMessage("Replay removed.") else onMessage("Replay could not be removed.")
                summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
            }
        },
        events = cachedEvents,
        eventSuggestions = suggestions,
        onAssignEvent = { tag ->
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { store.setEvent(lapId, tag) } }
                summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
            }
        },
        tyre = current.tyre,
        onAssignTyre = { compound ->
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { store.setTyre(lapId, compound) } }
                summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
            }
        },
        tcs = current.tcs,
        onAssignTcs = { level ->
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { store.setTcs(lapId, level) } }
                summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
            }
        },
        brakeBalance = current.brakeBalance,
        onAssignBrakeBalance = { level ->
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { store.setBrakeBalance(lapId, level) } }
                summary = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.lapId == lapId } }
            }
        },
    )
}

private fun parseInstant(value: String): Long =
    runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
