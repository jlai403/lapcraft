package io.github.jlai403.lapcraft.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CarInfo(
    val code: Int,
    val name: String,
    @SerialName("class") val carClass: String? = null,
    val manufacturer: String? = null,
)

object CarCatalogCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CatalogFile(
        val source: String? = null,
        @SerialName("fetched_at") val fetchedAt: String? = null,
        val cars: List<CarInfo> = emptyList(),
    )

    fun decode(text: String): Map<Int, CarInfo> =
        runCatching { json.decodeFromString<CatalogFile>(text) }
            .getOrNull()
            ?.cars
            ?.associateBy { it.code }
            .orEmpty()

    fun resolve(code: Int, catalog: Map<Int, CarInfo>): String? = catalog[code]?.name
}