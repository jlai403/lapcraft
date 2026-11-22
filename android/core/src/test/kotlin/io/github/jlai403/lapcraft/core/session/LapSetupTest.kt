package io.github.jlai403.lapcraft.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LapSetupTest {
    @Test
    fun `traction control reads as off at zero`() {
        assertNull(tcsLabel(null))
        assertEquals("TCS off", tcsLabel(0))
        assertEquals("TCS 3", tcsLabel(3))
    }

    @Test
    fun `brake balance keeps its sign`() {
        assertNull(brakeBalanceLabel(null))
        assertEquals("BB 0", brakeBalanceLabel(0))
        assertEquals("BB +2", brakeBalanceLabel(2))
        assertEquals("BB -3", brakeBalanceLabel(-3))
    }

    @Test
    fun `setup label joins only the values that are set`() {
        assertEquals("TCS 2 · BB +1", setupLabel(2, 1))
        assertEquals("TCS off", setupLabel(0, null))
        assertEquals("BB -1", setupLabel(null, -1))
        assertNull(setupLabel(null, null))
    }
}
