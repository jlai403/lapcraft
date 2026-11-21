package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.clip.LapClock
import io.github.jlai403.lapcraft.core.events.EventTag
import io.github.jlai403.lapcraft.core.tire.TyreCompound

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
    val tyre: TyreCompound? = null,
) {
    /**
     * Real elapsed milliseconds of the lap, from its boundary ticks. GT7 reports [lapTimeMs] in
     * 60ths of a second while a 59.94 Hz capture runs about 0.1% slower, so the clip has to cover
     * this span instead. Falls back to [lapTimeMs] when the ticks are missing or implausible.
     */
    val lapSpanMs: Int
        get() {
            val span = ((endNanos - startNanos) / 1_000_000L).toInt()
            return if (span in lapTimeMs - SPAN_SLACK_MS..lapTimeMs + SPAN_SLACK_MS) {
                span
            } else {
                lapTimeMs
            }
        }

    private companion object {
        const val SPAN_SLACK_MS = 2_000
    }
}

fun LapView.lapClock(): LapClock = LapClock.forTrimmedLap(leadInMs, lapTimeMs, lapSpanMs)

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
    tyre = tyre,
)
