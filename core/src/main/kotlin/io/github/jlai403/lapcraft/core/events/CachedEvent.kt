package io.github.jlai403.lapcraft.core.events

import kotlinx.serialization.Serializable

@Serializable
data class CachedEvent(
    val id: String,
    val kind: EventKind,
    val label: String,
    val series: String? = null,
    val round: Int? = null,
    val carClass: String? = null,
    val trackName: String? = null,
    val ownerCarName: String? = null,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val fetchedAtMs: Long,
)

data class EventMatchResult(
    val tag: EventTag? = null,
    val candidates: List<CachedEvent> = emptyList(),
)

object EventMatcher {
    private const val MARGIN_MS = 12L * 60 * 60 * 1000

    fun normalize(value: String?): String =
        value?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "").orEmpty()

    fun match(
        createdAtMs: Long,
        trackLabel: String?,
        carClass: String?,
        carName: String?,
        events: List<CachedEvent>,
    ): EventMatchResult {
        val trackN = normalize(trackLabel)
        val inTime = events.filter { e ->
            createdAtMs in (e.startMs - MARGIN_MS)..(e.endMs + MARGIN_MS)
        }
        val trackMatched = inTime.filter { e ->
            e.trackName == null || normalize(e.trackName) == trackN
        }
        val pool0 = if (trackMatched.isNotEmpty()) trackMatched else inTime
        val classFiltered = if (carClass != null) {
            pool0.filter { e -> e.carClass == null || e.carClass == carClass }
        } else pool0
        val pool = if (classFiltered.isNotEmpty()) classFiltered else pool0
        val exact = pool.filter { createdAtMs in it.startMs..it.endMs }
        val strong = exact.filter { e ->
            (e.carClass == null || carClass == null || e.carClass == carClass) &&
                (e.ownerCarName == null || carName == null || normalize(e.ownerCarName) == normalize(carName))
        }
        val tag = if (strong.size == 1) {
            val e = strong.first()
            EventTag(
                id = e.id,
                kind = e.kind,
                label = e.label,
                series = e.series,
                round = e.round,
                carClass = e.carClass,
                auto = true,
            )
        } else null
        return EventMatchResult(tag, pool.distinct())
    }
}