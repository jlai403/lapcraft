package io.github.jlai403.lapcraft.core.events

import io.github.jlai403.lapcraft.core.session.LapSummary
import java.time.Instant

data class LapEventGroup(
    val key: String,
    val tag: EventTag?,
    val status: EventStatus,
    val laps: List<LapSummary>,
    val bestLapMs: Int,
)

fun List<LapSummary>.eventGroups(
    cache: List<CachedEvent> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
): List<LapEventGroup> {
    val cacheIds = cache.map { it.id }.toSet()
    val untagged = mutableListOf<LapSummary>()
    val byKey = LinkedHashMap<String, MutableList<LapSummary>>()
    for (lap in this) {
        val tag = lap.event
        if (tag == null) {
            untagged += lap
            continue
        }
        val key = if (tag.id.startsWith("manual-")) {
            "manual-" + EventMatcher.normalize(tag.label)
        } else {
            tag.id
        }
        byKey.getOrPut(key) { mutableListOf() } += lap
    }

    fun eventStart(groupLaps: List<LapSummary>): Long {
        val tag = groupLaps.first().event
        if (tag?.startMs != null) return tag.startMs
        return groupLaps.maxOfOrNull { runCatching { Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(0L) } ?: 0L
    }
    fun eventEnd(groupLaps: List<LapSummary>): Long {
        val tag = groupLaps.first().event
        if (tag?.endMs != null) return tag.endMs
        return eventStart(groupLaps)
    }

    val current = byKey.map { (key, laps) ->
        val tag = laps.first().event!!
        LapEventGroup(key, tag, tag.status(nowMs, cacheIds), laps, laps.minOf { it.lapTimeMs })
    }.filter { it.status != EventStatus.Lapsed }
        .sortedByDescending { eventStart(it.laps) }

    val lapsed = byKey.map { (key, laps) ->
        val tag = laps.first().event!!
        LapEventGroup(key, tag, tag.status(nowMs, cacheIds), laps, laps.minOf { it.lapTimeMs })
    }.filter { it.status == EventStatus.Lapsed }
        .sortedByDescending { eventEnd(it.laps) }

    val result = mutableListOf<LapEventGroup>()
    result += current
    if (untagged.isNotEmpty()) {
        result += LapEventGroup(
            key = "untagged",
            tag = null,
            status = EventStatus.Unknown,
            laps = untagged,
            bestLapMs = untagged.minOf { it.lapTimeMs },
        )
    }
    result += lapsed
    return result
}