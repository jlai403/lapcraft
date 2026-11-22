package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.lap.LapTimeSource

import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.tire.TyreCompound
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val driverPsnId: String? = null,
    val event: EventTag? = null,
    val tyre: TyreCompound? = null,
    val tcs: Int? = null,
    val brakeBalance: Int? = null,
)

// toLapView() lives in LapView.kt