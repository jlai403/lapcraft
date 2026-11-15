package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.SessionWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class LapStoreTest {
    @TempDir
    lateinit var root: File

    @Test
    fun migratesOneLapSessionAndReadsItBack() {
        val sessions = File(root, "sessions").apply { mkdirs() }
        val sessionDir = File(sessions, "s1").apply { mkdirs() }
        File(sessionDir, "laps").mkdirs()
        val metadata = SessionMetadata(
            sessionId = "s1",
            createdAt = Instant.now().toString(),
            trackLabel = "Monza",
            carCode = 42,
            carName = "Car",
            rawFile = "",
            rawDeleted = true,
        )
        val writer = SessionWriter(sessionDir, metadata)
        val boundary = LapBoundary(lapNumber = 1, startNanos = 100L, endNanos = 200L, lapTimeMs = 90_000)
        writer.writeLap(boundary, videoFile = "", leadInMs = 0, driverPsnId = "psn")
        writer.finalize(rawDeleted = true)

        val store = LapStore(root)
        val ids = store.list()
        assertEquals(listOf(LapId("s1")), ids)
        val record = store.load(LapId("s1"))!!
        assertEquals("Monza", record.circuitLabel)
        assertEquals(42, record.carCode)
        assertEquals("psn", record.driverPsnId)
        assertEquals(90_000, record.lapTimeMs)
        assertTrue(!File(root, "sessions").exists() || File(root, "sessions.legacy-v1").exists())
        assertEquals(1, store.summaries().size)
    }

    @Test
    fun migratesMultiLapSessionIntoSeparateIds() {
        val sessions = File(root, "sessions").apply { mkdirs() }
        val sessionDir = File(sessions, "s2").apply { mkdirs() }
        File(sessionDir, "laps").mkdirs()
        val metadata = SessionMetadata(
            sessionId = "s2",
            createdAt = Instant.now().toString(),
            trackLabel = "Spa",
            carCode = 7,
            carName = "Car",
            rawFile = "",
            rawDeleted = true,
        )
        val writer = SessionWriter(sessionDir, metadata)
        writer.writeLap(LapBoundary(1, 10L, 20L, 60_000), videoFile = "", leadInMs = 0)
        writer.writeLap(LapBoundary(2, 20L, 30L, 61_000), videoFile = "", leadInMs = 0)
        writer.finalize(rawDeleted = true)

        val store = LapStore(root)
        val ids = store.list().map { it.value }.sorted()
        assertEquals(listOf("s2-l1", "s2-l2"), ids)
    }
}
