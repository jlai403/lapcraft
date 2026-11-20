package io.github.jlai403.lapcraft.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarCatalogCodecTest {
    @Test
    fun `decodes cars into a code map`() {
        val text = """
            {"source":"x","fetched_at":"2026-01-01","cars":[
              {"code":3474,"name":"SUBARU BRZ GT300 '21","class":"Gr.3","manufacturer":"SUBARU"},
              {"code":1544,"name":"Ferrari 430 Scuderia '07","class":"Gr.N","manufacturer":"Ferrari"}
            ]}
        """.trimIndent()
        val map = CarCatalogCodec.decode(text)
        assertEquals(2, map.size)
        assertEquals("SUBARU BRZ GT300 '21", map[3474]?.name)
        assertEquals("Gr.3", map[3474]?.carClass)
        assertEquals("SUBARU", map[3474]?.manufacturer)
    }

    @Test
    fun `returns empty map on garbage`() {
        assertEquals(emptyMap<Int, CarInfo>(), CarCatalogCodec.decode("not json"))
    }

    @Test
    fun `resolve returns null for unknown`() {
        val map = CarCatalogCodec.decode("""{"cars":[{"code":1,"name":"A"}]}""")
        assertNull(CarCatalogCodec.resolve(999, map))
        assertEquals("A", CarCatalogCodec.resolve(1, map))
    }
}