package io.github.jlai403.lapcraft.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.jlai403.lapcraft.app.replay.ClipTrimmer
import io.github.jlai403.lapcraft.app.replay.ReplayStore
import io.github.jlai403.lapcraft.app.replay.VideoTranscoder
import io.github.jlai403.lapcraft.app.replay.probeClip
import io.github.jlai403.lapcraft.app.replay.readFrameTimes
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.settings.estimateReplayBytes
import io.github.jlai403.lapcraft.app.settings.estimatedOriginalBytes
import io.github.jlai403.lapcraft.app.settings.replayProfile
import io.github.jlai403.lapcraft.core.clip.LapClock
import io.github.jlai403.lapcraft.core.clip.FrameTimeline
import io.github.jlai403.lapcraft.core.clip.SyncCheck
import io.github.jlai403.lapcraft.core.clip.SyncOffset
import io.github.jlai403.lapcraft.core.clip.SyncValidator
import io.github.jlai403.lapcraft.core.clip.TrackProgress
import io.github.jlai403.lapcraft.core.clip.TrimPlan
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapView
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.toLapView
import io.github.jlai403.lapcraft.app.catalog.lapStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
@Composable
fun ImportSyncScreen(
    lapId: LapId,
    sourceUri: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { lapStore(context) }
    val trimmer = remember { ClipTrimmer() }
    val transcoder = remember { VideoTranscoder(context) }
    val settings = remember { SettingsStore(context) }
    var replayPreset by remember { mutableStateOf(settings.replayPreset) }
    var replayKeyframes by remember { mutableStateOf(settings.replayKeyframes) }
    var replayAudio by remember { mutableStateOf(settings.replayAudio) }
    var replayResolution by remember { mutableStateOf(settings.replayResolution) }
    var replayCodec by remember { mutableStateOf(settings.replayCodec) }
    var qualityExpanded by remember { mutableStateOf(false) }
    val effectiveProfile = remember(
        replayPreset,
        replayKeyframes,
        replayAudio,
        replayResolution,
        replayCodec,
    ) {
        replayProfile(replayPreset, replayKeyframes, replayAudio, replayResolution, replayCodec)
    }
    val hapticsEnabled = settings.hapticsEnabled
    val player = remember(lapId) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    var lap by remember { mutableStateOf<LapView?>(null) }
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var sourceFile by remember { mutableStateOf<File?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var frameStepMs by remember { mutableLongStateOf(33L) }
    var frameDurationUs by remember { mutableLongStateOf(33_000L) }
    var frameTimeline by remember { mutableStateOf<FrameTimeline?>(null) }
    var scrubBaseUs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var renderedUs by remember { mutableLongStateOf(-1L) }
    var lapStartMs by remember { mutableStateOf<Long?>(null) }
    var offsetText by remember(sourceUri) { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var hadClip by remember { mutableStateOf(false) }
    var lapStartSuggested by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var tipsOpen by remember { mutableStateOf(false) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    var scrubbing by remember { mutableStateOf(false) }

    lateinit var scrubSeeker: ScrubSeeker
    fun seekTo(targetMs: Long, fromScrub: Boolean = false) {
        val clamped = targetMs.coerceIn(0, durationMs.coerceAtLeast(0))
        player.seekTo(clamped)
        positionMs = clamped
        renderedUs = -1
        player.pause()
        previewing = false
        if (!fromScrub) {
            scrubTargetMs = null
            scrubSeeker.clearPending()
        }
    }
    scrubSeeker = remember(player) {
        ScrubSeeker(scope) { target -> seekTo(target, fromScrub = true) }
    }

    BackHandler { confirmCancel = true }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                error = "Playback failed: ${playbackError.message}"
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

    LaunchedEffect(lapId, sourceUri) {
        error = null
        val record = withContext(Dispatchers.IO) { store.load(lapId) }
        if (record == null) {
            error = "Lap not found."
            return@LaunchedEffect
        }
        val loaded = record.toLapView()
        val dir = store.lapDir(lapId)
        hadClip = File(dir, "replay.mp4").isFile
        lap = loaded
        samples = withContext(Dispatchers.IO) {
            runCatching { store.readSamples(lapId) }.getOrDefault(emptyList())
        }

        val file = if (sourceUri != null) {
            val destination = ReplayStore.sourceFile(dir, 1)
            val copied = withContext(Dispatchers.IO) {
                ReplayStore.copy(context, Uri.parse(sourceUri), destination)
            }
            if (!copied) {
                error = "Could not read the replay. Download it in the PS App and pick the file instead."
                return@LaunchedEffect
            }
            withContext(Dispatchers.IO) { store.setSource(lapId, "source.mp4") }
            destination
        } else {
            val sf = File(dir, "source.mp4")
            sf.takeIf { it.isFile }
        }

        if (file == null || !file.isFile) {
            error = "No replay file attached."
            return@LaunchedEffect
        }
        sourceFile = file
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        val floor = if (probe.durationMs <= 0L) loaded.lapTimeMs + loaded.leadInMs.toLong() else 0L
        durationMs = maxOf(probe.durationMs, floor)
        frameStepMs = probe.frameMs
        frameDurationUs = probe.frameDurationUs
        frameTimeline = withContext(Dispatchers.IO) { readFrameTimes(file) }
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        }
    }

    LaunchedEffect(player, lap) {
        if (lap == null || started) return@LaunchedEffect
        var reported = 0L
        while (reported <= 0) {
            reported = withContext(Dispatchers.Main) { player.duration }
            if (reported <= 0) delay(50)
        }
        durationMs = maxOf(durationMs, reported)
        lap?.let { l ->
            if (durationMs <= 0L) durationMs = maxOf(durationMs, l.lapTimeMs + l.leadInMs.toLong())
        }
        val lapTime = (lap?.lapTimeMs ?: 0).toLong()
        val suggested = (durationMs - lapTime).coerceIn(0L, durationMs.coerceAtLeast(0L))
        val step = frameStepMs.coerceAtLeast(1L)
        val snapped = (suggested / step) * step
        lapStartMs = snapped
        lapStartSuggested = true
        withContext(Dispatchers.Main) { seekTo(snapped) }
        started = true
    }

    LaunchedEffect(player) {
        while (true) {
            withContext(Dispatchers.Main) {
                if (player.duration > 0) durationMs = maxOf(durationMs, player.duration)
                if (player.isPlaying) positionMs = player.currentPosition
            }
            delay(50)
        }
    }

    LaunchedEffect(previewing) {
        val range = trimRange(lap, lapStartMs, if (offsetText.isBlank()) 0L else SyncOffset.parse(offsetText), durationMs, frameTimeline)
        if (!previewing || range == null) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.Main) {
            player.seekTo(range.startMs)
            player.play()
        }
        while (previewing) {
            withContext(Dispatchers.Main) {
                if (player.currentPosition >= range.endMs) player.seekTo(range.startMs)
            }
            delay(50)
        }
    }
    LaunchedEffect(player, playbackSpeed) {
        withContext(Dispatchers.Main) { player.setPlaybackSpeed(playbackSpeed) }
    }

    val currentLap = lap
    val lapStart = lapStartMs
    val displayedUs = scrubTargetMs?.times(1_000)
        ?: if (renderedUs >= 0) renderedUs else positionMs * 1_000
    val displayedMs = scrubTargetMs
        ?: if (renderedUs >= 0) renderedUs / 1_000 else positionMs

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

    fun stepFrames(frames: Int) {
        val target = frameStepMs(displayedUs, frames, frameDurationUs, frameTimeline)
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
        scrubTargetMs = target
        scrubSeeker.submit(target)
    }
    val parsedOffset = SyncOffset.parse(offsetText)
    val offsetValid = offsetText.isBlank() || parsedOffset != null
    val offsetMs = parsedOffset ?: 0L
    val resolvedStart = if (lapStart != null && offsetValid) lapStart - offsetMs else null
    val sync = if (currentLap != null && resolvedStart != null) {
        LapClock.forLapStart(resolvedStart, currentLap.lapTimeMs)
    } else {
        null
    }
    val lapWindow = currentLap?.let { samples.lapWindow(it) } ?: emptyList()
    val events = remember(lapWindow, currentLap) {
        currentLap?.let { lapEvents(lapWindow, it) } ?: emptyList()
    }
    val brakeTimes = remember(events) {
        events.filter { it.type == LapEventType.Brake }.map { it.timeMs }
    }
    fun seekBrake(direction: Int) {
        val s = sync ?: return
        val target = if (direction > 0) {
            brakeTimes.firstOrNull { it > (s.telemetryMs(displayedMs)) + 200 }
        } else {
            brakeTimes.lastOrNull { it < (s.telemetryMs(displayedMs)) - 200 }
        }
        if (target != null) seekTo(s.clipMs(target))
    }
    val rulerEvents = remember(events, sync) {
        sync?.let { s -> events.map { LapEvent(it.type, s.clipMs(it.timeMs).toInt()) } } ?: events
    }
    val range = sync?.let { TrimPlan.forSync(it, durationMs) }
    val estimateMs = range?.let { (it.endMs - it.startMs).coerceAtLeast(1) }
        ?: currentLap?.lapTimeMs?.toLong()
        ?: 0L
    val estimatedBytes = estimateReplayBytes(effectiveProfile, estimateMs)
    val originalBytes = sourceFile?.length()?.let {
        estimatedOriginalBytes(it, estimateMs, durationMs)
    } ?: 0L
    val savedPercent = if (estimatedBytes > 0 && estimatedBytes < originalBytes) {
        (100 - estimatedBytes * 100 / originalBytes).toInt()
    } else {
        0
    }
    val endTrackPercent = currentLap?.let { TrackProgress.percentAt(samples, it.startNanos, it.lapTimeMs) }
    val check = sync?.let { SyncValidator.check(it, durationMs, endTrackPercent = endTrackPercent ?: 0f) }
    val telemetryMs = sync?.telemetryMs(displayedMs)
    val trackPercent = if (currentLap != null && telemetryMs != null) {
        TrackProgress.percentAt(samples, currentLap.startNanos, telemetryMs)
    } else {
        null
    }
    val nearestSpeed = if (currentLap != null && telemetryMs != null) {
        val target = currentLap.startNanos + telemetryMs * 1_000_000L
        samples.minByOrNull { abs(it.timeNanos - target) }?.speedMps
    } else {
        null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Sync clip",
            subtitle = currentLap?.let { "Lap ${it.index} · ${lapTimeLabel(it.lapTimeMs)}" } ?: "Replay",
            onBack = { confirmCancel = true },
        )
        val outerPlayer = player
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    setPlayer(outerPlayer)
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            buildString {
                if (trackPercent != null) append("%.1f%% lap".format(trackPercent))
                if (nearestSpeed != null) {
                    if (trackPercent != null) append(" · ")
                    append("%.0f km/h".format(nearestSpeed * 3.6f))
                }
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The lap start is the first frame of the lap. The replay and telemetry both start there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (durationMs > 0) {
            PlaybackFooter(
                cursorMs = displayedMs.toInt(),
                endMs = durationMs.toInt(),
                frameStepMs = frameStepMs,
                enabled = durationMs > 0,
                playing = playing,
                looping = previewing,
                speed = playbackSpeed,
                events = rulerEvents,
                hapticsEnabled = hapticsEnabled,
                clockLabel = "Clip",
                lapMs = currentLap?.lapTimeMs,
                frameNumber = frameTimeline?.indexAt(displayedUs)?.plus(1),
                frameTotal = frameTimeline?.size,
                markersMs = range?.let { listOf(it.startMs, it.endMs) } ?: emptyList(),
                onScrubStart = {
                    scrubBaseUs = displayedUs
                    scrubTargetMs = null
                    scrubbing = true
                },
                onScrub = { frames ->
                    val target = frameStepMs(scrubBaseUs, frames, frameDurationUs, frameTimeline)
                        .coerceIn(0, durationMs)
                    scrubTargetMs = target
                    scrubSeeker.submit(target)
                },
                onScrubEnd = { scrubbing = false },
                onSeekFraction = { fraction ->
                    seekTo((fraction * durationMs).roundToLong())
                },
                onRewind = { seekBrake(-1) },
                onPlayPause = {
                    if (player.isPlaying) {
                        player.pause()
                        positionMs = player.currentPosition
                    } else {
                        previewing = false
                        player.play()
                    }
                },
                onForward = { seekBrake(1) },
                onToggleLoop = { previewing = !previewing },
                onSpeedChange = { playbackSpeed = it },
                onStep = { frames -> stepFrames(frames) },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .testTag("sync-scroll")
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            currentLap?.let { activeLap ->
                SectionCard(title = "1 · Lap start") {
                    Text(
                        "The first frame of the lap",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The clip starts on this frame and runs for one lap (${lapTimeLabel(activeLap.lapTimeMs)}). The telemetry is cut to the same window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MetricLine("Lap start", lapStartMs?.let(::formatMillis) ?: "Not set")
                    if (lapStartSuggested) {
                        Text(
                            "The app picked this frame one lap back from the clip end. Check the HUD timer has just restarted, or set the lap start again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = { offsetText = it },
                        label = { Text("Timer on this frame (optional)") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                when {
                                    offsetText.isBlank() ->
                                        "Leave it empty when the HUD shows a lap time or the fastest-lap flash."
                                    parseTimer(offsetText) == null ->
                                        "Type seconds.milliseconds, e.g. 0.016."
                                    else ->
                                        "The frame lands a few milliseconds after the line. Type the timer to make up the difference."
                                },
                            )
                        },
                        isError = offsetText.isNotBlank() && parseTimer(offsetText) == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    lapStartMs?.let { lapStart ->
                        val start = lapStart - (parseTimer(offsetText) ?: 0L)
                        Text(
                            "Starts on the line at ${formatMillis(start)} · ends ${formatMillis(start + activeLap.lapTimeMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { tipsOpen = !tipsOpen }) {
                        Text(if (tipsOpen) "Hide tips" else "How to find the lap start")
                    }
                    if (tipsOpen) {
                        SyncTip("The first frame past the line is the lap start.")
                        SyncTip("If the timer is counting up (0:00.xxx), type it and the start lands on the line.")
                        SyncTip("If the HUD shows a lap time or the fastest-lap flash, leave it empty.")
                        SyncTip("Record a few seconds past the finish so the whole lap fits after the start.")
                    }
                    Text(
                        "Video and telemetry can be up to one frame apart (${frameGapLabel(frameDurationUs)} ms on this clip). You may notice it when you step frames. Normal playback looks the same.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryPill(
                        text = "Use this frame",
                        onClick = {
                            lapStartMs = displayedMs
                            lapStartSuggested = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = durationMs > 0,
                    )
                }
            }
            SectionCard(title = "2 · Finish frame") {
                if (sync == null) {
                    Text(
                        "Set the lap start to see the clip range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MetricLine("Lap start", formatMillis(sync.trimInMs))
                    MetricLine("Finish frame", formatMillis(sync.trimOutMs))
                    MetricLine("Lap", lapTimeLabel(sync.lapTimeMs))
                    range?.let { MetricLine("Finish marker", formatMillis(it.endMs)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostPill(flipLabel(displayedMs, sync.trimInMs, sync.trimOutMs), {
                            seekTo(flipTargetMs(displayedMs, sync.trimInMs, sync.trimOutMs))
                        }, Modifier.weight(1f))
                    }
                    Text(
                        "The end of the clip lands back on the line, one lap after the lap start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val result = check) {
                        is SyncCheck.ClipTooShort -> Text(
                            "The clip is %.1f s short of a full lap from this frame. Record past the finish line."
                                .format(result.missingMs / 1_000.0),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        is SyncCheck.EndNotAtLine -> Text(
                            "The clip's end lands %.1f%% around the lap, not on the line. Move the lap start."
                                .format(result.trackPercent),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        else -> Text(
                            "Check that the finish frame shows the line, then save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionCard(title = "Replay quality") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when {
                                estimateMs <= 0 -> "—"
                                !effectiveProfile.transcode -> "No re-encode"
                                else -> "≈ ${formatBytes(estimatedBytes)}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                estimateMs <= 0 -> "Set a lap start to estimate the stored size."
                                !effectiveProfile.transcode -> "Stored as-is at the original size."
                                savedPercent > 0 ->
                                    "about $savedPercent% smaller than the ≈ ${formatBytes(originalBytes)} clip"
                                else -> "Estimated stored size."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GhostPill(
                        text = if (qualityExpanded) "Hide" else "Change",
                        onClick = { qualityExpanded = !qualityExpanded },
                    )
                }
                if (qualityExpanded) {
                    ReplayCompressionControls(
                        preset = replayPreset,
                        keyframes = replayKeyframes,
                        audio = replayAudio,
                        resolution = replayResolution,
                        codec = replayCodec,
                        highlightChanges = false,
                        onPresetChange = { replayPreset = it },
                        onResolutionChange = { replayResolution = it },
                        onCodecChange = { replayCodec = it },
                        onKeyframesChange = { replayKeyframes = it },
                        onAudioChange = { replayAudio = it },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = Hairline)
                if (hadClip) {
                    Text(
                        "Saving replaces the current replay. Discard keeps it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostPill("Discard", { confirmCancel = true }, Modifier.weight(1f))
                    PrimaryPill(
                        text = when {
                            !saving -> "Save clip"
                            effectiveProfile.transcode -> "Compacting…"
                            else -> "Saving…"
                        },
                        onClick = {
                            val trimRange = range
                            val source = sourceFile
                            if (trimRange != null && source != null && !saving) {
                                saving = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val output = File(
                                            store.lapDir(lapId),
                                            "replay.mp4",
                                        )
                                        val trimmed = trimmer.trim(source, output, trimRange)
                                            ?: return@withContext null
                                        val profile = effectiveProfile
                                        var compactFailed = false
                                        if (profile.transcode) {
                                            val compact = File(output.absolutePath + ".compact.mp4")
                                            if (transcoder.transcode(output, compact, profile)) {
                                                if (output.delete() && compact.renameTo(output)) {
                                                } else {
                                                    compact.delete()
                                                    compactFailed = true
                                                }
                                            } else {
                                                compactFailed = true
                                            }
                                        }
                                        store.attachClip(
                                            lapId,
                                            trimmed.startOffsetMs,
                                        )
                                        trimmed to compactFailed
                                    }
                                    saving = false
                                    if (result == null) {
                                        error = if (hadClip) {
                                            "Trim failed. The current replay is unchanged; try again."
                                        } else {
                                            "Trim failed. No replay was attached; try again."
                                        }
                                    } else {
                                        if (result.second) {
                                            Toast.makeText(
                                                context,
                                                "Compaction failed. The clip stays uncompressed.",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        onDone()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = check == SyncCheck.Ok && offsetValid && !saving,
                    )
                }
            }
        }
    }


    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Discard this clip?") },
            text = {
                Text(
                    if (hadClip) {
                        "The copied clip is deleted. The current replay stays attached."
                    } else {
                        "The copied clip is deleted. The telemetry lap stays."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            store.setSource(lapId, null)
                            sourceFile?.delete()
                        }
                        onCancel()
                    }
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep") }
            },
        )
    }
}


@Composable
private fun SyncTip(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
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

private fun trimRange(lap: LapView?, lapStartMs: Long?, offsetMs: Long?, durationMs: Long, frames: FrameTimeline? = null) =
    if (lap != null && lapStartMs != null && offsetMs != null && offsetMs >= 0) {
        TrimPlan.forSync(LapClock.forLapStart(lapStartMs - offsetMs, lap.lapTimeMs), durationMs, frames)
    } else {
        null
    }
