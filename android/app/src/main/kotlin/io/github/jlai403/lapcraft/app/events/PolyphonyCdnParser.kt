package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

object PolyphonyCdnParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseActive(raw: String): List<CachedEvent> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val result = root["result"]?.jsonArray ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = mutableListOf<CachedEvent>()
        val seen = mutableSetOf<String>()
        result.forEach { el ->
            val o = el.jsonObject
            val titleMap = o["championship_title_map"]?.jsonObject
            val title = titleMap?.get("GB")?.jsonPrimitive?.content
                ?: titleMap?.get("US")?.jsonPrimitive?.content
                ?: "World Series"
            val seriesMap = o["season_title_map"]?.jsonObject
            val series = seriesMap?.get("GB")?.jsonPrimitive?.content
                ?: seriesMap?.get("US")?.jsonPrimitive?.content
            val seasonId = o["season_id"]?.jsonPrimitive?.content ?: return@forEach
            val seriesId = o["series_id"]?.jsonPrimitive?.content ?: return@forEach
            val rounds = o["rounds"]?.jsonArray ?: return@forEach
            rounds.forEach { r ->
                val ro = r.jsonObject
                val roundId = ro["round_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                val carClassRaw = ro["car_class"]?.jsonPrimitive?.content
                val carClass = mapCarClass(carClassRaw)
                val start = ro["start_date"]?.jsonPrimitive?.content ?: return@forEach
                val end = ro["end_date"]?.jsonPrimitive?.content ?: start
                val startMs = runCatching { Instant.parse(start).toEpochMilli() }.getOrNull() ?: return@forEach
                val endMs = runCatching { Instant.parse(end).toEpochMilli() }.getOrNull() ?: startMs
                val id = "champ-$seriesId-$seasonId-r$roundId"
                if (!seen.add(id)) return@forEach
                out.add(
                    CachedEvent(
                        id = id,
                        kind = EventKind.Championship,
                        label = "$title · R$roundId",
                        series = series,
                        round = roundId,
                        carClass = carClass,
                        trackName = null,
                        startMs = startMs,
                        endMs = endMs,
                        source = "polyphony-cdn",
                        fetchedAtMs = now,
                    )
                )
            }
        }
        return out
    }

    private fun mapCarClass(raw: String?): String? = when (raw) {
        "CAR_CLASS_GR1" -> "Gr.1"
        "CAR_CLASS_GR2" -> "Gr.2"
        "CAR_CLASS_GR3" -> "Gr.3"
        "CAR_CLASS_GR4" -> "Gr.4"
        "CAR_CLASS_GRB" -> "Gr.B"
        "CAR_CLASS_GRX" -> "Gr.X"
        "CAR_CLASS_GRN" -> "Gr.N"
        else -> null
    }
}