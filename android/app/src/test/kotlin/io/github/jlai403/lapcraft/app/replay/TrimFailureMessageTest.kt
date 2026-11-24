package io.github.jlai403.lapcraft.app.replay

import org.junit.Assert.assertTrue
import org.junit.Test

class TrimFailureMessageTest {
    @Test
    fun everyReasonProducesHadClipSuffix() {
        TrimFailureReason.entries.forEach { reason ->
            val msg = trimFailureMessage(reason, hadClip = true)
            assertTrue(msg.endsWith("The current replay is unchanged; try again."))
        }
    }

    @Test
    fun everyReasonProducesNoClipSuffix() {
        TrimFailureReason.entries.forEach { reason ->
            val msg = trimFailureMessage(reason, hadClip = false)
            assertTrue(msg.endsWith("No replay was attached; try again."))
        }
    }

    @Test
    fun noSpaceMessageIsSpecific() {
        val msg = trimFailureMessage(TrimFailureReason.NoSpace, hadClip = false)
        assertTrue(msg.contains("Not enough free space"))
    }
}