package io.github.jlai403.lapcraft.core.events

import kotlinx.serialization.Serializable

@Serializable
data class EventTag(
    val id: String,
    val kind: EventKind,
    val label: String,
    val series: String? = null,
    val round: Int? = null,
    val carClass: String? = null,
    val auto: Boolean = false,
    // Snapshot of the event window at tag time, so a lapsed event keeps its dates
    // after the schedule refresh drops it. Null for legacy tags and manual tags.
    val startMs: Long? = null,
    val endMs: Long? = null,
)

// Manual tags are stamped with a unique id, so they group and compare by their text instead.
val EventTag.matchKey: String
    get() = if (id.startsWith("manual-")) "manual-" + EventMatcher.normalize(label) else id

fun CachedEvent.toTag(auto: Boolean = false): EventTag =
    EventTag(id, kind, label, series, round, carClass, auto, startMs, endMs)

/** Offer an event already tagged in the library after the feed has moved on. */
fun EventTag.toCachedEvent(): CachedEvent = CachedEvent(
    id = id,
    kind = kind,
    label = label,
    series = series,
    round = round,
    carClass = carClass,
    startMs = startMs ?: 0,
    endMs = endMs ?: 0,
    source = "library",
    fetchedAtMs = 0,
)