package io.github.jlai403.lapcraft.core.events

/**
 * Rematch an auto championship tag after the schedule moved to another region folder,
 * which changes the season id and therefore the event id.
 * Returns null when the tag is manual, already cached, or has no single match.
 */
fun EventTag.repairedFrom(cache: List<CachedEvent>, createdAtMs: Long): EventTag? {
    if (!auto || kind != EventKind.Championship) return null
    if (cache.any { it.id == id }) return null
    if (series == null || round == null) return null
    val match = cache.filter {
        it.kind == EventKind.Championship &&
            it.series == series &&
            it.round == round &&
            createdAtMs in it.startMs..it.endMs
    }
    return match.singleOrNull()?.toTag(auto = true)
}
