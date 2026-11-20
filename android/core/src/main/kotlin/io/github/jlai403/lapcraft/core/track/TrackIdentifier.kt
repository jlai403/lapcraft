package io.github.jlai403.lapcraft.core.track

import io.github.jlai403.lapcraft.core.model.Sample
import kotlin.math.abs
import kotlin.math.hypot

object TrackIdentifier {
    const val MIN_SAMPLES = 60
    const val MAX_STEP_M = 200.0
    const val LENGTH_TOLERANCE = 0.04
    const val CENTRE_TOLERANCE_M = 120.0
    const val EXTENT_TOLERANCE = 0.20

    const val PATH_CELL_M = 20.0
    const val COVERAGE_RADIUS_M = 25.0
    const val MIN_COVERAGE = 0.90
    const val COVERAGE_MARGIN = 0.005
    const val DIRECTION_MIN = 0.5
    const val DIRECTION_SAMPLES = 80
    const val MAX_CANDIDATES = 5

    fun signature(samples: List<Sample>): TrackSignature? {
        if (samples.size < MIN_SAMPLES) return null

        var length = 0.0
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE

        samples.forEachIndexed { index, sample ->
            val x = sample.positionX.toDouble()
            val z = sample.positionZ.toDouble()
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minZ = minOf(minZ, z)
            maxZ = maxOf(maxZ, z)
            if (index > 0) {
                val previous = samples[index - 1]
                val step = hypot(
                    x - previous.positionX.toDouble(),
                    z - previous.positionZ.toDouble(),
                )
                if (step <= MAX_STEP_M) length += step
            }
        }

        if (length <= 0.0) return null
        return TrackSignature(
            lengthM = length,
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
            path = samples.map { TrackPoint(it.positionX.toDouble(), it.positionZ.toDouble()) },
        )
    }

    fun match(lap: TrackSignature, candidates: List<TrackSignature>): TrackMatch {
        if (lap.path.size < 2) return matchByBoxOnly(lap, candidates)

        val gated = candidates.filter { matches(lap, it) }
        val withPath = gated.filter { it.path.size >= 8 }
        val scored = LinkedHashMap<String, Scored>()
        for (candidate in if (withPath.isNotEmpty()) withPath else gated) {
            val resolved = resolve(lap, candidate) ?: continue
            val previous = scored[resolved.name]
            if (previous == null || resolved.coverage > previous.coverage) {
                scored[resolved.name] = resolved
            }
        }
        if (scored.isEmpty()) return TrackMatch(null, null, emptyList())

        val ranked = scored.entries.sortedByDescending { it.value.coverage }
        val offered = ranked
            .filter { it.value.coverage >= MIN_COVERAGE }
            .take(MAX_CANDIDATES)
            .map { TrackCandidate(it.key, it.value.officialId, it.value.coverage.toFloat()) }
        val best = ranked.first().value
        val runnerUp = ranked.getOrNull(1)?.value?.coverage ?: 0.0
        val name = if (best.coverage >= MIN_COVERAGE && best.coverage - runnerUp >= COVERAGE_MARGIN) {
            ranked.first().key
        } else {
            null
        }
        val officialId = if (name != null) best.officialId else null
        return TrackMatch(name, officialId, offered)
    }

    private fun matchByBoxOnly(lap: TrackSignature, candidates: List<TrackSignature>): TrackMatch {
        val gated = candidates.filter { matches(lap, it) }
        val names = gated.map { it.name }.distinct()
        val offered = names.map { name ->
            TrackCandidate(name, gated.first { it.name == name }.officialId, 1f)
        }
        val name = names.singleOrNull()
        val officialId = name?.let { gated.firstOrNull { c -> c.name == name }?.officialId }
        return TrackMatch(name, officialId, offered)
    }

    private fun resolve(lap: TrackSignature, candidate: TrackSignature): Scored? {
        if (candidate.path.isEmpty()) {
            return Scored(candidate.name, candidate.officialId, 1.0)
        }
        val direction = direction(lap.path, candidate.path)
        val name = when {
            direction >= DIRECTION_MIN -> candidate.name
            direction <= -DIRECTION_MIN && candidate.reverseName.isNotBlank() -> candidate.reverseName
            else -> return null
        }
        return Scored(name, candidate.officialId, coverage(lap.path, candidate.path))
    }

