package io.github.jlai403.lapcraft.core.events

import io.github.jlai403.lapcraft.core.tire.TyreCompound
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
    val laps: Int? = null,
    val tyreFamily: String? = null,
    val tyreCompounds: List<String> = emptyList(),
    val cars: List<String> = emptyList(),
)

data class EventMatchResult(
    val tag: EventTag? = null,
    val suggestions: List<EventSuggestion> = emptyList(),
)

data class EventSuggestion(
    val event: CachedEvent,
    val score: Int,
    val reasons: List<EventReason>,
)

enum class EventReason { Track, CarClass, Car, Tyre }

data class LapEventFacts(
    val createdAtMs: Long,
    val trackLabel: String?,
    val carName: String?,
    val carClass: String?,
    val tyre: TyreCompound?,
    val lapNumber: Int,
)

object EventMatcher {
    private const val MARGIN_MS = 12L * 60 * 60 * 1000

    fun normalize(value: String?): String =
        value?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "").orEmpty()

    fun match(facts: LapEventFacts, events: List<CachedEvent>): EventMatchResult {
        val trackN = normalize(facts.trackLabel)
        val carN = normalize(facts.carName)
        val classN = normalizeClass(facts.carClass)
        val tyreFamily = facts.tyre?.family
        val tyreCompounds = facts.tyre?.let { listOf(it.tier.name) } ?: emptyList<String>()
        val lapNumber = facts.lapNumber

        val inTime = events.filter { e ->
            facts.createdAtMs in (e.startMs - MARGIN_MS)..(e.endMs + MARGIN_MS)
        }

        val scored = inTime.mapNotNull { e ->
            val reasons = mutableListOf<EventReason>()
            var score = 0

            // Track: exact normalized match; a known track that differs drops the event.
            val eventTrackN = normalize(e.trackName)
            if (eventTrackN.isNotEmpty() && trackN.isNotEmpty()) {
                if (eventTrackN == trackN) {
                    score += 3
                    reasons += EventReason.Track
                } else if (trackN.isNotEmpty()) {
                    return@mapNotNull null
                }
            }

            // Car class: compatible gives points, incompatible drops the event.
            val eventClassN = normalizeClass(e.carClass)
            if (eventClassN.isNotEmpty() && classN.isNotEmpty()) {
                if (classCompatible(e.carClass, facts.carClass)) {
                    score += 2
                    reasons += EventReason.CarClass
                } else {
                    return@mapNotNull null
                }
            }

            // Lap count: a daily cannot be shorter than the lap you just did.
            if (e.laps != null && lapNumber > 0 && e.laps < lapNumber) {
                return@mapNotNull null
            }

            // Car in the field: meta car plus other top cars.
            if (e.cars.isNotEmpty() && carN.isNotEmpty()) {
                if (e.cars.any { normalize(it) == carN }) {
                    score += 3
                    reasons += EventReason.Car
                }
            }
            // Owner car for that event, when it matches.
            if (e.ownerCarName != null && carN.isNotEmpty() &&
                normalize(e.ownerCarName) == carN
            ) {
                if (EventReason.Car !in reasons) {
                    score += 3
                    reasons += EventReason.Car
                }
            }

            // Tyre: family match plus a compound that is allowed.
            if (tyreFamily != null && e.tyreFamily != null &&
                tyreFamily.equals(e.tyreFamily, ignoreCase = true)
            ) {
                val allowed = e.tyreCompounds
                val compoundMatches = allowed.isEmpty() ||
                    allowed.any { it.equals(facts.tyre?.tier?.name, ignoreCase = true) }
                if (compoundMatches) {
                    score += 2
                    reasons += EventReason.Tyre
                }
            }

            EventSuggestion(e, score, reasons)
        }

        val sorted = scored.sortedWith(
            compareByDescending<EventSuggestion> { it.score }
                .thenByDescending { it.event.startMs <= facts.createdAtMs && facts.createdAtMs <= it.event.endMs }
                .thenByDescending { it.event.startMs },
        )

        val top = sorted.firstOrNull()
        val second = sorted.getOrNull(1)
        val tag = if (top != null && autoTag(top, second, sorted.size)) {
            top.event.toTag(auto = true)
        } else null

        return EventMatchResult(tag, sorted.take(5))
    }

    private fun autoTag(top: EventSuggestion, second: EventSuggestion?, count: Int): Boolean {
        val signals = top.reasons.size
        val onlyTrack = signals == 1 && EventReason.Track in top.reasons && count == 1
        if (signals < 2 && !onlyTrack) return false
        val lead = if (second == null) Int.MAX_VALUE else top.score - second.score
        return lead >= 2 || second == null
    }

    private fun normalizeClass(value: String?): String =
        value?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "").orEmpty()

    private fun classCompatible(eventClass: String?, carClass: String?): Boolean {
        val a = eventClass?.trim()?.lowercase()
        val b = carClass?.trim()?.lowercase()
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a == b) return true
        // "Road" dailies accept road cars (Gr.N); treat Gr.X as unknown, not wrong.
        if (a == "road") {
            return b == "gr.n" || b == "grn"
        }
        return false
    }

    // Keep the old signature working for callers that only have raw values.
    fun match(
        createdAtMs: Long,
        trackLabel: String?,
        carClass: String?,
        carName: String?,
        events: List<CachedEvent>,
    ): EventMatchResult = match(
        LapEventFacts(createdAtMs, trackLabel, carName, carClass, null, 0),
        events,
    )
}
