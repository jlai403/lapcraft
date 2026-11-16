package io.github.jlai403.lapcraft.core.profile

import kotlinx.serialization.Serializable

@Serializable
data class DriverProfile(
    val psnId: String,
    val nickname: String,
    val countryCode: String?,
    val dr: String? = null,
    val sr: String? = null,
)
