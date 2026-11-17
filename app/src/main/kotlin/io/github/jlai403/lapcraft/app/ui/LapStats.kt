package io.github.jlai403.lapcraft.app.ui

import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapView

internal data class LoadedLap(val lap: LapView, val samples: List<Sample>)

internal data class LapStats(
    val lapTimeMs: Int,
    val topSpeedMps: Float,
    val minSpeedMps: Float,
    val avgSpeedMps: Float,
    val brakeTimeMs: Int,
    val fullThrottleMs: Int,
)

internal fun lapStats(window: List<Sample>, lap: LapView): LapStats? {
    if (window.size < 2) return null
    var top = window.first().speedMps
    var min = top
    var weighted = 0.0
    var total = 0L
    var brake = 0L
    var full = 0L
    for (index in 1 until window.size) {
        val dt = (window[index].lapTimeMs(lap) - window[index - 1].lapTimeMs(lap))
            .coerceAtLeast(0)
        val speed = window[index].speedMps
        top = maxOf(top, speed)
        min = minOf(min, speed)
        weighted += speed.toDouble() * dt
        total += dt
        if (window[index].brake > 0) brake += dt
        if (window[index].throttle >= 200) full += dt
    }
    if (total == 0L) return null
    return LapStats(
        lapTimeMs = lap.lapTimeMs,
        topSpeedMps = top,
        minSpeedMps = min,
        avgSpeedMps = (weighted / total).toFloat(),
        brakeTimeMs = brake.toInt(),
        fullThrottleMs = full.toInt(),
    )
}

internal fun lapStatsLine(stats: LapStats, imperial: Boolean): String {
    val unit = speedUnit(imperial)
    return "Top %.0f %s · Min %.0f · Avg %.0f".format(
        speedValue(stats.topSpeedMps, imperial),
        unit,
        speedValue(stats.minSpeedMps, imperial),
        speedValue(stats.avgSpeedMps, imperial),
    )
}

internal fun speedUnit(imperial: Boolean): String = if (imperial) "mph" else "km/h"

internal fun speedValue(mps: Float, imperial: Boolean): Float = mps * if (imperial) 2.23694f else 3.6f

internal fun lapStatsComparison(
    current: LapStats,
    ghost: LapStats?,
    imperial: Boolean,
    label: String = "ghost",
): String {
    if (ghost == null) return "No $label lap on this track yet."
    val unit = speedUnit(imperial)
    val top = speedValue(current.topSpeedMps - ghost.topSpeedMps, imperial)
    val avg = speedValue(current.avgSpeedMps - ghost.avgSpeedMps, imperial)
    val brake = percent(current.brakeTimeMs, current.lapTimeMs) -
        percent(ghost.brakeTimeMs, ghost.lapTimeMs)
    val full = percent(current.fullThrottleMs, current.lapTimeMs) -
        percent(ghost.fullThrottleMs, ghost.lapTimeMs)
    val lap = (current.lapTimeMs - ghost.lapTimeMs) / 1_000.0
    return "vs $label: top %+.0f %s · avg %+.0f · brake %+d%% · full %+d%% · lap %+.3fs".format(
        top,
        unit,
        avg,
        brake,
        full,
        lap,
    )
}

private fun percent(partMs: Int, wholeMs: Int): Int =
    if (wholeMs <= 0) 0 else partMs * 100 / wholeMs

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
