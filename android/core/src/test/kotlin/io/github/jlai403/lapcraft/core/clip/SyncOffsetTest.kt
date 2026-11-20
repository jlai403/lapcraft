package io.github.jlai403.lapcraft.core.clip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SyncOffsetTest {
    @Test
    fun `parses seconds and milliseconds`() {
        assertEquals(19L, SyncOffset.parse("0.019"))
        assertEquals(19L, SyncOffset.parse(".019"))
        assertEquals(1_234L, SyncOffset.parse("1.234"))
    }

    @Test
    fun `parses the HUD shape`() {
        assertEquals(19L, SyncOffset.parse("0:00.019"))
        assertEquals(12_345L, SyncOffset.parse("0:12.345"))
    }

    @Test
    fun `rejects bare integers and bad input`() {
        assertNull(SyncOffset.parse("19"))
        assertNull(SyncOffset.parse(""))
        assertEquals(10L, SyncOffset.parse("0.01"))
        assertNull(SyncOffset.parse("0.1234"))
        assertNull(SyncOffset.parse("-0.001"))
    }
}