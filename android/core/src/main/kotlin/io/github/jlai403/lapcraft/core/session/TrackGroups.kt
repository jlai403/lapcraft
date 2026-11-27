package io.github.jlai403.lapcraft.core.session

data class LapTrackGroup(
    val key: String,
    val label: String,
    val laps: List<LapSummary>,
    val bestLapMs: Int?,
)

fun List<LapSummary>.trackGroups(
    isUnknown: (LapSummary) -> Boolean = { false },
): List<LapTrackGroup> {
    if (isEmpty()) return emptyList()
    val byKey = LinkedHashMap<String, MutableList<LapSummary>>()
    for (lap in this) {
        byKey.getOrPut(lap.trackKey) { mutableListOf() } += lap
    }
    return byKey.entries
        .map { (key, laps) ->
            val unknown = laps.any { isUnknown(it) }
            LapTrackGroup(
                key = key,
                label = laps.first().trackLabel,
                laps = laps,
                bestLapMs = if (unknown) null else laps.minOfOrNull { it.lapTimeMs },
            )
        }
        .sortedWith(
            compareBy<LapTrackGroup>(
                { it.laps.any { lap -> isUnknown(lap) } },
                { groupNewest(it).let { -it } },
            ),
        )
}

private fun groupNewest(group: LapTrackGroup): Long =
    group.laps.maxOfOrNull {
        runCatching { java.time.Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(0L)
    } ?: 0L