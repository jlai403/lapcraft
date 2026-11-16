package io.github.jlai403.lapcraft.core.events

/** Which laps a library filter keeps, by event. */
sealed interface EventFilter {
    data object All : EventFilter
    data object None : EventFilter
    data class Kind(val kind: EventKind) : EventFilter
}
