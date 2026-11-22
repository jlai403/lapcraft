package io.github.jlai403.lapcraft.app.events

import org.junit.Assert.assertEquals
import org.junit.Test

class PolyphonyRegionTest {
    @Test
    fun `the americas map to the north and south folders`() {
        assertEquals(PolyphonyRegion.NORTH_AMERICA, PolyphonyRegion.folder("CA"))
        assertEquals(PolyphonyRegion.NORTH_AMERICA, PolyphonyRegion.folder("us"))
        assertEquals(PolyphonyRegion.NORTH_AMERICA, PolyphonyRegion.folder("MX"))
        assertEquals(PolyphonyRegion.SOUTH_AMERICA, PolyphonyRegion.folder("BR"))
    }

    @Test
    fun `europe and asia-pacific map to their folders`() {
        assertEquals(PolyphonyRegion.EUROPE, PolyphonyRegion.folder("GB"))
        assertEquals(PolyphonyRegion.EUROPE, PolyphonyRegion.folder("DE"))
        assertEquals(PolyphonyRegion.ASIA, PolyphonyRegion.folder("JP"))
        assertEquals(PolyphonyRegion.OCEANIA, PolyphonyRegion.folder("AU"))
    }

    @Test
    fun `an unknown country falls back to europe`() {
        assertEquals(PolyphonyRegion.EUROPE, PolyphonyRegion.folder("ZZ"))
        assertEquals(PolyphonyRegion.EUROPE, PolyphonyRegion.folder(""))
    }
}
