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
    val family: String,
) {
    @SerialName("CH") COMFORT_HARD("CH", "Comfort hard", TyreTier.Hard, "Comfort"),
    @SerialName("CM") COMFORT_MEDIUM("CM", "Comfort medium", TyreTier.Medium, "Comfort"),
    @SerialName("CS") COMFORT_SOFT("CS", "Comfort soft", TyreTier.Soft, "Comfort"),
    @SerialName("SH") SPORTS_HARD("SH", "Sports hard", TyreTier.Hard, "Sports"),
    @SerialName("SM") SPORTS_MEDIUM("SM", "Sports medium", TyreTier.Medium, "Sports"),
    @SerialName("SS") SPORTS_SOFT("SS", "Sports soft", TyreTier.Soft, "Sports"),
    @SerialName("RH") RACING_HARD("RH", "Racing hard", TyreTier.Hard, "Racing"),
    @SerialName("RM") RACING_MEDIUM("RM", "Racing medium", TyreTier.Medium, "Racing"),
    @SerialName("RS") RACING_SOFT("RS", "Racing soft", TyreTier.Soft, "Racing"),
    @SerialName("IM") INTERMEDIATE("IM", "Intermediate", TyreTier.Intermediate, "Intermediate"),
    @SerialName("W") WET("W", "Wet", TyreTier.Wet, "Wet"),
    @SerialName("D") DIRT("D", "Dirt", TyreTier.Dirt, "Dirt"),
    @SerialName("SN") SNOW("SN", "Snow", TyreTier.Snow, "Snow"),
    ;

    companion object {
        fun fromCode(code: String): TyreCompound? = entries.firstOrNull { it.code == code }
        fun fromFamily(value: String?): TyreCompound? =
            entries.firstOrNull { it.family.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class TyreTier {
    Hard, Medium, Soft, Intermediate, Wet, Dirt, Snow,
}