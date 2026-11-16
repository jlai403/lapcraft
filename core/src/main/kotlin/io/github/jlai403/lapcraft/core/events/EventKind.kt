package io.github.jlai403.lapcraft.core.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The sport-mode category of a lap's optional event tag. */
@Serializable
enum class EventKind {
    @SerialName("daily") Daily,
    @SerialName("time-trial") TimeTrial,
    @SerialName("championship") Championship;

    val label: String
        get() = when (this) {
            Daily -> "Daily"
            TimeTrial -> "Time Trial"
            Championship -> "Championship"
        }
}
