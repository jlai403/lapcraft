package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.events.EventTag

data class LapView(
    val index: Int = 1,
    val lapTimeMs: Int,
    val startNanos: Long,
    val endNanos: Long,
    val leadInMs: Int,
    val trackLabel: String = "",
    val carName: String = "",
    val driverPsnId: String? = null,
    val event: EventTag? = null,
)

fun LapRecord.toLapView(): LapView = LapView(
    index = 1,
    lapTimeMs = lapTimeMs,
    startNanos = startTickNanos,
    endNanos = endTickNanos,
    leadInMs = leadInMs,
    trackLabel = trackLabel,
    carName = carName,
    driverPsnId = driverPsnId,
    event = event,
)