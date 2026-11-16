package io.github.jlai403.lapcraft.core.capture

import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample

class LapSampleBuffer {
    private val samples = ArrayList<Sample>()

    @Synchronized
    fun add(sample: Sample) {
        samples.add(sample)
        val cutoff = sample.timeNanos - MAX_AGE_NANOS
        samples.removeAll { it.timeNanos < cutoff }
    }

    @Synchronized
    fun samplesFor(boundary: LapBoundary, preRollNanos: Long = 2_000_000_000L): List<Sample> {
        val start = boundary.startNanos - preRollNanos
        return samples.filter { it.timeNanos in start..boundary.endNanos }
    }

    @Synchronized
    fun covers(boundary: LapBoundary): Boolean =
        samples.firstOrNull()?.let { it.timeNanos < boundary.startNanos } == true

    @Synchronized
    fun trimBefore(nanos: Long) {
        samples.removeAll { it.timeNanos < nanos }
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    private companion object {
        const val MAX_AGE_NANOS = 15L * 60 * 1_000_000_000
    }
}
