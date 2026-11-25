package io.github.jlai403.lapcraft.app.events

import android.content.Context
import io.github.jlai403.lapcraft.app.catalog.CarCatalog
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.events.EventMatcher
import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.events.LapEventFacts
import io.github.jlai403.lapcraft.core.tire.TyreCompound

class EventTagger(private val context: Context) {
    fun facts(carCode: Int, trackLabel: String, createdAtMs: Long, lapNumber: Int, tyre: TyreCompound?): LapEventFacts {
        val carClass = CarCatalog(context).info(carCode)?.carClass
        val carName = CarCatalog(context).name(carCode)
        return LapEventFacts(createdAtMs, trackLabel, carName, carClass, tyre, lapNumber)
    }

    fun tag(carCode: Int, trackLabel: String, createdAtMs: Long, lapNumber: Int, tyre: TyreCompound?): EventTag? {
        val settings = SettingsStore(context)
        if (!settings.eventTags) return null
        val events = EventCache(context).events()
        if (events.isEmpty()) return null
        val facts = facts(carCode, trackLabel, createdAtMs, lapNumber, tyre)
        return EventMatcher.match(facts, events).tag
    }

    fun suggestions(carCode: Int, trackLabel: String, createdAtMs: Long, lapNumber: Int, tyre: TyreCompound?): List<io.github.jlai403.lapcraft.core.events.EventSuggestion> {
        val events = EventCache(context).events()
        if (events.isEmpty()) return emptyList()
        val facts = facts(carCode, trackLabel, createdAtMs, lapNumber, tyre)
        return EventMatcher.match(facts, events).suggestions
    }
}