package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object EventRefresh {
    suspend fun refresh(context: android.content.Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val cache = EventCache(context)
        val now = System.currentTimeMillis()
        // Polyphony CDN (no key)
        if (force || cache.isPolyphonyStale(now)) {
            runCatching {
                val conn = (URL("https://static.gt7.game.gran-turismo.com/championship/seasons/2/active.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = readBody(conn)
                    cache.replacePolyphony(PolyphonyCdnParser.parseActive(body), now)
                }
                conn.disconnect()
            }
        }
        val settings = SettingsStore(context)
        val key = DriverProfileStore(context).apiKey
        if (settings.eventTags && !key.isNullOrBlank() && (force || cache.isGridstatsStale(now))) {
            runCatching {
                val ownerPsn = DriverProfileStore(context).owner()?.psnId
                val thisWeek = fetch("/api/dailies/this-week", key)
                val nextWeek = fetch("/api/dailies/next-week", key)
                val ttLive = fetch("/api/tt-live", key)
                val ownerDaily = ownerPsn?.let { fetch("/api/daily-live/$it", key) }
                val ownerTt = ownerPsn?.let { fetch("/api/tt-live/$it", key) }
                val events = GridStatsEventParser.parseDailies(thisWeek, nextWeek, ownerPsn, ownerDaily) +
                    GridStatsEventParser.parseTimeTrials(ttLive, ownerPsn, ownerTt)
                cache.replaceGridstats(events, now)
            }
        }
    }

    private fun fetch(path: String, key: String?): String? {
        if (key.isNullOrBlank()) return null
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