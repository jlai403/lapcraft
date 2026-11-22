package io.github.jlai403.lapcraft.app.events

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PolyphonyCdnParserTest {
    private val northAmerica = """
        {"result":[
          {"championship_title_map":{"GB":"World Series 2026 - Exhibition 3"},
           "season_title_map":{"GB":"Manufacturers Cup"},
           "season_id":1801,"series_id":22,
           "rounds":[{"round_id":4,"car_class":"CAR_CLASS_GR3",
                      "start_date":"2026-11-22T00:00:00Z","end_date":"2026-11-22T04:59:59Z"}]},
          {"championship_title_map":{"GB":"World Series 2026 - Exhibition 3"},
           "season_title_map":{"GB":"Manufacturers Cup"},
           "season_id":1801,"series_id":22,
           "rounds":[{"round_id":4,"car_class":"CAR_CLASS_GR3",
                      "start_date":"2026-11-21T15:00:00Z","end_date":"2026-11-22T04:59:59Z"}]}
        ]}
    """.trimIndent()

    private fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `league entries for one round merge into the widest window`() {
        val event = PolyphonyCdnParser.parseActive(northAmerica).single()

        assertEquals("champ-22-1801-r4", event.id)
        assertEquals("Manufacturers Cup", event.series)
        assertEquals(4, event.round)
        assertEquals("Gr.3", event.carClass)
        assertEquals(epoch("2026-11-21T15:00:00Z"), event.startMs)
        assertEquals(epoch("2026-11-22T04:59:59Z"), event.endMs)
        assertEquals("World Series 2026 - Exhibition 3 · R4", event.label)
    }

    @Test
    fun `a round without a start date is skipped`() {
        val json = """
            {"result":[{"championship_title_map":{"GB":"World Series"},
              "season_id":1801,"series_id":22,
              "rounds":[{"round_id":1,"end_date":"2026-11-21T21:59:59Z"}]}]}
        """.trimIndent()

        assertEquals(0, PolyphonyCdnParser.parseActive(json).size)
    }
}
