package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.core.events.CachedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object GridStatsEventParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parseDailies(
        thisWeekJson: String?,
        nextWeekJson: String?,
        ownerPsn: String?,
        ownerDailyJson: String?,
    ): List<CachedEvent> {
        val now = System.currentTimeMillis()
        val ownerResults = parseOwnerDaily(ownerDailyJson)
        val out = mutableListOf<CachedEvent>()
        listOfNotNull(thisWeekJson, nextWeekJson).forEach { raw ->
            val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return@forEach
            val data = root["data"]?.jsonArray ?: return@forEach
            data.forEach { el ->
                val o = el.jsonObject
                val raceTypeRaw = o["race_type"]?.jsonPrimitive?.content ?: return@forEach
                val label = normalizeRaceType(raceTypeRaw)
                val track = o["track_name"]?.jsonPrimitive?.content
                val begin = o["begin_date"]?.jsonPrimitive?.content
                val end = o["end_date"]?.jsonPrimitive?.content
                val carClass = o["car_class"]?.jsonPrimitive?.content
                val startMs = parseDate(begin) ?: return@forEach
                val endMs = parseDate(end) ?: startMs
                val id = "daily-$label-${begin ?: ""}"
                val ownerCar = ownerResults.firstOrNull {
                    normalizeRaceType(it.raceType) == label && (track == null || it.trackName == track)
                }?.carName
                out.add(
                    CachedEvent(
                        id = id,
                        kind = "daily",
                        label = label,
                        carClass = carClass,
                        trackName = track,
                        ownerCarName = ownerCar,
                        startMs = startMs,
                        endMs = endMs,
                        source = "gridstats",
                        fetchedAtMs = now,
                    )
                )
            }
        }
        return out.distinctBy { it.id }
    }

    fun parseTimeTrials(
        ttLiveJson: String?,
        ownerPsn: String?,
        ownerTtJson: String?,
    ): List<CachedEvent> {
        val now = System.currentTimeMillis()
        val ownerResults = parseOwnerTt(ownerTtJson)
        val root = runCatching { ttLiveJson?.let { json.parseToJsonElement(it).jsonObject } }.getOrNull() ?: return emptyList()
        val data = root["data"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<CachedEvent>()
        data.forEach { el ->
            val o = el.jsonObject
            val eventId = o["event_id"]?.jsonPrimitive?.content ?: return@forEach
            val title = o["title"]?.jsonPrimitive?.content ?: "Time Trial"
            val track = o["track_name"]?.jsonPrimitive?.content ?: o["track"]?.jsonPrimitive?.content
            val begin = o["begin_date"]?.jsonPrimitive?.content
            val end = o["end_date"]?.jsonPrimitive?.content
            val startMs = parseDate(begin) ?: return@forEach
            val endMs = parseDate(end) ?: startMs
            val ownerCar = ownerResults.firstOrNull { it.eventId == eventId }?.carName
            out.add(
                CachedEvent(
                    id = "tt-$eventId",
                    kind = "time-trial",
                    label = "Time Trial",
                    trackName = track,
                    ownerCarName = ownerCar,
                    startMs = startMs,
                    endMs = endMs,
                    source = "gridstats",
                    fetchedAtMs = now,
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private fun normalizeRaceType(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("Daily Race", ignoreCase = true)) t else "Daily Race $t"
    }

    private fun parseDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            val ldt = LocalDateTime.parse(s.trim(), fmt)
            ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private data class OwnerDaily(val raceType: String, val trackName: String?, val carName: String?)
    private fun parseOwnerDaily(raw: String?): List<OwnerDaily> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.jsonObject
            val raceType = o["race_type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val track = o["track_name"]?.jsonPrimitive?.content
            val car = o["car_name"]?.jsonPrimitive?.content
            OwnerDaily(raceType, track, car)
        }
    }

    private data class OwnerTt(val eventId: String, val carName: String?)
    private fun parseOwnerTt(raw: String?): List<OwnerTt> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.jsonObject
            val eventId = o["event_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val car = o["car_name"]?.jsonPrimitive?.content
            OwnerTt(eventId, car)
        }
    }
}