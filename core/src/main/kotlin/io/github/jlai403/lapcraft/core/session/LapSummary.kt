package io.github.jlai403.lapcraft.core.session

import java.time.Instant

/** One row in the flat library. A session holds one lap for new captures; legacy sessions may hold several. */
data class LapSummary(
    val lapId: LapId,
    val lapTimeMs: Int,
    val createdAt: String,
    val trackId: String = "",
    val trackLabel: String,
    val carName: String,
    val hasReplay: Boolean,
    val replayPending: Boolean,
    val driverPsnId: String? = null,
    val event: io.github.jlai403.lapcraft.core.events.EventTag? = null,
    val videoBytes: Long = 0,
    val telemetryBytes: Long = 0,
    val pendingBytes: Long = 0,
)

enum class LapSort { Newest, Fastest, Track, Event }

data class LapFilter(
    val hasReplay: Boolean? = null,
    val trackKey: String? = null,
    val eventKind: String? = null,
    val eventId: String? = null,
)

fun List<LapSummary>.applyFilter(filter: LapFilter): List<LapSummary> = filter { lap ->
    (filter.hasReplay == null || lap.hasReplay == filter.hasReplay) &&
        (filter.trackKey == null || lap.trackKey == filter.trackKey) &&
        (filter.eventId == null || lap.event?.id == filter.eventId) &&
        (filter.eventKind == null ||
            (filter.eventKind == "none" && lap.event == null) ||
            (filter.eventKind != "none" && lap.event?.kind == filter.eventKind))
}

fun List<LapSummary>.applySort(sort: LapSort): List<LapSummary> = when (sort) {
    LapSort.Newest -> sortedByDescending { parseInstant(it.createdAt) }
    LapSort.Fastest -> sortedBy { it.lapTimeMs }
    LapSort.Track -> sortedWith(compareBy({ it.trackLabel }, { it.lapTimeMs }))
    LapSort.Event -> sortedWith(compareBy({ it.event?.label.orEmpty() }, { parseInstant(it.createdAt) }))
}

/** Quickest lap for each track label. A track with one lap keeps it; callers decide if that counts. */
fun List<LapSummary>.fastestPerTrack(): Map<String, LapSummary> =
    groupBy { it.trackKey }.mapValues { (_, laps) ->
        laps.minWith(compareBy({ it.lapTimeMs }, { parseInstant(it.createdAt) }))
    }

/** Track labels are user-editable, so compare them trimmed and case-insensitively. */
val LapSummary.trackKey: String
    get() = trackId.ifBlank { trackLabel.trim().lowercase() }


private fun parseInstant(value: String): Instant =
    runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
