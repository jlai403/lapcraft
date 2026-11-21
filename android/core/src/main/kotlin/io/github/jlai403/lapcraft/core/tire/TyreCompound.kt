package io.github.jlai403.lapcraft.core.tire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GT7 tyre compound. Codes match the in-game abbreviations (CH, RM, ...).
 * GT7's telemetry carries no compound, so this is always user-supplied metadata.
 */
@Serializable
enum class TyreCompound(
    val code: String,
    val label: String,
    val tier: TyreTier,
) {
    @SerialName("CH") COMFORT_HARD("CH", "Comfort hard", TyreTier.Hard),
    @SerialName("CM") COMFORT_MEDIUM("CM", "Comfort medium", TyreTier.Medium),
    @SerialName("CS") COMFORT_SOFT("CS", "Comfort soft", TyreTier.Soft),
    @SerialName("SH") SPORTS_HARD("SH", "Sports hard", TyreTier.Hard),
    @SerialName("SM") SPORTS_MEDIUM("SM", "Sports medium", TyreTier.Medium),
    @SerialName("SS") SPORTS_SOFT("SS", "Sports soft", TyreTier.Soft),
    @SerialName("RH") RACING_HARD("RH", "Racing hard", TyreTier.Hard),
    @SerialName("RM") RACING_MEDIUM("RM", "Racing medium", TyreTier.Medium),
    @SerialName("RS") RACING_SOFT("RS", "Racing soft", TyreTier.Soft),
    @SerialName("IM") INTERMEDIATE("IM", "Intermediate", TyreTier.Intermediate),
    @SerialName("W") WET("W", "Wet", TyreTier.Wet),
    @SerialName("D") DIRT("D", "Dirt", TyreTier.Dirt),
    @SerialName("SN") SNOW("SN", "Snow", TyreTier.Snow),
    ;

    companion object {
        fun fromCode(code: String): TyreCompound? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
enum class TyreTier {
    Hard, Medium, Soft, Intermediate, Wet, Dirt, Snow,
}