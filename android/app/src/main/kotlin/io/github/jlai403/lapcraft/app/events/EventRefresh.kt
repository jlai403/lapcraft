package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.app.catalog.lapStore
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.events.CachedEvent
import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.repairedFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.zip.GZIPInputStream

data class RefreshResult(
    val polyphonyCount: Int = 0,
    val gridStatsCount: Int = 0,
    val polyphonyWritten: Boolean = false,
    val gridStatsWritten: Boolean = false,
    val errors: List<String> = emptyList(),
)

object EventRefresh {
    suspend fun refresh(
        context: android.content.Context,
        force: Boolean = false,
        fetchCdn: suspend (String) -> String? = { url -> httpGet(url) },
        fetchApi: suspend (String, String) -> String? = { path, key -> httpGetWithKey(path, key) },
        regionFolder: Int = PolyphonyRegion.folder(),
    ): RefreshResult = withContext(Dispatchers.IO) {
        val cache = EventCache(context)
        val settings = SettingsStore(context)
        val now = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        var polyphonyCount = 0
        var gridStatsCount = 0
        var polyphonyWritten = false
        var gridStatsWritten = false

        // A forced refresh fills the cache before the toggle is turned on; the
        // toggle only gates the background refresh.
        if ((force || settings.eventTags) && (force || cache.isPolyphonyStale(now, regionFolder))) {
            try {
                val url = "https://static.gt7.game.gran-turismo.com/championship/seasons/$regionFolder/active.json"
                val body = fetchCdn(url)
                if (body == null) {
                    errors += "CDN: unavailable"
                } else {
                    val events = PolyphonyCdnParser.parseActive(body)
                    cache.replacePolyphony(events, now, regionFolder)
                    repairChampionshipTags(context, events)
                    polyphonyWritten = true
                    polyphonyCount = events.size
                }
            } catch (e: Exception) {
                errors += "CDN: ${e.message ?: "failed"}"
            }
        }

        val key = DriverProfileStore(context).apiKey
        if ((force || settings.eventTags) && !key.isNullOrBlank() && (force || cache.isGridstatsStale(now))) {
            try {
                val ownerPsn = DriverProfileStore(context).owner()?.psnId
                val thisWeek = fetchApi("/api/dailies/this-week", key)
                val nextWeek = fetchApi("/api/dailies/next-week", key)
                val ttLive = fetchApi("/api/tt-live", key)
                val ownerDaily = ownerPsn?.let { fetchApi("/api/daily-live/$it", key) }
                val ownerTt = ownerPsn?.let { fetchApi("/api/tt-live/$it", key) }
                val anySuccess = listOf(thisWeek, nextWeek, ttLive).any { it != null }
                val events = GridStatsEventParser.parseDailies(thisWeek, nextWeek, ownerPsn, ownerDaily) +
                    GridStatsEventParser.parseTimeTrials(ttLive, ownerPsn, ownerTt)
                if (anySuccess) {
                    cache.replaceGridstats(events, now)
                    gridStatsWritten = true
                    gridStatsCount = events.size
                } else {
                    errors += "GridStats: unavailable"
                }
            } catch (e: Exception) {
                errors += "GridStats: ${e.message ?: "failed"}"
            }
        }
        RefreshResult(
            polyphonyCount = polyphonyCount,
            gridStatsCount = gridStatsCount,
            polyphonyWritten = polyphonyWritten,
            gridStatsWritten = gridStatsWritten,
            errors = errors,
        )
    }

    /**
     * A region change moves the championship to another season id, so auto tags written
     * against the old folder would otherwise stay lapsed. Rematch them by series, round
     * and capture time.
     */
    private fun repairChampionshipTags(context: android.content.Context, events: List<CachedEvent>) {
        if (events.none { it.kind == EventKind.Championship }) return
        val store = lapStore(context)
        store.summaries().forEach { lap ->
            val tag = lap.event ?: return@forEach
            val createdAtMs = runCatching { Instant.parse(lap.createdAt).toEpochMilli() }.getOrNull() ?: return@forEach
            val repaired = tag.repairedFrom(events, createdAtMs) ?: return@forEach
            store.setEvent(lap.lapId, repaired)
        }
    }

    private suspend fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode == 200) readBody(conn) else null
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun httpGetWithKey(path: String, key: String): String? {
        val conn = (URL("https://gt-gridstats.com$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("User-Agent", "LapCraft/0.1 (+https://github.com/jlai403/lapcraft)")
        }
        return try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> readBody(conn)
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> null
            }
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.contentEncoding == "gzip") GZIPInputStream(conn.inputStream) else conn.inputStream
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

}