package io.github.jlai403.lapcraft.core.events

/** Whether an event is still live, upcoming, or already over. */
enum class EventStatus { Upcoming, Current, Lapsed, Unknown }

/**
 * Resolve the status of an [EventTag] at [nowMs].
 * [cacheIds] is the set of event ids still present in the cached schedule.
 * Tags carrying their own window win; auto tags fall back to cache membership;
 * manual tags without a window are [EventStatus.Unknown].
 */
fun EventTag.status(nowMs: Long, cacheIds: Set<String> = emptySet()): EventStatus {
    val start = startMs
    val end = endMs
    if (start != null || end != null) {
        val s = start ?: end ?: 0L
        val e = end ?: start ?: s
        return when {
            nowMs < s -> EventStatus.Upcoming
            nowMs > e -> EventStatus.Lapsed
            else -> EventStatus.Current
        }
    }
    if (auto) {
        return if (id in cacheIds) EventStatus.Current else EventStatus.Lapsed
    }
    return EventStatus.Unknown
}