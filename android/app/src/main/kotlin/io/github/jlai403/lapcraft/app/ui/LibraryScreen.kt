package io.github.jlai403.lapcraft.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.app.profile.GridStatsProfileClient
import io.github.jlai403.lapcraft.app.profile.DriverProfileLinks
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.profile.needsRefresh
import io.github.jlai403.lapcraft.app.profile.countryFlagEmoji
import io.github.jlai403.lapcraft.app.replay.LapReencoder
import io.github.jlai403.lapcraft.app.replay.PlayStationApp
import io.github.jlai403.lapcraft.app.replay.VideoTranscoder
import io.github.jlai403.lapcraft.app.seed.SeedLaps
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.share.LibraryTransfer
import io.github.jlai403.lapcraft.app.track.TrackCatalog
import io.github.jlai403.lapcraft.app.track.TrackNamer
import io.github.jlai403.lapcraft.core.events.EventFilter
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventStatus
import io.github.jlai403.lapcraft.core.events.eventGroups
import io.github.jlai403.lapcraft.core.session.LapFilter
import io.github.jlai403.lapcraft.core.session.LapSort
import io.github.jlai403.lapcraft.core.session.LapSummary
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.app.catalog.lapStore
import io.github.jlai403.lapcraft.app.events.EventCache
import io.github.jlai403.lapcraft.app.events.EventRefresh
import io.github.jlai403.lapcraft.core.session.applyFilter
import io.github.jlai403.lapcraft.core.session.applySort
import io.github.jlai403.lapcraft.core.session.canCompareWith
import io.github.jlai403.lapcraft.core.session.fastestPerTrack
import io.github.jlai403.lapcraft.core.session.trackKey
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenLap: (LapId) -> Unit,
    onOpenRecord: () -> Unit = {},
    onOpenSync: (LapId, String?) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onCompare: (LapId, LapId) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { lapStore(context) }
    val settings = remember { SettingsStore(context) }
    val profileStore = remember { DriverProfileStore(context) }
    val namer = remember { TrackNamer(store, TrackCatalog(context)) }
    val owner = remember { profileStore.owner() }
    var profiles by remember {
        mutableStateOf(profileStore.profiles().associateBy { it.psnId.lowercase() })
    }
    var laps by remember { mutableStateOf<List<LapSummary>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(LapFilter()) }
    var sort by remember { mutableStateOf(LapSort.Newest) }
    var pastExpanded by remember { mutableStateOf(false) }
    var carMenu by remember { mutableStateOf(false) }
    var cachedEvents by remember { mutableStateOf(EventCache(context).events()) }
    var previewEmpty by remember { mutableStateOf(settings.previewEmptyLibrary) }
    var editingId by remember { mutableStateOf<LapId?>(null) }
    val editing = editingId?.let { id -> laps?.firstOrNull { it.lapId == id } }
    var candidates by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var deleting by remember { mutableStateOf<LapSummary?>(null) }
    var syncTarget by remember { mutableStateOf<LapSummary?>(null) }
    var reencoding by remember { mutableStateOf(false) }
    var reencodeResult by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var trackMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var compareMode by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<List<LapSummary>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = syncTarget
        syncTarget = null
        if (uri != null && target != null) {
            onOpenSync(LapId(target.lapId.value), uri.toString())
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = "Importing laps…"
            scope.launch {
                runCatching {
                    LibraryTransfer.import(context, uri, context.filesDir) { _, _ -> }
                }
                    .onSuccess {
                        message = importSummary(it)
                        reload++
                    }
                    .onFailure { message = "Import failed: ${it.message ?: "unknown error"}" }
            }
        }
    }

    LaunchedEffect(reload) {
        runCatching {
            SeedLaps.importIfNeeded(context, store, settings, previewEmpty)
        }
        laps = withContext(Dispatchers.IO) { store.summaries() }
        if (settings.trackIdentificationVersion < TrackNamer.VERSION) {
            runCatching { withContext(Dispatchers.IO) { namer.identifyUnknownLaps() } }
            settings.trackIdentificationVersion = TrackNamer.VERSION
            laps = withContext(Dispatchers.IO) { store.summaries() }
        }
        val repairedWindows = withContext(Dispatchers.IO) {
            runCatching { EventRefresh.repairDailyTags(context, laps.orEmpty()) }.getOrDefault(false)
        }
        if (repairedWindows) laps = withContext(Dispatchers.IO) { store.summaries() }
        val cache = EventCache(context)
        if (cache.isPolyphonyStale() || cache.isGridstatsStale()) {
            runCatching { EventRefresh.refresh(context) }
            cachedEvents = EventCache(context).events()
        }
        // TTL-gated DR/SR refresh for stale drivers, only when a key is available.
        val apiKey = profileStore.apiKey
        if (!apiKey.isNullOrBlank()) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val psns = store.summaries().mapNotNull { it.driverPsnId }.distinct()
                        .filter { psn ->
                            val cached = profileStore.profile(psn)
                            cached == null || cached.needsRefresh(now, true)
                        }
                        .take(16)
                    if (psns.isNotEmpty()) {
                        val fetched = GridStatsProfileClient { apiKey }.fetchBatch(psns)
                        val fetchedIds = fetched.map { it.psnId.lowercase() }.toSet()
                        for (p in fetched) profileStore.save(p)
                        for (psn in psns) {
                            if (psn.lowercase() !in fetchedIds) {
                                profileStore.save(
                                    DriverProfile(psnId = psn, nickname = psn, countryCode = null,
                                        fetchedAtMs = now, notFound = true),
                                )
                            }
                        }
                    }
                }
                profiles = withContext(Dispatchers.IO) {
                    profileStore.profiles().associateBy { it.psnId.lowercase() }
                }
            }
        }
        refreshing = false
    }

    fun openLink(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) {
            message = "No app can open that link."
        }
    }

    fun edit(lap: LapSummary) {
        editingId = lap.lapId
        candidates = null
        reencodeResult = null
        if (lap.trackLabel == TrackNamer.UNKNOWN) {
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    runCatching { namer.candidates(lap.lapId) }.getOrDefault(emptyList())
                }
                if (editingId == lap.lapId) candidates = found
            }
        }
    }

    fun saveTrack(lap: LapSummary, name: String) {
        scope.launch {
            val renamed = withContext(Dispatchers.IO) {
                runCatching { store.renameTrack(lap.lapId, name) }.getOrNull()
            }
            if (renamed == null) message = "Track name could not be saved."
            reload++
        }
    }

    fun assignDriver(lap: LapSummary, profile: DriverProfile?) {
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching { store.setDriver(lap.lapId, profile?.psnId) }.getOrNull()
            }
            if (updated == null) {
                message = "Driver could not be saved."
                return@launch
            }
            profile?.let {
                profileStore.save(it)
                profiles = profileStore.profiles().associateBy { saved -> saved.psnId.lowercase() }
            }
            reload++
        }
    }

    fun reencode(lap: LapSummary) {
        if (reencoding) return
        reencoding = true
        reencodeResult = null
        scope.launch {
            val reencoded = runCatching {
                LapReencoder(store, VideoTranscoder(context)).reencode(lap.lapId)
            }.getOrDefault(false)
            reencoding = false
            reencodeResult = reencoded
            reload++
        }
    }

    fun togglePick(lap: LapSummary) {
        if (picks.any { it.lapId == lap.lapId }) {
            picks = picks.filterNot { it.lapId == lap.lapId }
            return
        }
        val primary = picks.firstOrNull()
        if (primary != null && !lap.canCompareWith(primary)) {
            message = primary.event?.let { "Pick another lap in ${it.label}." }
                ?: "Pick another lap on ${primary.trackLabel}."
            return
        }
        picks = if (picks.size >= 2) listOf(picks[0], lap) else picks + lap
        message = null
    }

    val all = laps
    val primaryPick = picks.firstOrNull()
    val visible = all?.applyFilter(filter)?.applySort(sort)?.let { lapsInScope ->
        if (primaryPick == null) lapsInScope else lapsInScope.filter { it.canCompareWith(primaryPick) }
    }
    val referenceCount = visible.orEmpty().count { it.lapId != primaryPick?.lapId }
    val groups = visible?.eventGroups(cachedEvents, System.currentTimeMillis()).orEmpty()
    val tracks = all?.distinctBy { it.trackKey }?.map { it.trackKey to it.trackLabel }?.sortedBy { it.second }.orEmpty()
    val cars = all.orEmpty().map { it.carName }.filter { it.isNotBlank() }.distinct().sorted()
    val rankable = all?.filter { it.trackLabel != TrackNamer.UNKNOWN }
    val fastest = rankable?.fastestPerTrack().orEmpty()
    val trackCounts = rankable?.groupingBy { it.trackKey }?.eachCount().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Library",
            subtitle = summaryLine(all, tracks.map { it.second }, previewEmpty),
            modifier = Modifier.padding(horizontal = 20.dp),
            actions = {
                if (compareMode) {
                    TextButton(
                        onClick = {
                            compareMode = false
                            picks = emptyList()
                        },
                    ) { Text("Cancel") }
                } else {
                    GhostPill(
                        text = "Compare",
                        onClick = { compareMode = true },
                        enabled = (all?.size ?: 0) >= 2,
                        modifier = Modifier.testTag("library-compare"),
                    )
                    DriverChip(
                        profile = owner,
                        menuOpen = profileMenu,
                        onAdd = onOpenSettings,
                        onOpenMenu = { profileMenu = true },
                        onDismissMenu = { profileMenu = false },
                        onGridStats = {
                            profileMenu = false
                            owner?.let { openLink(DriverProfileLinks.gridStats(it.psnId)) }
                        },
                        onGtshRank = {
                            profileMenu = false
                            owner?.let { openLink(DriverProfileLinks.gtshRank(it.psnId)) }
                        },
                        onEdit = {
                            profileMenu = false
                            onOpenSettings()
                        },
                    )
                }
            },
        )

        if (!previewEmpty && !all.isNullOrEmpty()) {
            val kindFilter = (filter.event as? EventFilter.Kind)?.kind
            SegmentedChoice(
                options = listOf("All") + EventKind.entries.map { it.label },
                selectedIndex = when (kindFilter) {
                    null -> 0
                    else -> EventKind.entries.indexOf(kindFilter) + 1
                },
                modifier = Modifier.padding(horizontal = 20.dp),
                onSelect = { index ->
                    filter = if (index == 0) {
                        filter.copy(event = EventFilter.All, eventId = null)
                    } else {
                        filter.copy(event = EventFilter.Kind(EventKind.entries[index - 1]), eventId = null)
                    }
                },
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        GhostPill(
                            text = tracks.firstOrNull { it.first == filter.trackKey }?.second ?: "All tracks",
                            onClick = { trackMenu = true },
                            leadingIcon = FilterIcons.Filter,
                            trailingIcon = Icons.Filled.KeyboardArrowDown,
                        )
                        DropdownMenu(expanded = trackMenu, onDismissRequest = { trackMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All tracks") },
                                onClick = {
                                    filter = filter.copy(trackKey = null)
                                    trackMenu = false
                                },
                            )
                            tracks.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        filter = filter.copy(trackKey = key)
                                        trackMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        GhostPill(
                            text = filter.car ?: "All cars",
                            onClick = { carMenu = true },
                            modifier = Modifier.widthIn(max = 190.dp),
                            leadingIcon = FilterIcons.Filter,
                            trailingIcon = Icons.Filled.KeyboardArrowDown,
                        )
                        DropdownMenu(
                            expanded = carMenu,
                            onDismissRequest = { carMenu = false },
                            modifier = Modifier.heightIn(max = 320.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("All cars") },
                                onClick = {
                                    filter = filter.copy(car = null)
                                    carMenu = false
                                },
                            )
                            cars.forEach { car ->
                                DropdownMenuItem(
                                    text = { Text(car) },
                                    onClick = {
                                        filter = filter.copy(car = car)
                                        carMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                Box {
                    GhostIconPill(
                        icon = FilterIcons.Sort,
                        contentDescription = "Sort: ${sort.label}",
                        onClick = { sortMenu = true },
                        modifier = Modifier.testTag("library-sort"),
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LapSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sort = option
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                if (filter != LapFilter()) {
                    TextButton(onClick = { filter = LapFilter() }) { Text("Clear") }
                }
            }
        }

        message?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (compareMode) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    compareHint(picks, referenceCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryPill(
                    text = "Compare (${picks.size})",
                    enabled = picks.size == 2,
                    onClick = {
                        if (picks.size == 2) {
                            onCompare(
                                picks[0].lapId,
                                picks[1].lapId,
                            )
                        }
                    },
                    modifier = Modifier.testTag("library-compare-go"),
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                reload++
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("lap-list"),
            ) {
                if (previewEmpty) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            PreviewBanner(
                                onTurnOff = {
                                    previewEmpty = false
                                    settings.previewEmptyLibrary = false
                                },
                            )
                        }
                    }
                }
                when {
                    all == null -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    previewEmpty || all.isEmpty() -> item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            EmptyLibrary(
                                onOpenRecord = onOpenRecord,
                                onImport = {
                                    importLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream"),
                                    )
                                },
                            )
                        }
                    }

                    visible.isNullOrEmpty() -> item {
                        val kind = (filter.event as? EventFilter.Kind)?.kind
                        SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                            if (kind != null) {
                                Text(
                                    "No ${kind.label} laps yet",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (!settings.eventTags) {
                                    Text(
                                        "Event tags are off. Turn them on in Settings to group " +
                                            "laps by event.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    GhostPill("Open Settings", onClick = onOpenSettings)
                                } else {
                                    Text(
                                        "Tag a lap with a ${kind.label} event in Edit lap, or " +
                                            "capture another lap.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                GhostPill(
                                    "Show all laps",
                                    onClick = { filter = filter.copy(event = EventFilter.All, eventId = null) },
                                )
                            } else {
                                Text("No laps match this filter", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Clear the filter or capture another lap.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                GhostPill("Clear filters", onClick = { filter = LapFilter() })
                            }
                        }
                    }

                    else -> {
                        val lapsedGroups = groups.filter { it.status == EventStatus.Lapsed }
                        val currentGroups = groups.filter { it.status != EventStatus.Lapsed }
                        val currentFlat = currentGroups.flatMap { it.laps }
                        val pastFlat = lapsedGroups.flatMap { it.laps }
                        val currentGroupByLap = currentGroups.flatMap { g -> g.laps.map { it.lapId to g } }.toMap()
                        val currentGroupAt = currentFlat.map { currentGroupByLap.getValue(it.lapId) }
                        val newGroupIndices = currentFlat.indices.filter { i ->
                            i == 0 || currentGroupAt[i - 1].key != currentGroupAt[i].key
                        }.toSet()
                        if (currentFlat.isNotEmpty()) {
                            itemsIndexed(items = currentFlat, key = { _, lap -> lap.lapId.value }) { index, lap ->
                                val group = currentGroupAt[index]
                                if (index in newGroupIndices) {
                                    CurrentEventHeader(group = group, isFirst = index == 0)
                                }
                                val tagged = group.tag != null
                                val groupSharedTrack = group.laps.map { it.trackLabel }.distinct().size <= 1
                                val groupSharedDate = group.laps.map { it.createdAt.take(10) }.distinct().size <= 1
                                val bestMs = if (tagged) group.bestLapMs else fastest[lap.trackKey]?.lapTimeMs
                                val ranked = if (tagged) {
                                    group.laps.size >= 2 && lap.trackLabel != TrackNamer.UNKNOWN
                                } else {
                                    (trackCounts[lap.trackKey] ?: 0) >= 2
                                }
                                val isFastest = ranked && bestMs != null && lap.lapTimeMs == bestMs
                                val deltaMs = if (ranked && !isFastest && bestMs != null) {
                                    lap.lapTimeMs - bestMs
                                } else {
                                    null
                                }
                                val driverPsn = lap.driverPsnId
                                val driver = driverPsn?.let { psn ->
                                    profiles[psn.lowercase()] ?: owner?.takeIf { it.psnId.equals(psn, true) }
                                }
                                Column() {
                                    LapCard(
                                        lap = lap,
                                        driver = driver,
                                        fastest = isFastest,
                                        deltaMs = deltaMs,
                                        onOpen = { target ->
                                            if (compareMode) togglePick(target) else onOpenLap(target.lapId)
                                        },
                                        onEdit = ::edit,
                                        onDelete = { deleting = it },
                                        compareMode = compareMode,
                                        pick = when {
                                            picks.getOrNull(0)?.lapId == lap.lapId -> LapPick.Primary
                                            picks.getOrNull(1)?.lapId == lap.lapId -> LapPick.Reference
                                            else -> null
                                        },
                                        showTrack = !tagged || !groupSharedTrack,
                                        showDate = !tagged || !groupSharedDate,
                                        showFastestLabel = !tagged,
                                    )
                                    if (index != currentFlat.lastIndex && currentGroupAt[index + 1].key == currentGroupAt[index].key) {
                                        HorizontalDivider(
                                            modifier = Modifier.testTag("lap-divider"),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (lapsedGroups.isNotEmpty()) {
                            item(key = "past-header") {
                                PastEventsHeader(
                                    lapCount = lapsedGroups.sumOf { it.laps.size },
                                    eventCount = lapsedGroups.count { it.tag != null },
                                    expanded = pastExpanded,
                                    onToggle = { pastExpanded = !pastExpanded },
                                )
                            }
                            val pastGroupByLap = lapsedGroups.flatMap { g -> g.laps.map { it.lapId to g } }.toMap()
                            val pastGroupAt = pastFlat.map { pastGroupByLap.getValue(it.lapId) }
                            val pastNewGroupIndices = pastFlat.indices.filter { i ->
                                i == 0 || pastGroupAt[i - 1].key != pastGroupAt[i].key
                            }.toSet()
                            if (pastExpanded) {
                                itemsIndexed(items = pastFlat, key = { _, lap -> lap.lapId.value }) { index, lap ->
                                    val fastestLap = fastest[lap.trackKey]
                                    val ranked = (trackCounts[lap.trackKey] ?: 0) >= 2
                                    val isFastest = ranked && fastestLap?.lapId == lap.lapId
                                    val deltaMs = if (ranked && !isFastest && fastestLap != null) {
                                        lap.lapTimeMs - fastestLap.lapTimeMs
                                    } else {
                                        null
                                    }
                                    val driverPsn = lap.driverPsnId
                                    val driver = driverPsn?.let { psn ->
                                        profiles[psn.lowercase()] ?: owner?.takeIf { it.psnId.equals(psn, true) }
                                    }
                                    if (index in pastNewGroupIndices) {
                                        CurrentEventHeader(group = pastGroupAt[index])
                                    }
                                    Column() {
                                        LapCard(
                                            lap = lap,
                                            driver = driver,
                                            fastest = isFastest,
                                            deltaMs = deltaMs,
                                            onOpen = { target ->
                                                if (compareMode) togglePick(target) else onOpenLap(target.lapId)
                                            },
                                            onEdit = ::edit,
                                            onDelete = { deleting = it },
                                            compareMode = compareMode,
                                            pick = when {
                                                picks.getOrNull(0)?.lapId == lap.lapId -> LapPick.Primary
                                                picks.getOrNull(1)?.lapId == lap.lapId -> LapPick.Reference
                                                else -> null
                                            },
                                        )
                                        if (index != pastFlat.lastIndex && pastGroupAt[index + 1].key == pastGroupAt[index].key) {
                                            HorizontalDivider(
                                                modifier = Modifier.testTag("lap-divider"),
                                                thickness = 1.dp,
                                                color = Hairline,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { lap ->
        EditLapSheet(
            lap = lap,
            driver = lap.driverPsnId?.let { profiles[it.lowercase()] },
            owner = owner,
            profiles = profiles.values.toList(),
            candidates = candidates,
            onDismiss = {
                editingId = null
                reencodeResult = null
            },
            onSaveTrack = { name -> saveTrack(lap, name) },
            onAssignDriver = { profile -> assignDriver(lap, profile) },
            events = cachedEvents,
            onAssignEvent = { tag ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.setEvent(lap.lapId, tag)
                    }
                    reload++
                }
            },
            onAddReplay = {
                syncTarget = lap
                picker.launch(arrayOf("video/*"))
            },
            onOpenPlayStation = {
                if (!PlayStationApp.open(context)) message = "PlayStation App is not available."
            },
            onResumeSync = { onOpenSync(lap.lapId, null) },
            developerTools = settings.developerTools,
            reencoding = reencoding,
            reencodeResult = reencodeResult,
            onReencode = { reencode(lap) },
            onRemoveReplay = {
                scope.launch {
                    val removed = withContext(Dispatchers.IO) {
                        store.removeReplay(lap.lapId)
                    }
                    message = if (removed) "Replay removed." else "Replay could not be removed."
                    reload++
                }
            },
            tyre = lap.tyre,
            onAssignTyre = { compound ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.setTyre(lap.lapId, compound)
                    }
                    reload++
                }
            },
            tcs = lap.tcs,
            onAssignTcs = { level ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.setTcs(lap.lapId, level)
                    }
                    reload++
                }
            },
            brakeBalance = lap.brakeBalance,
            onAssignBrakeBalance = { level ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.setBrakeBalance(lap.lapId, level)
                    }
                    reload++
                }
            },
        )
    }

    deleting?.let { lap ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete lap?") },
            text = { Text("${lap.trackLabel} · ${lapTimeLabel(lap.lapTimeMs)} and its replay will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleting ?: return@TextButton
                    deleting = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            store.delete(target.lapId)
                        }
                        if (!deleted) message = "Lap could not be deleted."
                        reload++
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyLibrary(onOpenRecord: () -> Unit, onImport: () -> Unit) {
    SectionCard {
        Text("No laps yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Connect to GT7 on the Record tab to capture a lap. " +
                "You can add a replay later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryPill(
            text = "Go to Record",
            onClick = onOpenRecord,
            modifier = Modifier.fillMaxWidth(),
        )
        GhostPill(
            text = "Import a backup",
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().testTag("library-import-backup"),
        )
    }
}

@Composable
private fun PreviewBanner(onTurnOff: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Previewing the empty library",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onTurnOff) { Text("Turn off") }
    }
}

@Composable
private fun DriverChip(
    profile: DriverProfile?,
    menuOpen: Boolean,
    onAdd: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onGridStats: () -> Unit,
    onGtshRank: () -> Unit,
    onEdit: () -> Unit,
) {
    if (profile == null) {
        GhostPill(
            text = "Add GT profile",
            onClick = onAdd,
            modifier = Modifier.testTag("library-add-profile"),
        )
        return
    }
    Box {
        val flag = countryFlagEmoji(profile.countryCode).orEmpty()
        GhostPill(
            text = listOf(flag, profile.nickname).filter { it.isNotEmpty() }.joinToString(" ") + " ▾",
            onClick = onOpenMenu,
            modifier = Modifier.testTag("library-profile-menu"),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text("GT GridStats") }, onClick = onGridStats)
            DropdownMenuItem(text = { Text("GTSH-Rank") }, onClick = onGtshRank)
            DropdownMenuItem(text = { Text("Edit profile") }, onClick = onEdit)
        }
    }
}

private fun compareHint(picks: List<LapSummary>, referenceCount: Int): String {
    val primary = picks.firstOrNull() ?: return "Pick the primary lap"
    val event = primary.event
    return when {
        picks.size == 1 && event != null && referenceCount == 0 -> "No other lap in ${event.label}"
        picks.size == 1 && event != null -> "Pick a reference lap from ${event.label}"
        picks.size == 1 -> "Pick a reference lap on ${primary.trackLabel}"
        else -> "Primary ${lapTimeLabel(picks[0].lapTimeMs)} · " +
            "reference ${lapTimeLabel(picks[1].lapTimeMs)}"
    }
}

private fun summaryLine(
    laps: List<LapSummary>?,
    tracks: List<String>,
    previewEmpty: Boolean,
): String? {    if (laps.isNullOrEmpty() || previewEmpty) return null
    val best = laps.minOfOrNull { it.lapTimeMs }
    return buildString {
        append(laps.size)
        append(if (laps.size == 1) " lap · " else " laps · ")
        append(tracks.size)
        append(if (tracks.size == 1) " track" else " tracks")
        best?.let { append(" · Best ${lapTimeLabel(it)}") }
    }
}

private val LapSort.label: String
    get() = when (this) {
        LapSort.Newest -> "Newest"
        LapSort.Fastest -> "Fastest"
        LapSort.Track -> "Track"
        LapSort.Event -> "Event"
    }
