package io.github.jlai403.lapcraft.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TrackGroupsTest {
    private fun lap(id: String, track: String, timeMs: Int, createdAt: String = "2026-11-16T10:00:00Z") =
        LapSummary(
            lapId = LapId(id),
            lapTimeMs = timeMs,
            createdAt = createdAt,
            trackLabel = track,
            carName = "BRZ GT300 '21",
            hasReplay = true,
            replayPending = false,
        )

    @Test
    fun `groups laps by track`() {
        val groups = listOf(
            lap("a", "Suzuka", 90_000),
            lap("b", "Suzuka", 91_000),
            lap("c", "Nurburgring", 120_000),
        ).trackGroups()
        assertEquals(2, groups.size)
        assertEquals("Suzuka", groups[0].label)
        assertEquals(2, groups[0].laps.size)
        assertEquals(90_000, groups[0].bestLapMs)
        assertEquals("Nurburgring", groups[1].label)
    }

    @Test
    fun `orders groups by newest lap`() {
        val groups = listOf(
            lap("a", "Suzuka", 90_000, createdAt = "2026-11-10T10:00:00Z"),
            lap("b", "Nurburgring", 120_000, createdAt = "2026-11-16T10:00:00Z"),
        ).trackGroups()
        assertEquals("Nurburgring", groups[0].label)
        assertEquals("Suzuka", groups[1].label)
    }

    @Test
    fun `unknown tracks sort last`() {
        val groups = listOf(
            lap("a", "Unknown", 90_000, createdAt = "2026-11-16T10:00:00Z"),
            lap("b", "Suzuka", 91_000, createdAt = "2026-11-10T10:00:00Z"),
        ).trackGroups(isUnknown = { it.trackLabel == "Unknown" })
        assertEquals("Suzuka", groups[0].label)
        assertEquals("Unknown", groups[1].label)
        assertEquals(null, groups[1].bestLapMs)
    }

    @Test
    fun `empty input yields no groups`() {
        assertTrue(listOf<LapSummary>().trackGroups().isEmpty())
    }
}