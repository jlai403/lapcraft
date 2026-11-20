package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GridStatsApiParserTest {
    @Test
    fun `parses DR SR and country from racers response`() {
        val json = """
            {"status":"complete","count":1,"not_found":[],
             "drivers":[{"PSN_ID":"skino2024","Nickname":"Skino","DR":"B","SR":"S",
                         "country_code":"gb"}]}
        """.trimIndent()
        val profile = parseApiProfile(json, "skino2024")
        assertEquals("skino2024", profile.psnId)
        assertEquals("Skino", profile.nickname)
        assertEquals("GB", profile.countryCode)
        assertEquals("B", profile.dr)
        assertEquals("S", profile.sr)
    }

    @Test
    fun `throws when psn missing`() {
        val json = """{"status":"complete","count":0,"not_found":["nope"],"drivers":[]}"""
        assertThrows(ProfileNotFoundException::class.java) {
            parseApiProfile(json, "nope")
        }
    }
}