package io.github.jlai403.lapcraft.core.track

data class TrackPoint(val x: Double, val z: Double)

data class TrackSignature(
    val officialId: String = "",
    val name: String = "",
    val lengthM: Double,
    val minX: Double,
    val maxX: Double,
    val minZ: Double,
    val maxZ: Double,
    val path: List<TrackPoint> = emptyList(),
    val reverseName: String = "",
)

data class TrackCandidate(val name: String, val officialId: String, val coverage: Float)

data class TrackMatch(val name: String?, val officialId: String? = null, val candidates: List<TrackCandidate> = emptyList())
