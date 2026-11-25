package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.events.EventFilter
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.matchKey
import io.github.jlai403.lapcraft.core.tire.TyreCompound

import java.time.Instant

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
    val tyre: TyreCompound? = null,
    val tcs: Int? = null,
    val brakeBalance: Int? = null,
    val videoBytes: Long = 0,
    val telemetryBytes: Long = 0,
    val pendingBytes: Long = 0,
    val carCode: Int = 0,
    val lapCount: Int = 0,
)

enum class LapSort { Newest, Fastest, Track, Event }

data class LapFilter(
    val trackKey: String? = null,
    val car: String? = null,
    val event: EventFilter = EventFilter.All,
    val eventId: String? = null,
)

fun List<LapSummary>.applyFilter(filter: LapFilter): List<LapSummary> = filter { lap ->
    (filter.trackKey == null || lap.trackKey == filter.trackKey) &&
        (filter.car == null || lap.carName == filter.car) &&
        (filter.eventId == null || lap.event?.id == filter.eventId) &&
        when (filter.event) {
            EventFilter.All -> true
            EventFilter.None -> lap.event == null
            is EventFilter.Kind -> lap.event?.kind == filter.event.kind
        }
}

fun List<LapSummary>.applySort(sort: LapSort): List<LapSummary> = when (sort) {
    LapSort.Newest -> sortedByDescending { parseInstant(it.createdAt) }
    LapSort.Fastest -> sortedBy { it.lapTimeMs }
    LapSort.Track -> sortedWith(compareBy({ it.trackLabel }, { it.lapTimeMs }))
    LapSort.Event -> sortedWith(compareBy({ it.event?.label.orEmpty() }, { parseInstant(it.createdAt) }))
}

fun List<LapSummary>.fastestPerTrack(): Map<String, LapSummary> =
    groupBy { it.trackKey }.mapValues { (_, laps) ->
        laps.minWith(compareBy({ it.lapTimeMs }, { parseInstant(it.createdAt) }))
    }

val LapSummary.trackKey: String
    get() = trackId.ifBlank { trackLabel.trim().lowercase() }

// A reference lap has to run the same track and, when the primary is tagged, the same event.
fun LapSummary.canCompareWith(primary: LapSummary): Boolean =
    trackKey == primary.trackKey && event?.matchKey == primary.event?.matchKey

fun List<LapSummary>.compareCandidates(primary: LapSummary): List<LapSummary> =
    filter { it.lapId != primary.lapId && it.canCompareWith(primary) }


private fun parseInstant(value: String): Instant =
    runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
