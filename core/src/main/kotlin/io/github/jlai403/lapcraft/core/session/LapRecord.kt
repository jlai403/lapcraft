package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.events.EventTag
import kotlinx.serialization.Serializable

/** Where a lap's time came from. GT7 reports it at the crossing; the app measures when the report is missing or implausible. */
@Serializable
enum class LapTimeSource { Reported, Measured }

/**
 * One lap. Facts only; nothing about the container it used to live in, and nothing
 * that can be derived from the directory (which files exist decides replay state).
 */
@Serializable
data class LapRecord(
    val schemaVersion: Int = 2,
    val capturedAt: String,
    val lapTimeMs: Int,
    val lapTimeSource: LapTimeSource = LapTimeSource.Reported,
    val circuitId: String = "",
    val circuitLabel: String = "",
    val carCode: Int = 0,
    val carName: String = "",
    val startTickNanos: Long = 0L,
    val endTickNanos: Long = 0L,
    val sampleRateHz: Int = 60,
    val leadInMs: Int = 0,
    val syncOffsetMs: Int = 0,
    val driverPsnId: String? = null,
    val event: EventTag? = null,
)