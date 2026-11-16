package io.github.jlai403.lapcraft.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LapSummaryTest {
    @TempDir
    lateinit var temp: File

    private fun install(
        id: String = "s1",
        videoFile: String = "",
        createFile: Boolean = false,
        lapTimeMs: Int = 90_000,
        createdAt: String = "2026-11-11T10:00:00Z",
        trackLabel: String = "Suzuka",
        driverPsnId: String? = null,
    ): SessionStore {
        val store = SessionStore(temp)
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = lapTimeMs,
            startNanos = 0,
            endNanos = lapTimeMs * 1_000_000L,
            sampleCount = 1,
            videoFile = videoFile,
            leadInMs = 0,
            driverPsnId = driverPsnId,
        )
        store.save(
            SessionMetadata(
                sessionId = id,
                createdAt = createdAt,
                trackLabel = trackLabel,
                carCode = 3474,
                carName = "Car #3474",
                rawFile = "",
                laps = listOf(lap),
            ),
        )
        if (createFile && videoFile.isNotBlank()) {
            File(store.sessionDir(id), videoFile).apply {
                parentFile?.mkdirs()
                writeText("x")
            }
        }
        return store
    }

    @Test
    fun `summaries carry the lap driver`() {
        val summary = install(driverPsnId = "skino2024").summaries().single()
        assertEquals("skino2024", summary.driverPsnId)
    }

    @Test
    fun `a clip on disk marks the lap as having a replay`() {
        val summary = install(videoFile = "laps/lap-001.mp4", createFile = true).summaries().single()
        assertTrue(summary.hasReplay)
    }

    @Test
    fun `summaries carry replay and telemetry sizes`() {
        val store = install(videoFile = "laps/lap-001.mp4", createFile = true)
        File(store.sessionDir("s1"), "laps/lap-001.gt7lap").writeBytes(ByteArray(2048))

        val summary = store.summaries().single()

        assertEquals(1L, summary.videoBytes)
        assertEquals(2048L, summary.telemetryBytes)
    }

    @Test
    fun `legacy lap without a clip stays visible`() {
        val summary = install(videoFile = "laps/lap-001.mp4", createFile = false).summaries().single()
        assertFalse(summary.hasReplay)
    }

    @Test
    fun `deleteLap removes the session when the last lap goes`() {
        val store = install()
        assertTrue(store.deleteLap("s1", 1))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `shared legacy raw is not a replay and survives deleting one lap`() {
        val store = SessionStore(temp)
        val raw = "raw.mp4"
        val laps = (1..2).map { index ->
            LapMetadata(
                index = index,
                lapTimeMs = 90_000 + index,
                startNanos = (index - 1) * 100_000_000_000L,
                endNanos = index * 100_000_000_000L,
                sampleCount = 1,
                videoFile = raw,
                leadInMs = 0,
                needsCut = true,
            )
        }
        store.save(
            SessionMetadata(
                sessionId = "legacy",
                createdAt = "2026-11-10T10:00:00Z",
                trackLabel = "Suzuka",
                carCode = 3474,
                carName = "Car #3474",
                rawFile = raw,
                laps = laps,
            ),
        )
        File(store.sessionDir("legacy"), raw).writeText("raw")

        assertEquals(2, store.summaries().size)
        assertTrue(store.summaries().none { it.hasReplay })

        assertTrue(store.deleteLap("legacy", 1))
        assertTrue(File(store.sessionDir("legacy"), raw).isFile)
        assertEquals(1, store.summaries().size)

        assertTrue(store.deleteLap("legacy", 2))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `attachClip clears the source and sets the playback offset`() {
        val store = install()
        val source = "laps/lap-001-source.mp4"
        File(store.sessionDir("s1"), source).apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        store.setSource("s1", 1, source)

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        val lap = store.lap("s1", 1)!!
        assertEquals("laps/lap-001.mp4", lap.videoFile)
        assertEquals(1_234, lap.leadInMs)
        assertNull(lap.sourceFile)
        assertFalse(File(store.sessionDir("s1"), source).exists())
    }

    @Test
    fun `attachClip resets the sync offset from the previous clip`() {
        val store = install()
        store.setSyncOffset("s1", 250)

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        assertEquals(0, store.lap("s1", 1)!!.syncOffsetMs)
    }

    @Test
    fun `attachClip deletes the clip it supersedes`() {
        val store = install(videoFile = "laps/lap-001-old.mp4", createFile = true)

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        assertFalse(File(store.sessionDir("s1"), "laps/lap-001-old.mp4").exists())
    }

    @Test
    fun `attachClip clears the preview cache`() {
        val store = install(videoFile = "laps/lap-001.mp4", createFile = true)
        val preview = File(store.sessionDir("s1"), "laps/lap-001.preview").apply {
            mkdirs()
            File(this, "00000.jpg").writeText("x")
        }

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        assertFalse(preview.exists())
    }

    @Test
    fun `filters by replay and track`() {
        install(id = "s1", trackLabel = "Suzuka")
        install(
            id = "s2",
            trackLabel = "Nurburgring",
            videoFile = "laps/lap-001.mp4",
            createFile = true,
        )
        val laps = install().summaries()
        assertEquals(2, laps.size)

        val withReplay = laps.applyFilter(LapFilter(hasReplay = true))
        assertEquals(listOf("s2"), withReplay.map { it.lapId.value })

        val suzuka = laps.applyFilter(LapFilter(trackKey = "suzuka"))
        assertEquals(listOf("s1"), suzuka.map { it.lapId.value })
    }

    @Test
    fun `sorts newest fastest and by track`() {
        install(id = "old", lapTimeMs = 95_000, createdAt = "2026-11-10T10:00:00Z", trackLabel = "A")
        install(id = "fast", lapTimeMs = 80_000, createdAt = "2026-11-11T10:00:00Z", trackLabel = "B")

        val laps = SessionStore(temp).summaries()
        assertEquals(listOf("fast", "old"), laps.applySort(LapSort.Newest).map { it.lapId.value })
        assertEquals(listOf("fast", "old"), laps.applySort(LapSort.Fastest).map { it.lapId.value })
        assertEquals(listOf("old", "fast"), laps.applySort(LapSort.Track).map { it.lapId.value })
    }

    @Test
    fun `fastest per track picks the quickest lap on each track`() {
        val laps = listOf(
            summary("a", 91_500, "Suzuka", "2026-11-11T10:00:00Z"),
            summary("a", 90_000, "Suzuka", "2026-11-11T10:05:00Z"),
            summary("b", 80_000, "Monza", "2026-11-10T10:00:00Z"),
        )

        val fastest = laps.fastestPerTrack()

        assertEquals(90_000, fastest.getValue("suzuka").lapTimeMs)
        assertEquals(80_000, fastest.getValue("monza").lapTimeMs)
    }

    @Test
    fun `fastest per track returns a lone lap for its track`() {
        val fastest = listOf(summary("a", 90_000, "Suzuka", "2026-11-11T10:00:00Z")).fastestPerTrack()

        assertEquals(1, fastest.size)
        assertEquals("a", fastest.getValue("suzuka").lapId.value)
        assertEquals(90_000, fastest.getValue("suzuka").lapTimeMs)
    }

    @Test
    fun `fastest per track breaks ties by earlier capture`() {
        val laps = listOf(
            summary("later", 90_000, "Suzuka", "2026-11-11T10:00:00Z"),
            summary("earlier", 90_000, "Suzuka", "2026-11-10T10:00:00Z"),
        )

        assertEquals("earlier", laps.fastestPerTrack().getValue("suzuka").lapId.value)
    }

    @Test
    fun `fastest per track ignores label case and padding`() {
        val laps = listOf(
            summary("a", 91_500, " Suzuka ", "2026-11-11T10:00:00Z"),
            summary("b", 90_000, "suzuka", "2026-11-11T10:00:00Z"),
        )

        val fastest = laps.fastestPerTrack()

        assertEquals(1, fastest.size)
        assertEquals("b", fastest.getValue("suzuka").lapId.value)
    }

    @Test
    fun `track filter ignores label case and padding`() {
        val laps = listOf(
            summary("a", 90_000, "Suzuka", "2026-11-11T10:00:00Z"),
            summary("b", 91_000, "suzuka", "2026-11-11T10:00:00Z"),
            summary("c", 92_000, "Monza", "2026-11-11T10:00:00Z"),
        )

        val filtered = laps.applyFilter(LapFilter(trackKey = "suzuka"))

        assertEquals(listOf("a", "b"), filtered.map { it.lapId.value })
    }

    @Test
    fun `same lap compares session and lap index`() {
        val lap = summary("a", 90_000, "Suzuka", "2026-11-11T10:00:00Z")

        assertTrue(lap.lapId == summary("a", 80_000, "Suzuka", "2026-11-11T10:00:00Z").lapId)
        assertFalse(lap.lapId == summary("a2", 90_000, "Suzuka", "2026-11-11T10:00:00Z").lapId)
        assertFalse(lap.lapId == summary("b", 90_000, "Suzuka", "2026-11-11T10:00:00Z").lapId)
    }

    private fun summary(
        lapId: String,
        lapTimeMs: Int,
        trackLabel: String,
        createdAt: String,
    ) = LapSummary(
        lapId = LapId(lapId),
        lapTimeMs = lapTimeMs,
        createdAt = createdAt,
        trackLabel = trackLabel,
        carName = "BRZ GT300 '21",
        hasReplay = false,
        replayPending = false,
    )
}