    fun coverage(lap: List<TrackPoint>, path: List<TrackPoint>): Double {
        val grid = HashMap<Pair<Int, Int>, MutableList<Int>>()
        path.forEachIndexed { index, point ->
            grid.getOrPut(cell(point)) { ArrayList() }.add(index)
        }
        val radiusSquared = COVERAGE_RADIUS_M * COVERAGE_RADIUS_M
        var covered = 0
        for (point in lap) {
            val (cx, cz) = cell(point)
            var hit = false
            for (dx in -1..1) {
                for (dz in -1..1) {
                    for (index in grid[(cx + dx) to (cz + dz)].orEmpty()) {
                        val other = path[index]
                        if (squaredDistance(point, other) <= radiusSquared) {
                            hit = true
                            break
                        }
                    }
                    if (hit) break
                }
                if (hit) break
            }
            if (hit) covered++
        }
        return covered.toDouble() / lap.size
    }

    fun direction(lap: List<TrackPoint>, path: List<TrackPoint>): Double {
        if (lap.size < 2 || path.size < 8) return 0.0
        val grid = HashMap<Pair<Int, Int>, MutableList<Int>>()
        path.forEachIndexed { index, point ->
            grid.getOrPut(cell(point)) { ArrayList() }.add(index)
        }
        val sampleStep = maxOf(1, lap.size / DIRECTION_SAMPLES)
        val found = ArrayList<Int>()
        for (index in 0 until lap.size step sampleStep) {
            val point = lap[index]
            val (cx, cz) = cell(point)
            var nearest = -1
            var nearestDistance = Double.MAX_VALUE
            for (dx in -1..1) {
                for (dz in -1..1) {
                    for (candidate in grid[(cx + dx) to (cz + dz)].orEmpty()) {
                        val distance = squaredDistance(point, path[candidate])
                        if (distance < nearestDistance) {
                            nearestDistance = distance
                            nearest = candidate
                        }
                    }
                }
            }
            if (nearest >= 0) found += nearest
        }
        var forward = 0
        var backward = 0
        for (index in 0 until found.size - 1) {
            val advance = (found[index + 1] - found[index] + path.size) % path.size
            if (advance == 0) continue
            if (advance < path.size / 2) forward++ else backward++
        }
        val tested = forward + backward
        return if (tested == 0) 0.0 else (forward - backward).toDouble() / tested
    }

    private fun cell(point: TrackPoint): Pair<Int, Int> =
        (point.x / PATH_CELL_M).toInt() to (point.z / PATH_CELL_M).toInt()

    private fun squaredDistance(a: TrackPoint, b: TrackPoint): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private data class Scored(val name: String, val officialId: String, val coverage: Double)

    private fun matches(lap: TrackSignature, candidate: TrackSignature): Boolean {
        if (candidate.lengthM <= 0.0) return false
        if (abs(lap.lengthM - candidate.lengthM) / candidate.lengthM > LENGTH_TOLERANCE) return false
        if (abs(centreX(lap) - centreX(candidate)) > CENTRE_TOLERANCE_M) return false
        if (abs(centreZ(lap) - centreZ(candidate)) > CENTRE_TOLERANCE_M) return false
        return withinExtent(extentX(lap), extentX(candidate)) &&
            withinExtent(extentZ(lap), extentZ(candidate))
    }

    private fun centreX(signature: TrackSignature): Double = (signature.minX + signature.maxX) / 2
    private fun centreZ(signature: TrackSignature): Double = (signature.minZ + signature.maxZ) / 2
    private fun extentX(signature: TrackSignature): Double = signature.maxX - signature.minX
    private fun extentZ(signature: TrackSignature): Double = signature.maxZ - signature.minZ

    private fun withinExtent(lap: Double, candidate: Double): Boolean {
        if (candidate <= 0.0) return false
        return abs(lap - candidate) / candidate <= EXTENT_TOLERANCE
    }
}
