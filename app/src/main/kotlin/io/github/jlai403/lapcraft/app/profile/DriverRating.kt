package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stampFormat = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

/** "DR B · SR S", or null when GT GridStats never returned a rating. */
fun DriverProfile.ratingLabel(): String? =
    listOfNotNull(dr?.let { "DR $it" }, sr?.let { "SR $it" }).joinToString(" · ").ifEmpty { null }

/** "DR B · SR S · updated Nov 3", so a stale rating is visible. */
fun DriverProfile.ratingWithAgeLabel(): String? {
    val rating = ratingLabel() ?: return null
    val stamp = fetchedAtMs ?: return rating
    return "$rating · updated ${stampFormat.format(Instant.ofEpochMilli(stamp))}"
}

/** True when the cached rating is missing or stale; [expectRating] is set when an API key can supply one. */
fun DriverProfile.needsRefresh(
    nowMs: Long,
    expectRating: Boolean,
    maxAgeMs: Long = RATING_MAX_AGE_MS,
): Boolean {
    val stamp = fetchedAtMs ?: return true
    if (expectRating && dr == null && sr == null) return true
    return nowMs - stamp > maxAgeMs
}

const val RATING_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
