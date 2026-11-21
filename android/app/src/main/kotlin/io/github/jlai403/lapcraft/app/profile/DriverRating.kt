package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stampFormat = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

/** "DR (B) · SR (S)", or null when GT GridStats never returned a rating. */
fun DriverProfile.ratingLabel(): String? =
    listOfNotNull(dr?.let { "DR ($it)" }, sr?.let { "SR ($it)" }).joinToString(" · ").ifEmpty { null }

/** "DR (B) · SR (S) · updated 2 h ago", so a stale rating is visible. */
fun DriverProfile.ratingWithAgeLabel(nowMs: Long = System.currentTimeMillis()): String? {
    val rating = ratingLabel() ?: return null
    val stamp = fetchedAtMs ?: return rating
    val ageMs = (nowMs - stamp).coerceAtLeast(0L)
    val ageText = when {
        ageMs < 60_000 -> "just now"
        ageMs < 60 * 60_000 -> "${ageMs / 60_000} min ago"
        ageMs < 24 * 60 * 60_000 -> "${ageMs / (60 * 60_000)} h ago"
        else -> stampFormat.format(Instant.ofEpochMilli(stamp))
    }
    return "$rating · updated $ageText"
}

/** True when the cached rating is missing or stale; [expectRating] is set when an API key can supply one. */
fun DriverProfile.needsRefresh(
    nowMs: Long,
    expectRating: Boolean,
    maxAgeMs: Long = RATING_MAX_AGE_MS,
): Boolean {
    val stamp = fetchedAtMs ?: return true
    if (notFound) return nowMs - stamp > 24L * 60 * 60 * 1000
    if (expectRating && dr == null && sr == null) return true
    return nowMs - stamp > maxAgeMs
}

const val RATING_MAX_AGE_MS: Long = 1L * 60 * 60 * 1000
