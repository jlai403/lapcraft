package io.github.jlai403.lapcraft.core.protocol

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecodeFailureLogTest {
    @Test
    fun `records at most five distinct failures`() {
        val log = DecodeFailureLog(limit = 5)
        repeat(5) { assertTrue(log.record(368, "decrypt-or-magic-$it")) }
        assertFalse(log.record(368, "decrypt-or-magic-5"))
    }

    @Test
    fun `duplicate failure is recorded once`() {
        val log = DecodeFailureLog()
        assertTrue(log.record(368, "decrypt-or-magic"))
        assertFalse(log.record(368, "decrypt-or-magic"))
    }
}
