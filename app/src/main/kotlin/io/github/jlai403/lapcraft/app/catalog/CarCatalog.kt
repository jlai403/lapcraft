package io.github.jlai403.lapcraft.app.catalog

import android.content.Context
import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.catalog.CarCatalogCodec
import io.github.jlai403.lapcraft.core.catalog.CarInfo

/** Loads the bundled GT7 car catalogue (car_code → name) from assets. */
class CarCatalog(context: Context) {
    private val cars by lazy {
        runCatching {
            context.assets.open(ASSET).bufferedReader().use { reader ->
                CarCatalogCodec.decode(reader.readText())
            }
        }.onFailure {
            Diagnostics.log("car catalog: asset failed: ${it.message}")
        }.getOrDefault(emptyMap())
    }

    fun name(code: Int): String? = cars[code]?.name

    fun info(code: Int): CarInfo? = cars[code]

    private companion object {
        const val ASSET = "car-catalog.json"
    }
}