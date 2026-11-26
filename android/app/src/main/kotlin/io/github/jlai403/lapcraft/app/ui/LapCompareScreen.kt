package io.github.jlai403.lapcraft.app.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import io.github.jlai403.lapcraft.R
import io.github.jlai403.lapcraft.app.replay.probeClip
import io.github.jlai403.lapcraft.app.replay.readFrameTimes
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.clip.TrackProgress
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.lapClock
import io.github.jlai403.lapcraft.core.clip.FrameTimeline
import io.github.jlai403.lapcraft.core.session.LapView
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.toLapView
import io.github.jlai403.lapcraft.app.catalog.lapStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

internal val COMPARE_MAP_PANE_WIDTH = 320.dp

private data class CompareLap(
    val lapId: LapId,
    val trackLabel: String,
    val lap: LapView,
    val samples: List<Sample>,
    val clip: File?,
)

@OptIn(UnstableApi::class)
@Composable
fun LapCompareScreen(
    primaryLapId: LapId,
    referenceLapId: LapId,
    onBack: () -> Unit,
    onAddClip: (LapId) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { lapStore(context) }
    val settings = remember { SettingsStore(context) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val primary by produceState<CompareLap?>(null, primaryLapId) {
        value = loadLap(store, primaryLapId)
    }
    val reference by produceState<CompareLap?>(null, referenceLapId) {
        value = loadLap(store, referenceLapId)
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val primaryPlayer = remember(primary?.clip?.path) {
        primary?.clip?.let { newPlayer(context) }
    }
    val referencePlayer = remember(reference?.clip?.path) {
        reference?.clip?.let { newPlayer(context).apply { volume = 0f } }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var renderedUs by remember { mutableLongStateOf(-1L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var frameStepMs by remember { mutableLongStateOf(33L) }
    var frameDurationUs by remember { mutableLongStateOf(33_000L) }
    var frameTimeline by remember { mutableStateOf<FrameTimeline?>(null) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubBaseUs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
    var referenceDurationMs by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val mapView = remember { MapViewState() }

    fun seekPrimary(targetMs: Long) {
        val player = primaryPlayer ?: return
        val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(clamped)
        positionMs = clamped
        renderedUs = -1L
    }

    val primarySeeker = remember(primaryPlayer) {
        ScrubSeeker(scope, exactWaitMs = SCRUB_EXACT_WAIT_MS) { seekPrimary(it) }
    }
    val referenceSeeker = remember(referencePlayer) {
        ScrubSeeker(scope, exactWaitMs = SCRUB_EXACT_WAIT_MS) { target ->
            referencePlayer?.seekTo(
                target.coerceIn(0L, referenceDurationMs.coerceAtLeast(0L)),
            )
        }
    }

    DisposableEffect(primaryPlayer) {
        val player = primaryPlayer ?: return@DisposableEffect onDispose {}
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
            primarySeeker.onFrameRendered()
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(referencePlayer) {
        val player = referencePlayer ?: return@DisposableEffect onDispose {}
        player.setVideoFrameMetadataListener { _, _, _, _ ->
            referenceSeeker.onFrameRendered()
        }
        onDispose { player.release() }
    }

    val primaryVersion = remember(primary?.clip?.absolutePath) {
        primary?.clip?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
    }
    val referenceVersion = remember(reference?.clip?.absolutePath) {
        reference?.clip?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
    }

    LaunchedEffect(primaryVersion, primaryPlayer) {
        val player = primaryPlayer ?: return@LaunchedEffect
        val loaded = primary ?: return@LaunchedEffect
        val file = loaded.clip ?: return@LaunchedEffect
        renderedUs = -1L
        scrubTargetMs = null
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        durationMs = maxOf(probe.durationMs, loaded.lap.lapSpanMs + loaded.lap.leadInMs.toLong())
        frameStepMs = probe.frameMs
        frameDurationUs = probe.frameDurationUs
        frameTimeline = withContext(Dispatchers.IO) { readFrameTimes(file) }
        val start = loaded.lap.lapClock().clipMs(0)
            .coerceIn(0L, durationMs)
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.seekTo(start)
            positionMs = start
            player.prepare()
            player.playWhenReady = false
        }
    }

    LaunchedEffect(referenceVersion, referencePlayer) {
        val player = referencePlayer ?: return@LaunchedEffect
        val loaded = reference ?: return@LaunchedEffect
        val file = loaded.clip ?: return@LaunchedEffect
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        referenceDurationMs =
            maxOf(probe.durationMs, loaded.lap.lapSpanMs + loaded.lap.leadInMs.toLong())
        val start = loaded.lap.lapClock().clipMs(0)
            .coerceIn(0L, referenceDurationMs)
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.seekTo(start)
            player.prepare()
        }
    }

    LaunchedEffect(primaryPlayer) {
        val player = primaryPlayer ?: return@LaunchedEffect
        while (true) {
            if (player.duration > 0) durationMs = maxOf(durationMs, player.duration)
            positionMs = player.currentPosition
            delay(50)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            withFrameNanos { primaryPlayer?.let { positionMs = it.currentPosition } }
        }
    }

    LaunchedEffect(primaryPlayer, playbackSpeed) {
        primaryPlayer?.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(primaryPlayer, looping) {
        primaryPlayer?.repeatMode =
            if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(landscape) {
        if (!landscape) primaryPlayer?.pause()
    }

    val primaryLap = primary?.lap
    val framesLive = tab == 0
    val displayedUs = displayedFrameUs(framesLive, scrubTargetMs, renderedUs, positionMs)
    val displayedMs = displayedUs / 1_000
    val primaryCursorMs = primaryLap?.let {
        it.lapClock().telemetryMs(displayedMs)
    } ?: 0
    val primaryLapCursorMs = primaryLap?.let { it.lapClock().lapMs(displayedMs) } ?: 0

    val primaryWindow = remember(primary) {
        primary?.let { it.samples.lapWindow(it.lap) } ?: emptyList()
    }
    val referenceWindow = remember(reference) {
        reference?.let { it.samples.lapWindow(it.lap) } ?: emptyList()
    }
    val primaryTable = remember(primaryWindow, primary) {
        primary?.let { TrackProgress.table(primaryWindow, it.lap.startNanos) }
    }
    val referenceTable = remember(referenceWindow, reference) {
        reference?.let { TrackProgress.table(referenceWindow, it.lap.startNanos) }
    }
    val alignment = remember(primaryWindow, referenceWindow, reference) {
        reference?.let { TrackProgress.align(primaryWindow, referenceWindow, it.lap.startNanos) }
    }
    val referenceCursorMs = referenceCursor(
        primaryTable = primaryTable,
        primaryCursorMs = primaryCursorMs,
        referenceTable = referenceTable,
        referenceLapTimeMs = reference?.lap?.lapSpanMs ?: 0,
        alignment = alignment,
    )
    val referenceClipMs = reference?.let { loaded ->
        loaded.lap.lapClock().clipMs(referenceCursorMs.toInt())
            .coerceIn(0L, referenceDurationMs.coerceAtLeast(0L))
    } ?: 0L

    LaunchedEffect(referenceSeeker, referenceClipMs, tab) {
        if (tab != 0) return@LaunchedEffect
        if (referencePlayer == null || primaryPlayer == null) return@LaunchedEffect
        referenceSeeker.submit(referenceClipMs)
    }

    val events = remember(primaryWindow, primaryLap) {
        primaryLap?.let { lapEvents(primaryWindow, it) } ?: emptyList()
    }
    val brakeTimes = remember(events) {
        events.filter { it.type == LapEventType.Brake }.map { it.timeMs }
    }
    val deltaMs = if (primaryTable != null && referenceTable != null) {
        primaryCursorMs - referenceCursorMs
    } else {
        null
    }

    fun seekLap(targetMs: Long) {
        val lap = primaryLap ?: return
        val clamped = targetMs.coerceIn(0L, lap.lapSpanMs.toLong())
        seekPrimary(lap.lapClock().clipMs(clamped.toInt()))
        primaryPlayer?.pause()
        scrubTargetMs = null
        primarySeeker.clearPending()
    }

    fun scrubTarget(baseUs: Long, frames: Int): Long? {
        val lap = primaryLap ?: return null
        val clock = lap.lapClock()
        val startMs = clock.clipMs(0).coerceAtLeast(0)
        val endMs = clock.endClipMs.coerceAtMost(durationMs.coerceAtLeast(0))
        return frameStepMs(baseUs, frames, frameDurationUs, frameTimeline)
            .coerceIn(startMs, endMs.coerceAtLeast(startMs))
    }

    fun stepFrames(frames: Int) {
        val target = frameStepMs(displayedUs, frames, frameDurationUs, frameTimeline)
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
        scrubTargetMs = target
        primarySeeker.submit(target)
    }

    fun seekBrake(direction: Int) {
        val target = if (direction > 0) {
            brakeTimes.firstOrNull { it > primaryCursorMs + 200 }
        } else {
            brakeTimes.lastOrNull { it < primaryCursorMs - 200 }
        }
        if (target != null) seekLap(target.toLong())
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompareChrome(
                deltaMs = deltaMs,
                tab = tab,
                onTabChange = { tab = it },
                onBack = onBack,
            )
            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val active = primary
            if (active == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Loading lap…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (tab == 0) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComparePane(
                        lap = primary,
                        player = primaryPlayer,
                        title = "Lap ${active.lap.index}",
                        tag = "primary",
                        cursorMs = primaryCursorMs,
                        imperial = settings.imperialUnits,
                        showHud = settings.telemetryHud,
                        captionColor = MaterialTheme.colorScheme.secondary,
                        onAddClip = { onAddClip(primaryLapId) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ComparePane(
                        lap = reference,
                        player = referencePlayer,
                        title = reference?.let { "Ref · Lap ${it.lap.index}" } ?: "Reference",
                        tag = "reference",
                        cursorMs = referenceCursorMs,
                        imperial = settings.imperialUnits,
                        showHud = settings.telemetryHud,
                        captionColor = MaterialTheme.colorScheme.tertiary,
                        onAddClip = { onAddClip(referenceLapId) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    val mapPaneWidth = minOf(COMPARE_MAP_PANE_WIDTH, maxWidth * 0.42f)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(mapPaneWidth)
                                .fillMaxHeight()
                                .testTag("compare-map-pane"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TrackMap(
                                samples = active.samples,
                                lap = active.lap,
                                cursorMs = primaryCursorMs,
                                follow = true,
                                centeredFollow = true,
                                reference = reference?.let { LoadedLap(it.lap, it.samples) },
                                referenceCursorMs = referenceCursorMs,
                                fillHeight = true,
                                view = mapView,
                                modifier = Modifier.weight(1f),
                            )
                            TrackMapLegend(showReference = true, horizontal = true)
                        }
                        CompareCharts(
                            samples = active.samples,
                            lap = active.lap,
                            reference = reference?.let { LoadedLap(it.lap, it.samples) },
                            cursorMs = primaryCursorMs,
                            imperial = settings.imperialUnits,
                            alignment = alignment,
                            referenceCursorMs = referenceCursorMs,
                            windowMs = settings.chartWindowSeconds * 2_000,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("compare-charts"),
                        )
                    }
                }
            }

            if (primaryPlayer != null) {
                PlaybackFooter(
                    cursorMs = primaryLapCursorMs,
                    endMs = primaryLap?.lapTimeMs ?: 0,
                    frameStepMs = frameStepMs,
                    enabled = durationMs > 0,
                    playing = playing,
                    looping = looping,
                    speed = playbackSpeed,
                    events = events,
                    hapticsEnabled = settings.hapticsEnabled,
                    compact = true,
                    onScrubStart = {
                        scrubBaseUs = displayedUs
                        scrubTargetMs = null
                        scrubbing = true
                        primaryPlayer.pause()
                    },
                    onScrub = { frames ->
                        scrubTarget(scrubBaseUs, frames)?.let { target ->
                            scrubTargetMs = target
                            primarySeeker.submit(target)
                        }
                    },
                    onScrubEnd = {
                        scrubbing = false
                        scrubTargetMs?.let { target ->
                            seekPrimary(target)
                            primaryPlayer.pause()
                        }
                    },
                    onSeekFraction = { fraction ->
                        seekLap((fraction * (primaryLap?.lapSpanMs ?: 0)).roundToLong())
                    },
                    onRewind = { seekBrake(-1) },
                    rewindEnabled = brakeTimes.any { it < primaryCursorMs - 200 },
                    forwardEnabled = brakeTimes.any { it > primaryCursorMs + 200 },
                    onPlayPause = {
                        if (primaryPlayer.isPlaying) {
                            primaryPlayer.pause()
                        } else {
                            if (primaryCursorMs >= (primaryLap?.lapSpanMs ?: 0)) seekLap(0)
                            primaryPlayer.play()
                        }
                    },
                    onForward = { seekBrake(1) },
                    onToggleLoop = { looping = !looping },
                    onSpeedChange = { playbackSpeed = it },
                    onStep = { stepFrames(it) },
                )
            }
        }

        if (!landscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("compare-rotate"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Rotate to landscape", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Compare needs a landscape screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompareChrome(
    deltaMs: Int?,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("compare-back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = tab == 0,
                onClick = { onTabChange(0) },
                label = { Text("Video", maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = tab == 1,
                onClick = { onTabChange(1) },
                label = { Text("Telemetry", maxLines = 1) },
                modifier = Modifier.testTag("compare-tab-telemetry"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        Text(
            deltaMs?.let { d ->
                if (d == 0) "Δ 0.000 s" else "%s Δ %+.3f s".format(
                    if (d < 0) "▲" else "▼",
                    d / 1000.0,
                )
            } ?: "Δ —",
            modifier = Modifier.testTag("compare-delta").padding(end = 8.dp),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                deltaMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
                deltaMs <= 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            },
        )
    }
}

@Composable
private fun ComparePane(
    lap: CompareLap?,
    player: ExoPlayer?,
    title: String,
    tag: String,
    cursorMs: Int,
    imperial: Boolean,
    showHud: Boolean,
    captionColor: Color,
    onAddClip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = captionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            lap?.let { loaded ->
                NumericText(
                    lapTimeLabel(loaded.lap.lapTimeMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = captionColor,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .then(
                    lap?.let { loaded ->
                        Modifier
                            .testTag("compare-cursor-$tag")
                            .semantics {
                                contentDescription =
                                    "$title replay, ${lapTimeLabel(loaded.lap.lapTimeMs)}"
                                stateDescription = formatMillis(cursorMs.toLong())
                            }
                    } ?: Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                player != null && lap?.clip != null -> AndroidView(
                    factory = { viewContext ->
                        (LayoutInflater.from(viewContext)
                            .inflate(R.layout.view_lap_player, null) as PlayerView).apply {
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize().testTag("compare-video"),
                )
                lap != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (lap.samples.isEmpty()) "No track data" else "No clip yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryPill(
                        text = "Add clip",
                        onClick = onAddClip,
                        modifier = Modifier.testTag("compare-add-clip-$tag"),
                    )
                }
                else -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            lap?.let { loaded ->
                if (showHud) {
                    TelemetryHud(
                        window = loaded.samples,
                        lap = loaded.lap,
                        cursorMs = cursorMs,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    )
                }
            }
        }
        lap?.let {
            CompareReadoutRow(
                samples = it.samples,
                lap = it.lap,
                cursorMs = cursorMs,
                imperial = imperial,
            )
        }
    }
}

internal fun referenceCursor(
    primaryTable: TrackProgress.Table?,
    primaryCursorMs: Int,
    referenceTable: TrackProgress.Table?,
    referenceLapTimeMs: Int,
    alignment: TrackProgress.Alignment? = null,
): Int {
    val percent = primaryTable?.percentAt(primaryCursorMs) ?: 0f
    return alignment?.referenceTimeAt(percent, referenceLapTimeMs)
        ?: referenceTable?.timeAtPercent(percent, referenceLapTimeMs)
        ?: 0
}

internal fun displayedFrameUs(
    framesLive: Boolean,
    scrubTargetMs: Long?,
    renderedUs: Long,
    positionMs: Long,
): Long = scrubTargetMs?.times(1_000)
    ?: if (framesLive && renderedUs >= 0) renderedUs else positionMs * 1_000

private fun deltaLabel(deltaMs: Int): String =
    if (deltaMs == 0) "Δ 0.000 s" else "Δ %+.3f s".format(deltaMs / 1000.0)

@OptIn(UnstableApi::class)
private fun newPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setSeekParameters(SeekParameters.EXACT)
    }

private suspend fun loadLap(
    store: LapStore,
    lapId: LapId,
): CompareLap? = withContext(Dispatchers.IO) {
    val record = store.load(lapId) ?: return@withContext null
    val samples = runCatching { store.readSamples(lapId) }
        .getOrDefault(emptyList())
    val dir = store.lapDir(lapId)
    val clip = File(dir, "replay.mp4").takeIf { it.isFile }
    CompareLap(lapId, record.trackLabel, record.toLapView(), samples, clip)
}
