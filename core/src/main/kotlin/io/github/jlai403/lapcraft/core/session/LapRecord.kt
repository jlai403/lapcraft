package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.lap.LapTimeSource

import io.github.jlai403.lapcraft.core.events.EventTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("circuitId")
    val trackId: String = "",
    @SerialName("circuitLabel")
    val trackLabel: String = "",
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

fun LapRecord.toLapMetadata(): LapMetadata = LapMetadata(
    index = 1,
    lapTimeMs = lapTimeMs,
    startNanos = startTickNanos,
    endNanos = endTickNanos,
    sampleCount = 0,
    videoFile = "",
    leadInMs = leadInMs,
    syncOffsetMs = syncOffsetMs,
    needsCut = false,
    sourceFile = null,
    driverPsnId = driverPsnId,
    event = event,
)