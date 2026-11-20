package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverRatingTest {
    private val now = 1_763_000_000_000L

    @Test
    fun `rating label joins DR and SR`() {
        val profile = DriverProfile("skino2024", "Skino", "GB", dr = "B", sr = "S")

        assertEquals("DR (B) · SR (S)", profile.ratingLabel())
    }

    @Test
    fun `an unknown rating has no label`() {
        assertNull(DriverProfile("skino2024", "Skino", "GB").ratingLabel())
    }

    @Test
    fun `the label carries the fetch date`() {
        val profile = DriverProfile("skino2024", "Skino", "GB", dr = "B", sr = "S", fetchedAtMs = now)

        assertTrue(profile.ratingWithAgeLabel()!!.startsWith("DR (B) · SR (S) · updated "))
    }

    @Test
    fun `a never fetched profile needs a refresh`() {
        assertTrue(DriverProfile("skino2024", "Skino", "GB").needsRefresh(now, expectRating = false))
    }

    @Test
    fun `a fresh rating does not need a refresh`() {
        val profile = DriverProfile("skino2024", "Skino", "GB", dr = "B", sr = "S", fetchedAtMs = now)

        assertFalse(profile.needsRefresh(now, expectRating = true))
    }

    @Test
    fun `a week old rating needs a refresh`() {
        val profile = DriverProfile(
            "skino2024",
            "Skino",
            "GB",
            dr = "B",
            sr = "S",
            fetchedAtMs = now - RATING_MAX_AGE_MS - 1,
        )

        assertTrue(profile.needsRefresh(now, expectRating = true))
    }

    @Test
    fun `a missing rating needs a refresh only when a key can supply one`() {
        val keylessGap = DriverProfile("skino2024", "Skino", "GB", fetchedAtMs = now)

        assertFalse(keylessGap.needsRefresh(now, expectRating = false))
        assertTrue(keylessGap.needsRefresh(now, expectRating = true))
    }
}
