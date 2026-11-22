package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.tire.TyreCompound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LapStoreTest {
    @TempDir
    lateinit var root: File

    private val id = LapId("lap-1")

    private fun saveEmpty() = LapStore(root).save(
        id,
        LapRecord(capturedAt = "2026-11-22T10:00:00Z", lapTimeMs = 90_000),
    )

    @Test
    fun `setup metadata round trips through lap json`() {
        saveEmpty()
        val store = LapStore(root)
        store.setTyre(id, TyreCompound.RACING_SOFT)
        store.setTcs(id, 2)
        store.setBrakeBalance(id, -1)

        val loaded = LapStore(root).load(id)!!
        assertEquals(TyreCompound.RACING_SOFT, loaded.tyre)
        assertEquals(2, loaded.tcs)
        assertEquals(-1, loaded.brakeBalance)
    }

    @Test
    fun `setup values clamp to their ranges`() {
        saveEmpty()
        val store = LapStore(root)
        store.setTcs(id, 9)
        store.setBrakeBalance(id, -9)

        val loaded = LapStore(root).load(id)!!
        assertEquals(5, loaded.tcs)
        assertEquals(-5, loaded.brakeBalance)
    }

    @Test
    fun `clearing setup metadata writes nulls`() {
        saveEmpty()
        val store = LapStore(root)
        store.setTcs(id, 3)
        store.setBrakeBalance(id, 2)
        store.setTcs(id, null)
        store.setBrakeBalance(id, null)

        val loaded = LapStore(root).load(id)!!
        assertNull(loaded.tcs)
        assertNull(loaded.brakeBalance)
    }

    @Test
    fun `removeReplay deletes the clip but keeps telemetry and metadata`() {
        saveEmpty()
        val store = LapStore(root)
        store.setTyre(id, TyreCompound.RACING_SOFT)
        store.setTcs(id, 2)
        val dir = File(root, "laps/${id.value}")
        dir.mkdirs()
        File(dir, "replay.mp4").writeText("clip")
        File(dir, "source.mp4").writeText("source")
        File(dir, "replay.mp4.tmp").writeText("tmp")
        File(dir, "lap.gt7lap").writeText("telemetry")
        File(dir, "leadInMs").writeText("") // placeholder, ignored

        val before = store.load(id)!!
        store.save(id, before.copy(leadInMs = 120))

        assertTrue(store.removeReplay(id))

        val after = store.load(id)!!
        assertEquals(0, after.leadInMs)
        assertEquals(TyreCompound.RACING_SOFT, after.tyre)
        assertEquals(2, after.tcs)
        assertTrue(!File(dir, "replay.mp4").exists())
        assertTrue(!File(dir, "source.mp4").exists())
        assertTrue(!File(dir, "replay.mp4.tmp").exists())
        assertTrue(File(dir, "lap.gt7lap").exists())
        assertTrue(File(dir, "lap.json").exists())
        assertFalse(store.summaries().single().hasReplay)
    }

    @Test
    fun `removeReplay returns false when there is no clip`() {
        saveEmpty()
        val store = LapStore(root)
        assertFalse(store.removeReplay(id))
        assertTrue(File(root, "laps/${id.value}/lap.json").exists())
    }

    @Test
    fun `summaries carry the setup metadata`() {
        saveEmpty()
        val store = LapStore(root)
        store.setTyre(id, TyreCompound.RACING_MEDIUM)
        store.setTcs(id, 4)
        store.setBrakeBalance(id, 1)

        val summary = store.summaries().single()
        assertEquals(TyreCompound.RACING_MEDIUM, summary.tyre)
        assertEquals(4, summary.tcs)
        assertEquals(1, summary.brakeBalance)
    }
}
