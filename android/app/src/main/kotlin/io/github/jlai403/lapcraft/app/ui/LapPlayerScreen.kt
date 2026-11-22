package io.github.jlai403.lapcraft.app.ui

import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import io.github.jlai403.lapcraft.app.replay.LapReencoder
import io.github.jlai403.lapcraft.app.replay.PlayStationApp
import io.github.jlai403.lapcraft.app.replay.VideoTranscoder
import io.github.jlai403.lapcraft.app.replay.probeClip
import io.github.jlai403.lapcraft.app.replay.readFrameTimes
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.profile.countryFlagEmoji
import io.github.jlai403.lapcraft.app.share.Sharing
import io.github.jlai403.lapcraft.app.events.EventCache
import io.github.jlai403.lapcraft.app.events.EventRefresh
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.track.TrackCatalog
import io.github.jlai403.lapcraft.app.track.TrackNamer
import io.github.jlai403.lapcraft.R
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.clip.FrameTimeline
import io.github.jlai403.lapcraft.core.session.LapSummary

import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapRecord
import io.github.jlai403.lapcraft.core.session.toLapView
import io.github.jlai403.lapcraft.core.session.lapClock
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.brakeBalanceLabel
import io.github.jlai403.lapcraft.core.session.compareCandidates
import io.github.jlai403.lapcraft.core.session.tcsLabel
import io.github.jlai403.lapcraft.app.catalog.CarCatalog
import io.github.jlai403.lapcraft.app.catalog.lapStore
import io.github.jlai403.lapcraft.core.session.trackKey
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
@Composable
fun LapPlayerScreen(
    lapId: LapId,
    onBack: () -> Unit,
    onImportSync: (String?) -> Unit,
    onCompare: (LapId, LapId) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { lapStore(context) }
    val settings = remember { SettingsStore(context) }
    val profileStore = remember { DriverProfileStore(context) }
    val namer = remember { TrackNamer(store, TrackCatalog(context)) }
    fun openPlayStationApp() {
        if (!PlayStationApp.open(context)) {
            Toast.makeText(context, "PlayStation App is not available.", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportSync(uri.toString())
    }

    var sessionReload by remember { mutableIntStateOf(0) }
    var cachedEvents by remember { mutableStateOf(EventCache(context).events()) }
    LaunchedEffect(Unit) {
        val cache = EventCache(context)
        if (cache.isPolyphonyStale() || cache.isGridstatsStale()) {
            runCatching { EventRefresh.refresh(context) }
            cachedEvents = EventCache(context).events()
        }
    }
    val session by produceState<LapRecord?>(null, lapId, sessionReload) {
        value = withContext(Dispatchers.IO) { store.load(lapId) }
    }
    val lap = session?.toLapView()
    val samples by produceState<List<Sample>>(emptyList(), lapId) {
        value = withContext(Dispatchers.IO) {
            runCatching { store.readSamples(lapId) }.getOrDefault(emptyList())
        }
    }
    val summary by produceState<LapSummary?>(null, lapId, sessionReload) {
        value = withContext(Dispatchers.IO) {
            store.summaries().firstOrNull { it.lapId == lapId }
        }
    }
    val ghost by produceState<LoadedLap?>(null, lapId, sessionReload) {
        value = withContext(Dispatchers.IO) {
            val laps = store.summaries()
            val current = laps.firstOrNull { it.lapId == lapId } ?: return@withContext null
            val best = laps
                .filter {
                    it.trackKey == current.trackKey && it.lapId != lapId
                }
                .minByOrNull { it.lapTimeMs } ?: return@withContext null
            val bestLap = store.load(best.lapId) ?: return@withContext null
            val bestSamples = runCatching {
                store.readSamples(best.lapId)
            }.getOrDefault(emptyList())
            if (bestSamples.isEmpty()) null else LoadedLap(bestLap.toLapView(), bestSamples)
        }
    }

    val compareCandidates by produceState<List<LapSummary>>(
        emptyList(),
        lapId,
        sessionReload,
    ) {
        value = withContext(Dispatchers.IO) {
            val laps = store.summaries()
            val current = laps.firstOrNull { it.lapId == lapId } ?: return@withContext emptyList()
            laps.compareCandidates(current).sortedBy { it.lapTimeMs }
        }
    }
    val candidateDrivers = remember(compareCandidates, sessionReload) {
        compareCandidates.mapNotNull { it.driverPsnId }.distinct()
            .associateWith { profileStore.profile(it) }
    }

    val lapDir = store.lapDir(lapId)
    val videoFile = File(lapDir, "replay.mp4").takeIf { it.isFile }
    val hasClip = videoFile != null
    val player = remember(lapId) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }
    val videoVersion = remember(videoFile?.absolutePath, sessionReload) {
        videoFile?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var clockMs by remember(lapId) { mutableLongStateOf(0L) }
    var renderedUs by remember { mutableLongStateOf(-1L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var frameStepMs by remember { mutableLongStateOf(if (hasClip) 33L else 16L) }
    var frameDurationUs by remember { mutableLongStateOf(if (hasClip) 33_000L else 16_667L) }
    var frameTimeline by remember { mutableStateOf<FrameTimeline?>(null) }
    var scrubBaseUs by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var reencoding by remember { mutableStateOf(false) }
    var reencodeResult by remember { mutableStateOf<Boolean?>(null) }
    var candidates by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var headerExpanded by remember { mutableStateOf(false) }
    var showGhost by remember { mutableStateOf(true) }
    var followMap by remember { mutableStateOf(true) }
    var looping by remember { mutableStateOf(false) }
    var compareOpen by remember { mutableStateOf(false) }

    fun applySeek(targetMs: Long) {
        val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(clamped)
        positionMs = clamped
        renderedUs = -1L
    }

    val scrubSeeker = remember(player) {
        ScrubSeeker(
            scope = scope,
            exactWaitMs = SCRUB_EXACT_WAIT_MS,
        ) { target -> applySeek(target) }
    }

    fun seekClip(targetMs: Long) {
        if (hasClip) {
            applySeek(targetMs)
            player.pause()
        } else {
            clockMs = targetMs.coerceIn(0L, (lap?.lapTimeMs ?: 0).toLong())
            playing = false
        }
        scrubTargetMs = null
        scrubSeeker.clearPending()
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(playing, hasClip, playbackSpeed, looping, lap?.lapSpanMs) {
        if (hasClip || !playing) return@LaunchedEffect
        val active = lap ?: return@LaunchedEffect
        val end = active.lapSpanMs.toLong()
        var last = withFrameNanos { it } / 1_000_000L
        while (playing) {
            val now = withFrameNanos { it } / 1_000_000L
            val delta = now - last
            last = now
            if (delta > 0) {
                clockMs += (delta * playbackSpeed).toLong()
                if (clockMs >= end) {
                    if (looping) {
                        clockMs = 0L
                    } else {
                        clockMs = end
                        playing = false
                    }
                }
            }
        }
    }
    LaunchedEffect(looping) {
        player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun shareVideo() {
        val file = videoFile
        if (file == null) {
            message = "No video yet"
        } else {
            Sharing.shareFile(context, file, "video/mp4", "Share lap")
        }
    }

    fun exportCsv() {
        val active = lap ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Sharing.exportCsv(context, store, lapId)
                }
            }.onSuccess {
                Sharing.shareFile(context, it, "text/csv", "Export lap")
            }.onFailure {
                message = "CSV export failed: ${it.message}"
            }
        }
    }

    fun openEdit() {
        editing = true
        candidates = null
        reencodeResult = null
        if (session?.trackLabel == TrackNamer.UNKNOWN) {
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    runCatching { namer.candidates(lapId) }.getOrDefault(emptyList())
                }
                if (editing) candidates = found
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                positionMs = newPosition.positionMs
            }

            override fun onPlayerError(error: PlaybackException) {
                message = "Playback failed: ${error.message}"
            }
        }
        player.addListener(listener)
        player.setVideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
            renderedUs = presentationTimeUs
            scrubSeeker.onFrameRendered()
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(videoVersion, lap?.leadInMs) {
        val active = lap ?: return@LaunchedEffect
        val file = videoFile ?: return@LaunchedEffect
        renderedUs = -1L
        scrubTargetMs = null
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        durationMs = probe.durationMs
        frameStepMs = probe.frameMs
        frameDurationUs = probe.frameDurationUs
        frameTimeline = withContext(Dispatchers.IO) { readFrameTimes(file) }
        val start = active.lapClock().clipMs(0)
        val ready = if (probe.durationMs > 0) {
            start.coerceIn(0L, probe.durationMs)
        } else {
            start
        }
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.seekTo(ready)
            player.prepare()
            player.playWhenReady = false
            positionMs = ready
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.duration > 0) durationMs = maxOf(durationMs, player.duration)
            if (player.isPlaying) positionMs = player.currentPosition
            delay(50)
        }
    }

    fun seekLap(targetMs: Long) {
        val active = lap ?: return
        val clamped = targetMs.coerceIn(0L, active.lapSpanMs.toLong())
        if (hasClip) {
            seekClip(active.lapClock().clipMs(clamped.toInt()))
        } else {
            seekClip(clamped)
        }
    }

    val displayedUs = scrubTargetMs?.times(1_000)
        ?: if (hasClip && renderedUs >= 0) renderedUs else (if (hasClip) positionMs else clockMs) * 1_000
    val displayedMs = scrubTargetMs
        ?: if (hasClip && renderedUs >= 0) renderedUs / 1_000 else (if (hasClip) positionMs else clockMs)
    val cursorMs = lap?.let {
        if (hasClip) it.lapClock().telemetryMs(displayedMs) else displayedMs.toInt()
    } ?: 0
    // GT7's own timer, for the readout: the clip covers the lap's real span, not its 60ths.
    val cursorLapMs = lap?.let { it.lapClock().lapMs(displayedMs) } ?: 0

    fun stepFrames(frames: Int) {
        val target = frameStepMs(displayedUs, frames, frameDurationUs, frameTimeline)
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
        scrubTargetMs = target
        scrubSeeker.submit(target)
    }

    LaunchedEffect(scrubbing, renderedUs) {
        val target = scrubTargetMs ?: return@LaunchedEffect
        if (scrubbing) return@LaunchedEffect
        if (renderedUs >= 0 && abs(renderedUs / 1_000 - target) <= frameStepMs * 2) {
            scrubTargetMs = null
        }
    }

    LaunchedEffect(scrubbing) {
        if (scrubbing) return@LaunchedEffect
        val target = scrubTargetMs ?: return@LaunchedEffect
        delay(1500)
        if (scrubTargetMs == target) scrubTargetMs = null
    }

    fun scrubTarget(baseUs: Long, frames: Int): Long? {
        val active = lap ?: return null
        if (!hasClip) {
            val target = frameStepMs(baseUs, frames, frameDurationUs, null)
            return target.coerceIn(0L, active.lapSpanMs.toLong())
        }
        val clock = active.lapClock()
        val startMs = clock.clipMs(0).coerceAtLeast(0)
        val endMs = clock.endClipMs.coerceAtMost(durationMs.coerceAtLeast(0))
        return frameStepMs(baseUs, frames, frameDurationUs, frameTimeline)
            .coerceIn(startMs, endMs.coerceAtLeast(startMs))
    }

    val driverProfile = remember(lap?.driverPsnId, sessionReload) {
        lap?.driverPsnId?.let { profileStore.profile(it) }
    }
    val driverLabel = when {
        driverProfile != null -> listOfNotNull(
            countryFlagEmoji(driverProfile.countryCode),
            driverProfile.nickname,
        ).joinToString(" ")
        lap?.driverPsnId != null -> lap.driverPsnId
        else -> profileStore.owner()?.let {
            listOfNotNull(countryFlagEmoji(it.countryCode), it.nickname).joinToString(" ")
        }
    }
    val subtitle = listOfNotNull(session?.trackLabel, driverLabel).joinToString(" · ").ifBlank { null }
    val currentStats = remember(samples, lap) {
        lap?.let { lapStats(samples.lapWindow(it), it) }
    }
    val ghostStats = remember(ghost) {
        ghost?.let { lapStats(it.samples.lapWindow(it.lap), it.lap) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            title = lap?.let { lapTimeLabel(it.lapTimeMs) }.orEmpty(),
            subtitle = null,
            onBack = onBack,
            onTitleClick = { headerExpanded = !headerExpanded },
            titleExpanded = headerExpanded,
            titleBadge = {
                lap?.tyre?.let { TyreBadge(compound = it, size = 22.dp) }
            },
            actions = {
                GhostPill(
                    text = "Compare",
                    onClick = { compareOpen = true },
                    enabled = compareCandidates.isNotEmpty(),
                    modifier = Modifier.testTag("lap-compare"),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share video") },
                            enabled = videoFile != null,
                            onClick = {
                                menuOpen = false
                                shareVideo()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share screenshot") },
                            onClick = {
                                menuOpen = false
                                shareScreenshot(context, "Share lap") { message = it }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                menuOpen = false
                                exportCsv()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Edit lap") },
                            onClick = {
                                menuOpen = false
                                openEdit()
                            },
                        )
                    }
                }
            },
        )

        if (headerExpanded && lap != null) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    listOfNotNull(
                        subtitle,
                        CarCatalog(context).name(session?.carCode ?: 0) ?: session?.carName,
                        session?.event?.label,
                        lap?.tyre?.label,
                        tcsLabel(lap?.tcs),
                        brakeBalanceLabel(lap?.brakeBalance),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                currentStats?.let {
                    Text(lapStatsLine(it, settings.imperialUnits), style = MaterialTheme.typography.bodySmall)
                    Text(
                        lapStatsComparison(it, ghostStats, settings.imperialUnits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (lap == null) {
            Text(
                "Loading lap…",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val currentLap = lap

        val events = remember(samples, currentLap) {
            lapEvents(samples.lapWindow(currentLap), currentLap)
        }
        val brakeTimes = remember(events) {
            events.filter { it.type == LapEventType.Brake }.map { it.timeMs }
        }
        fun seekBrake(direction: Int) {
            val target = if (direction > 0) {
                brakeTimes.firstOrNull { it > cursorMs + 200 }
            } else {
                brakeTimes.lastOrNull { it < cursorMs - 200 }
            }
            if (target != null) seekLap(target.toLong())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasClip) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            (LayoutInflater.from(viewContext)
                                .inflate(R.layout.view_lap_player, null) as PlayerView).apply {
                                this.player = player
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                    if (settings.telemetryHud) {
                        TelemetryHud(
                            window = samples,
                            lap = currentLap,
                            cursorMs = cursorMs,
                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        )
                    }
                }
                if (File(store.lapDir(lapId), "source.mp4").isFile) {
                    Text(
                        "Newer clip waiting to be synced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = "Telemetry") {
                ChartColumn(
                    samples = samples,
                    lap = currentLap,
                    cursorMs = cursorMs,
                    imperial = settings.imperialUnits,
                    windowMs = settings.chartWindowSeconds * 2_000,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Track map",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FilterChip(
                        selected = followMap,
                        onClick = { followMap = !followMap },
                        label = { Text("Follow") },
                    )
                    FilterChip(
                        selected = showGhost && ghost != null,
                        onClick = { showGhost = !showGhost },
                        enabled = ghost != null,
                        label = { Text("Ghost") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { followMap = false },
                        label = { Text("Reset") },
                    )
                }
                TrackMapLegend()
                TrackMap(
                    samples = samples,
                    lap = currentLap,
                    cursorMs = cursorMs,
                    ghost = ghost,
                    showGhost = showGhost,
                    follow = followMap,
                    view = remember { MapViewState() },
                )
            }

            if (!hasClip) {
                SectionCard(title = "Replay") {
                    if (File(store.lapDir(lapId), "source.mp4").isFile) {
                        Text(
                            "Clip imported. Sync not finished.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        PrimaryPill(
                            text = "Resume sync",
                            onClick = { onImportSync(null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("No replay attached", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Telemetry plays without one. Record the single-lap replay on the " +
                                "PS5, upload it to the PS App, then add it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReplaySourcePill(
                            text = "Add replay",
                            primary = true,
                            onFiles = { importLauncher.launch(arrayOf("video/*")) },
                            onPlayStation = { openPlayStationApp() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.padding(bottom = 8.dp))
        }

        PlaybackFooter(
            cursorMs = cursorLapMs,
            endMs = currentLap.lapTimeMs,
            frameStepMs = frameStepMs,
            enabled = if (hasClip) durationMs > 0 else true,
            playing = playing,
            looping = looping,
            speed = playbackSpeed,
            events = events,
            hapticsEnabled = settings.hapticsEnabled,
            frameSteps = hasClip,
            onScrubStart = {
                scrubBaseUs = displayedUs
                scrubTargetMs = null
                scrubbing = true
                playing = false
                player.pause()
            },
            onScrub = { frames ->
                scrubTarget(scrubBaseUs, frames)?.let { target ->
                    scrubTargetMs = target
                    if (hasClip) scrubSeeker.submit(target)
                }
            },
            onScrubEnd = {
                scrubbing = false
                scrubTargetMs?.let { seekClip(it) }
            },
            onSeekFraction = { fraction ->
                seekLap((fraction * currentLap.lapSpanMs).roundToLong())
            },
            onRewind = { seekBrake(-1) },
            onPlayPause = {
                if (hasClip) {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (cursorMs >= currentLap.lapSpanMs) seekLap(0)
                        player.play()
                    }
                } else {
                    if (playing) {
                        playing = false
                    } else {
                        if (clockMs >= currentLap.lapSpanMs) clockMs = 0L
                        playing = true
                    }
                }
            },
            onForward = { seekBrake(1) },
            onToggleLoop = { looping = !looping },
            onSpeedChange = { playbackSpeed = it },
            onStep = { stepFrames(it) },
        )
    }

    if (editing) {
        summary?.let { sheetLap ->
            EditLapSheet(
                lap = sheetLap,
                driver = sheetLap.driverPsnId?.let { profileStore.profile(it) },
                owner = profileStore.owner(),
                profiles = profileStore.profiles(),
                candidates = candidates,
                onDismiss = {
                    editing = false
                    reencodeResult = null
                },
                onSaveTrack = { name ->
                    scope.launch {
                        val renamed = withContext(Dispatchers.IO) {
                            runCatching { store.renameTrack(lapId, name) }.getOrNull()
                        }
                        if (renamed == null) message = "Track name could not be saved."
                        sessionReload++
                    }
                },
                onAssignDriver = { profile ->
                    scope.launch {
                        val updated = withContext(Dispatchers.IO) {
                            runCatching {
                                store.setDriver(lapId, profile?.psnId)
                            }.getOrNull()
                        }
                        if (updated == null) {
                            message = "Driver could not be saved."
                        } else {
                            profile?.let { profileStore.save(it) }
                            sessionReload++
                        }
                    }
                },
                onAddReplay = {
                    editing = false
                    importLauncher.launch(arrayOf("video/*"))
                },
                onOpenPlayStation = {
                    editing = false
                    openPlayStationApp()
                },
                onResumeSync = {
                    editing = false
                    onImportSync(null)
                },
                developerTools = settings.developerTools,
                reencoding = reencoding,
                reencodeResult = reencodeResult,
                onReencode = {
                    if (!reencoding) {
                        reencoding = true
                        reencodeResult = null
                        scope.launch {
                            val reencoded = runCatching {
                                LapReencoder(store, VideoTranscoder(context))
                                    .reencode(lapId)
                            }.getOrDefault(false)
                            reencoding = false
                            reencodeResult = reencoded
                            sessionReload++
                        }
                    }
                },
                events = cachedEvents,
                onAssignEvent = { tag ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { store.setEvent(lapId, tag) }
                        }
                        sessionReload++
                    }
                },
                tyre = sheetLap.tyre,
                onAssignTyre = { compound ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { store.setTyre(lapId, compound) }
                        }
                        sessionReload++
                    }
                },
                tcs = sheetLap.tcs,
                onAssignTcs = { level ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { store.setTcs(lapId, level) }
                        }
                        sessionReload++
                    }
                },
                brakeBalance = sheetLap.brakeBalance,
                onAssignBrakeBalance = { level ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { store.setBrakeBalance(lapId, level) }
                        }
                        sessionReload++
                    }
                },
            )
        }
    }

    if (compareOpen) {
        AlertDialog(
            onDismissRequest = { compareOpen = false },
            title = { Text("Compare with") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    compareCandidates.forEach { candidate ->
                        val profile = candidate.driverPsnId?.let { candidateDrivers[it] }
                        val driver = profile?.let {
                            listOfNotNull(countryFlagEmoji(it.countryCode), it.nickname)
                                .joinToString(" ")
                        } ?: candidate.driverPsnId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    compareOpen = false
                                    onCompare(
                                        lapId,
                                        candidate.lapId,
                                    )
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NumericText(
                                    lapTimeLabel(candidate.lapTimeMs),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    shortDateLabel(candidate.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                listOfNotNull(driver, candidate.trackLabel).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { compareOpen = false }) { Text("Cancel") }
            },
        )
    }
}

