package io.github.jlai403.lapcraft.app.events

import io.github.jlai403.lapcraft.core.events.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridStatsEventParserTest {
    private val dailies = """
        {"data":[
          {"race_type":"A","track_name":"Suzuka Circuit","begin_date":"2026-11-16 07:00:00",
           "end_date":"2026-11-23 07:00:00","car_class":"Gr.4"},
          {"race_type":"B","track_name":"Deep Forest Raceway","begin_date":"2026-11-16 07:00:00",
           "end_date":"2026-11-23 07:00:00","car_class":"Gr.3"}
        ]}
    """.trimIndent()

    private val timeTrials = """
        {"data":[
          {"event_id":901,"title":"Time Trial","track_name":"Suzuka Circuit",
           "begin_date":"2026-11-16 07:00:00","end_date":"2026-11-23 07:00:00"},
          {"event_id":902,"title":"Time Trial","track":"Brands Hatch",
           "begin_date":"2026-11-16 07:00:00","end_date":"2026-11-23 07:00:00"}
        ]}
    """.trimIndent()

    @Test
    fun `daily races carry their ISO week`() {
        val events = GridStatsEventParser.parseDailies(dailies, null, null, null)

        assertEquals(listOf("Daily Race A · W47", "Daily Race B · W47"), events.map { it.label })
        assertEquals(listOf(EventKind.Daily, EventKind.Daily), events.map { it.kind })
        assertEquals("Suzuka Circuit", events.first().trackName)
    }

    @Test
    fun `daily ids ignore the week so a refreshed cache does not churn`() {
        val events = GridStatsEventParser.parseDailies(dailies, null, null, null)

        assertEquals("daily-Daily Race A-2026-11-16 07:00:00", events.first().id)
    }

    @Test
    fun `time trials are labelled by track so they can be told apart`() {
        val events = GridStatsEventParser.parseTimeTrials(timeTrials, null, null)

        assertEquals(
            listOf("Time Trial · Suzuka Circuit", "Time Trial · Brands Hatch"),
            events.map { it.label },
        )
    }

    @Test
    fun `a time trial without a track keeps the title`() {
        val json = """
            {"data":[{"event_id":903,"title":"Time Trial 3",
                      "begin_date":"2026-11-16 07:00:00"}]}
        """.trimIndent()

        assertEquals("Time Trial 3", GridStatsEventParser.parseTimeTrials(json, null, null).single().label)
    }

    @Test
    fun `owner car is attached to the matching daily`() {
        val ownerDaily = """
            {"results":[{"race_type":"A","track_name":"Suzuka Circuit","car_name":"MAZDA3 Gr.4"}]}
        """.trimIndent()

        val events = GridStatsEventParser.parseDailies(dailies, null, "skino2024", ownerDaily)

        assertEquals("MAZDA3 Gr.4", events.first().ownerCarName)
        assertNull(events.last().ownerCarName)
    }
}
