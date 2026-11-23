package io.github.jlai403.lapcraft.core.events

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.UK).withZone(ZoneOffset.UTC)

/** GT7 Sport Mode rolls Daily Races at 07:00 UTC on Monday. */
private const val SPORT_MODE_RESET_HOUR_UTC = 7
private const val SPORT_MODE_WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** Start of the Sport Mode week that begins on [date], which is a Monday. */
fun sportModeWeekStart(date: LocalDate): Long =
    date.atTime(SPORT_MODE_RESET_HOUR_UTC, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

/** Last second of the Sport Mode week starting at [startMs]. */
fun sportModeWeekEnd(startMs: Long): Long = startMs + SPORT_MODE_WEEK_MS - 1_000

/** ISO week of a timestamp in UTC. Daily races rotate weekly, so the week names the round. */
fun isoWeekOf(epochMs: Long): Int =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneOffset.UTC)
        .get(WeekFields.ISO.weekOfWeekBasedYear())

/** "Week of Nov 16": the Monday the round started, matching how GT GridStats labels weeks. */
fun weekStartLabel(epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate()
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    return "Week of ${dayFormat.format(monday)}"
}

/** Monday 00:00 UTC of the ISO week containing [epochMs]. */
fun startOfIsoWeek(epochMs: Long): Long {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate()
    val monday = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** "Nov 16 – Nov 23", or a single date when the range is missing or falls on one day. */
fun eventRangeLabel(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val format = dayFormat.withZone(zone)
    val start = format.format(Instant.ofEpochMilli(startMs))
    val end = format.format(Instant.ofEpochMilli(endMs))
    if (endMs <= startMs || end == start) return start
    return "$start – $end"
}
