package io.github.jlai403.lapcraft.core.events

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.UK).withZone(ZoneOffset.UTC)

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

/** "Nov 16 – Nov 23", or a single date when the range is missing. */
fun eventRangeLabel(startMs: Long, endMs: Long): String {
    val start = dayFormat.format(Instant.ofEpochMilli(startMs))
    if (endMs <= startMs) return start
    return "$start – ${dayFormat.format(Instant.ofEpochMilli(endMs))}"
}