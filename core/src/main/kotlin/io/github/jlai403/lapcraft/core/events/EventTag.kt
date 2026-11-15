package io.github.jlai403.lapcraft.core.events

import kotlinx.serialization.Serializable

/** A lap's optional sport-mode event tag. */
@Serializable
data class EventTag(
    val id: String,
    val kind: String, // daily | time-trial | championship
    val label: String,
    val series: String? = null,
    val round: Int? = null,
    val carClass: String? = null,
    val auto: Boolean = false,
)