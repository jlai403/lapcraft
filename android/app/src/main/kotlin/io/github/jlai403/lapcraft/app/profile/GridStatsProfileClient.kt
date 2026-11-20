package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ProfileNotFoundException : IOException("No GT GridStats profile found for that PSN ID.")

class GridStatsProfileClient(
    private val apiKey: () -> String? = { null },
) {
    suspend fun fetch(psnId: String): DriverProfile = withContext(Dispatchers.IO) {
        val trimmed = psnId.trim()
        require(trimmed.isNotEmpty()) { "Enter a PSN ID." }
        val key = apiKey()
        val profile = if (key.isNullOrBlank()) fetchHtml(trimmed) else fetchApi(trimmed, key)
        profile.copy(fetchedAtMs = System.currentTimeMillis())
    }

    private fun fetchApi(psnId: String, key: String): DriverProfile {
        val encoded = URLEncoder.encode(psnId, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val connection = (URL("https://gt-gridstats.com/api/racers/$encoded").openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("User-Agent", USER_AGENT)
            }
        return try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseApiProfile(body, psnId)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> throw ProfileNotFoundException()
                401 -> throw IOException("GT GridStats API key was rejected.")
                429 -> throw IOException("GT GridStats rate limit hit; try again in a minute.")
                422 -> throw IOException("GT GridStats rejected the request.")
                else -> throw IOException("GT GridStats returned HTTP $status.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchHtml(psnId: String): DriverProfile {
        val connection = (URL(DriverProfileLinks.gridStats(psnId)).openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "text/html")
                setRequestProperty("User-Agent", USER_AGENT)
            }
        return try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseGridStatsProfile(html, psnId)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> throw ProfileNotFoundException()
                else -> throw IOException("GT GridStats returned HTTP $status.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val USER_AGENT = "LapCraft/0.1 (+https://github.com/jlai403/lapcraft)"
    }
}

@Serializable
private data class RacersResponse(
    val drivers: List<RacerDto> = emptyList(),
    @SerialName("not_found") val notFound: List<String> = emptyList(),
)

@Serializable
private data class RacerDto(
    @SerialName("PSN_ID") val psnId: String = "",
    @SerialName("Nickname") val nickname: String = "",
    @SerialName("DR") val dr: String? = null,
    @SerialName("SR") val sr: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)

internal fun parseApiProfile(jsonText: String, psnId: String): DriverProfile {
    val json = Json { ignoreUnknownKeys = true }
    val response = json.decodeFromString<RacersResponse>(jsonText)
    val dto = response.drivers.firstOrNull { it.psnId.equals(psnId.trim(), ignoreCase = true) }
        ?: throw ProfileNotFoundException()
    val nickname = dto.nickname.trim().ifEmpty { psnId.trim() }
    return DriverProfile(
        psnId = psnId.trim(),
        nickname = nickname,
        countryCode = dto.countryCode?.trim()?.uppercase(),
        dr = dto.dr?.trim()?.uppercase(),
        sr = dto.sr?.trim()?.uppercase(),
    )
}

private val NAME_PATTERN = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL)
private val FLAG_PATTERN = Regex("""flagcdn\.com/w20/([A-Za-z]{2})\.png""")
private val TAG_PATTERN = Regex("<[^>]+>")
private val ENTITY_PATTERN = Regex("&(#x[0-9A-Fa-f]+|#[0-9]+|[A-Za-z]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "apos" to "'",
    "gt" to ">",
    "lt" to "<",
    "quot" to "\"",
)

internal fun parseGridStatsProfile(html: String, psnId: String): DriverProfile {
    val rawName = NAME_PATTERN.find(html)?.groupValues?.getOrNull(1)
        ?: throw IOException("GT GridStats profile page did not include a name.")
    val nickname = unescapeHtml(rawName.replace(TAG_PATTERN, "")).trim()
    if (nickname.isEmpty()) throw IOException("GT GridStats profile page did not include a name.")
    val countryCode = FLAG_PATTERN.find(html)?.groupValues?.getOrNull(1)?.uppercase()
    return DriverProfile(psnId = psnId.trim(), nickname = nickname, countryCode = countryCode)
}

private fun unescapeHtml(text: String): String = ENTITY_PATTERN.replace(text) { match ->
    val entity = match.groupValues[1]
    val decoded = when {
        entity.startsWith("#x", ignoreCase = true) -> numericEntity(entity.drop(2).toIntOrNull(16))
        entity.startsWith("#") -> numericEntity(entity.drop(1).toIntOrNull())
        else -> NAMED_ENTITIES[entity]
    }
    decoded ?: match.value
}

private fun numericEntity(codePoint: Int?): String? =
    codePoint?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) }