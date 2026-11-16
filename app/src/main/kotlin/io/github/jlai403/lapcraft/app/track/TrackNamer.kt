package io.github.jlai403.lapcraft.app.track

import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Names laps captured before track identification existed. */
class TrackNamer(
    private val store: LapStore,
    private val catalog: TrackCatalog,
) {
    suspend fun identifyUnknownLaps(): Int = withContext(Dispatchers.IO) {
        var renamed = 0
        for (id in store.list()) {
            val record = store.load(id) ?: continue
            if (record.trackLabel != UNKNOWN) continue
            val samples = lapSamples(id)
            if (samples.isEmpty()) continue
            val name = catalog.identify(samples) ?: continue
            store.renameTrack(id, name)
            Diagnostics.log("track: backfilled ${id.value} -> $name")
            renamed++
        }
        renamed
    }

    fun candidates(id: LapId): List<TrackCandidate> =
        catalog.candidates(lapSamples(id))

    private fun lapSamples(id: LapId): List<Sample> {
        val record = store.load(id) ?: return emptyList()
        val samples = runCatching { store.readSamples(id) }.getOrDefault(emptyList())
        return samples.filter { it.timeNanos in record.startTickNanos..record.endTickNanos }
    }

    companion object {
        const val UNKNOWN = "Unknown"

        /** Bumped when the matcher or bundled signatures change, so old laps are retried once. */
        const val VERSION = 1
    }
}