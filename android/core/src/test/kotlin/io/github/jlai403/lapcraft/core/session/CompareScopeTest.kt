package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.events.EventKind
import io.github.jlai403.lapcraft.core.events.EventTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompareScopeTest {
    private fun lap(
        id: String,
        timeMs: Int = 90_000,
        track: String = "Suzuka",
        event: EventTag? = null,
    ) = LapSummary(
        lapId = LapId(id),
        lapTimeMs = timeMs,
        createdAt = "2026-11-17T10:00:00Z",
        trackLabel = track,
        carName = "BRZ GT300 '21",
        hasReplay = true,
        replayPending = false,
        event = event,
    )

    private val dailyA = EventTag(id = "daily-Daily Race A-2026-11-16", kind = EventKind.Daily, label = "Daily Race A · W47")
    private val dailyB = EventTag(id = "daily-Daily Race B-2026-11-16", kind = EventKind.Daily, label = "Daily Race B · W47")

    @Test
    fun `a reference needs the same track and event`() {
        val primary = lap("primary", event = dailyA)
        assertTrue(lap("same", event = dailyA).canCompareWith(primary))
        assertFalse(lap("other-event", event = dailyB).canCompareWith(primary))
        assertFalse(lap("untagged").canCompareWith(primary))
        assertFalse(lap("other-track", track = "Monza", event = dailyA).canCompareWith(primary))
    }

    @Test
    fun `an untagged primary still compares across the same track`() {
        val primary = lap("primary")
        assertTrue(lap("same").canCompareWith(primary))
        assertFalse(lap("other-track", track = "Monza").canCompareWith(primary))
    }

    @Test
    fun `manual tags compare by their normalised label`() {
        val primary = lap("primary", event = EventTag("manual-1", EventKind.Championship, "World Series · R3"))
        val same = lap("same", event = EventTag("manual-2", EventKind.Championship, "world series · r3"))
        val other = lap("other", event = EventTag("manual-3", EventKind.Championship, "World Series · R4"))

        assertTrue(same.canCompareWith(primary))
        assertFalse(other.canCompareWith(primary))
    }

    @Test
    fun `compare candidates drop the primary and keep the scoped laps`() {
        val primary = lap("primary", event = dailyA)
        val laps = listOf(primary, lap("same", timeMs = 91_000, event = dailyA), lap("other", event = dailyB))

        assertEquals(listOf(LapId("same")), laps.compareCandidates(primary).map { it.lapId })
    }
}
