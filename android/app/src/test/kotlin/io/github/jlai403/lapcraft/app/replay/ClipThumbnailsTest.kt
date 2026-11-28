package io.github.jlai403.lapcraft.app.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClipThumbnailsTest {
    @Test
    fun `filmstripTimes spans the clip evenly`() {
        assertEquals(listOf(0L, 25L, 50L, 75L, 100L), filmstripTimes(0, 100, 5))
    }

    @Test
    fun `filmstripTimes returns empty for too few frames or empty span`() {
        assertTrue(filmstripTimes(0, 100, 1).isEmpty())
        assertTrue(filmstripTimes(0, 100, 0).isEmpty())
        assertTrue(filmstripTimes(50, 50, 5).isEmpty())
        assertTrue(filmstripTimes(100, 0, 5).isEmpty())
    }

    @Test
    fun `readThumbnails returns empty for a missing file`() {
        assertTrue(readThumbnails(File("/no/such/file.mp4"), listOf(0L, 10L), 64, 40).isEmpty())
    }

    @Test
    fun `hudCropRect lands inside the frame and matches the timer aspect`() {
        val r = hudCropRect(864, 486)
        assertTrue(r[0] >= 0 && r[1] >= 0)
        assertTrue(r[0] + r[2] <= 864 && r[1] + r[3] <= 486)
        val aspect = r[2].toDouble() / r[3].toDouble()
        assertTrue(aspect in 4.0..4.6)
    }
}