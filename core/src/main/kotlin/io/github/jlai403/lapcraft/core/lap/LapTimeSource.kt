package io.github.jlai403.lapcraft.core.lap

/** Where a lap's time came from. GT7 reports it at the crossing; the app measures when the report is missing or implausible. */
import kotlinx.serialization.Serializable

@Serializable
enum class LapTimeSource { Reported, Measured }
