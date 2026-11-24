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
        assertEquals("Manufacturers Cup 2026 - Exhibition 3 · R4", event.label)
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

    @Test
    fun `nations cup entry leads its label with Nations Cup`() {
        val json = """
            {"result":[{"championship_title_map":{"GB":"World Series 2026 - Exhibition 3"},
              "season_title_map":{"GB":"Nations Cup"},
              "season_id":1805,"series_id":22,
              "rounds":[{"round_id":1,"car_class":"CAR_CLASS_INVALID",
                        "start_date":"2026-11-29T01:00:00Z","end_date":"2026-11-29T05:59:59Z"}]}]}
        """.trimIndent()
        val event = PolyphonyCdnParser.parseActive(json).single()
        assertEquals("Nations Cup", event.series)
        assertEquals("Nations Cup 2026 - Exhibition 3 · R1", event.label)
    }

    @Test
    fun `legacy feed where the cup lives in the championship title`() {
        val json = """
            {"result":[{"championship_title_map":{"GB":"World Series - Manufacturers Cup"},
              "season_title_map":{"GB":"2023/24 Exhibition Series | Season 3"},
              "season_id":858,"series_id":10,
              "rounds":[{"round_id":1,"car_class":"CAR_CLASS_GR3",
                        "start_date":"2023-10-01T00:00:00Z","end_date":"2023-10-01T04:59:59Z"}]}]}
        """.trimIndent()
        val event = PolyphonyCdnParser.parseActive(json).single()
        assertEquals("Manufacturers Cup · 2023/24 Exhibition Series | Season 3 · R1", event.label)
    }

    @Test
    fun `entry without a cup keeps the feed title in the label`() {
        val json = """
            {"result":[{"championship_title_map":{"GB":"TOYOTA GAZOO Racing GT Cup"},
              "season_title_map":{"GB":"2024 Series"},
              "season_id":951,"series_id":12,
              "rounds":[{"round_id":2,"car_class":"CAR_CLASS_GR4",
                        "start_date":"2024-01-01T00:00:00Z","end_date":"2024-01-01T04:59:59Z"}]}]}
        """.trimIndent()
        val event = PolyphonyCdnParser.parseActive(json).single()
        assertEquals("TOYOTA GAZOO Racing GT Cup · R2", event.label)
    }
}
