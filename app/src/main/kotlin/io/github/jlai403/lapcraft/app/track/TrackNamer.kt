package io.github.jlai403.lapcraft.app.track

import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapId
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import io.github.jlai403.lapcraft.core.track.TrackMatch
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
            if (record.trackId.isNotEmpty() && record.trackLabel != UNKNOWN) continue
            val samples = lapSamples(id)
            if (samples.isEmpty()) continue
            val match = catalog.match(samples)
            val newId = match.officialId ?: record.trackId
            val newName = if (record.trackLabel == UNKNOWN || record.trackLabel.isBlank()) {
                match.name ?: record.trackLabel
            } else {
                record.trackLabel
            }
            if (newId == record.trackId && newName == record.trackLabel) continue
            store.setTrack(id, newId, newName)
            Diagnostics.log("track: backfilled ${id.value} -> $newName ($newId)")
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
        const val VERSION = 2
    }
}
