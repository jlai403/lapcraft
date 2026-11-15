package io.github.jlai403.lapcraft.app.events

import android.content.Context
import io.github.jlai403.lapcraft.app.catalog.CarCatalog
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.events.EventMatcher
import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventTag

class EventTagger(private val context: Context) {
    fun tag(carCode: Int, trackLabel: String, createdAtMs: Long, lapTimeMs: Int): EventTag? {
        val settings = SettingsStore(context)
        if (!settings.eventTags) return null
        val events = EventCache(context).events()
        if (events.isEmpty()) return null
        val carClass = CarCatalog(context).info(carCode)?.carClass
        val carName = CarCatalog(context).name(carCode)
        return EventMatcher.match(createdAtMs, trackLabel, carClass, carName, events).tag
    }

    fun candidates(carCode: Int, trackLabel: String, createdAtMs: Long): List<CachedEvent> {
        val events = EventCache(context).events()
        val carClass = CarCatalog(context).info(carCode)?.carClass
        val carName = CarCatalog(context).name(carCode)
        return EventMatcher.match(createdAtMs, trackLabel, carClass, carName, events).candidates
    }
}