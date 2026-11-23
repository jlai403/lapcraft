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

private fun capturedAtMs(lap: LapSummary): Long =
    runCatching { Instant.parse(lap.createdAt).toEpochMilli() }.getOrDefault(Long.MAX_VALUE)

fun List<LapSummary>.eventGroups(
    cache: List<CachedEvent> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
): List<LapEventGroup> {
    val cacheIds = cache.map { it.id }.toSet()
    val weekStart = startOfIsoWeek(nowMs)
    val untagged = mutableListOf<LapSummary>()
    val byKey = LinkedHashMap<String, MutableList<LapSummary>>()
    for (lap in this) {
        val tag = lap.event
        if (tag == null) {
            untagged += lap
            continue
        }
        byKey.getOrPut(tag.matchKey) { mutableListOf() } += lap
    }

    fun eventStart(groupLaps: List<LapSummary>): Long {
        val start = groupLaps.firstNotNullOfOrNull { it.event?.normalizedDaily()?.startMs }
        if (start != null) return start
        return groupLaps.maxOfOrNull { capturedAtMs(it) } ?: 0L
    }
    fun eventEnd(groupLaps: List<LapSummary>): Long =
        groupLaps.firstNotNullOfOrNull { it.event?.normalizedDaily()?.endMs } ?: eventStart(groupLaps)

    val current = mutableListOf<LapEventGroup>()
    val lapsed = mutableListOf<LapEventGroup>()
    for ((key, laps) in byKey) {
        val tag = laps.first().event!!.normalizedDaily()
        // Legacy tags in the same group have no window; any lap's window is the event's.
        val windowed = laps.firstNotNullOfOrNull { l ->
            l.event?.normalizedDaily()?.takeIf { it.startMs != null || it.endMs != null }
        } ?: tag
        val status = windowed.status(nowMs, cacheIds)
        val resolved = if (status == EventStatus.Unknown) {
            val newest = laps.maxOfOrNull { capturedAtMs(it) } ?: 0L
            if (newest < weekStart) EventStatus.Lapsed else EventStatus.Unknown
        } else status
        val group = LapEventGroup(key, tag, resolved, laps, laps.minOf { it.lapTimeMs })
        if (resolved == EventStatus.Lapsed) lapsed += group else current += group
    }
    current.sortByDescending { eventStart(it.laps) }

    // Untagged laps split by age: this week stays in the main feed, older ones archive.
    val (untaggedCurrent, untaggedOld) = untagged.partition { capturedAtMs(it) >= weekStart }
    if (untaggedCurrent.isNotEmpty()) {
        current += LapEventGroup(
            key = "untagged",
            tag = null,
            status = EventStatus.Unknown,
            laps = untaggedCurrent,
            bestLapMs = untaggedCurrent.minOf { it.lapTimeMs },
        )
    }
    if (untaggedOld.isNotEmpty()) {
        lapsed += LapEventGroup(
            key = "untagged-past",
            tag = null,
            status = EventStatus.Lapsed,
            laps = untaggedOld,
            bestLapMs = untaggedOld.minOf { it.lapTimeMs },
        )
    }
    lapsed.sortByDescending { eventEnd(it.laps) }

    val result = mutableListOf<LapEventGroup>()
    result += current
    result += lapsed
    return result
}