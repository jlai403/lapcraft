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

fun CachedEvent.toTag(auto: Boolean = false): EventTag =
    EventTag(id, kind, label, series, round, carClass, auto, startMs, endMs)