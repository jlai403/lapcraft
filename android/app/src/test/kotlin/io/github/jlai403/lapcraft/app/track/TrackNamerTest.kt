package io.github.jlai403.lapcraft.app.track

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.core.lap.LapTimeSource
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapRecord
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LapTelemetryCodec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackNamerTest {
    @Test
    fun `backfills an unknown legacy lap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LapStore(context.filesDir)
        val samples = roadAtlantaLap()
        val lapId = LapId("legacy")
        store.save(
            lapId,
            LapRecord(
                schemaVersion = 3,
                capturedAt = "2026-11-10T10:00:00Z",
                lapTimeMs = 95_000,
                lapTimeSource = LapTimeSource.Reported,
                trackId = "",
                trackLabel = TrackNamer.UNKNOWN,
                carCode = 3474,
                carName = "Car #3474",
                startTickNanos = samples.first().timeNanos,
                endTickNanos = samples.last().timeNanos,
                leadInMs = 0,
            ),
        )
        store.writeSamples(lapId, samples)

        val renamed = runBlocking {
            TrackNamer(store, TrackCatalog(context)).identifyUnknownLaps()
        }

        assertEquals(1, renamed)
        assertEquals("Michelin Raceway Road Atlanta", store.load(lapId)?.trackLabel)
    }

    private fun roadAtlantaLap(): List<Sample> {
        val points = Json.decodeFromString<List<List<Double>>>(
            checkNotNull(javaClass.getResourceAsStream("/road-atlanta-line.json")) {
                "missing Road Atlanta fixture"
            }.bufferedReader().use { it.readText() },
        )
        return points.mapIndexed { index, point ->
            Sample(
                timeNanos = index * 16_666_667L,
                speedMps = 0f,
                throttle = 0,
                brake = 0,
                gear = 1,
                rpm = 0f,
                positionX = point[0].toFloat(),
                positionY = 0f,
                positionZ = point[1].toFloat(),
                lapCount = 1,
                bestLapTimeMs = 0,
                lastLapTimeMs = 0,
                flags = 0,
                carCode = 0,
                fuel = 0f,
                tyreTempFL = 0f,
                tyreTempFR = 0f,
                tyreTempRL = 0f,
                tyreTempRR = 0f,
                packetId = 0,
            )
        }
    }
}

