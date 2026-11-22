package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.core.events.CachedEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EventCacheFile(
    val gridstatsFetchedAtMs: Long = 0,
    val polyphonyFetchedAtMs: Long = 0,
    // Region folder of the last Polyphony fetch; 0 means an old cache.
    val polyphonyFolder: Int = 0,
    val events: List<CachedEvent> = emptyList(),
) {
    /** Newest of the two feeds: whichever refreshed last owns the "updated" line. */
    val lastFetchedAtMs: Long get() = maxOf(gridstatsFetchedAtMs, polyphonyFetchedAtMs)
}

class EventCache(private val rootDir: File) {
    constructor(context: android.content.Context) : this(java.io.File(context.filesDir, "events").also { it.mkdirs() })
    private val file = File(rootDir, "events.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): EventCacheFile {
        if (!file.isFile) return EventCacheFile()
        return runCatching { json.decodeFromString<EventCacheFile>(file.readText()) }.getOrDefault(EventCacheFile())
    }

    fun save(cache: EventCacheFile) {
        rootDir.mkdirs()
        io.github.jlai403.lapcraft.core.io.AtomicFiles.writeText(file, json.encodeToString(EventCacheFile.serializer(), cache))
    }

    fun events(): List<CachedEvent> = load().events

    fun isGridstatsStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - load().gridstatsFetchedAtMs > GRIDSTATS_TTL_MS

    fun isPolyphonyStale(
        nowMs: Long = System.currentTimeMillis(),
        folder: Int = PolyphonyRegion.folder(),
    ): Boolean {
        val cache = load()
        return cache.polyphonyFolder != folder || nowMs - cache.polyphonyFetchedAtMs > POLYPHONY_TTL_MS
    }

    fun replaceGridstats(events: List<CachedEvent>, nowMs: Long = System.currentTimeMillis()) {
        val current = load()
        val kept = current.events.filterNot { it.source == "gridstats" }
        save(current.copy(gridstatsFetchedAtMs = nowMs, events = kept + events))
    }

    fun replacePolyphony(
        events: List<CachedEvent>,
        nowMs: Long = System.currentTimeMillis(),
        folder: Int = PolyphonyRegion.folder(),
    ) {
        val current = load()
        val kept = current.events.filterNot { it.source == "polyphony-cdn" }
        save(current.copy(polyphonyFetchedAtMs = nowMs, polyphonyFolder = folder, events = kept + events))
    }

    companion object {
        const val GRIDSTATS_TTL_MS = 12L * 60 * 60 * 1000
        const val POLYPHONY_TTL_MS = 24L * 60 * 60 * 1000
    }
}