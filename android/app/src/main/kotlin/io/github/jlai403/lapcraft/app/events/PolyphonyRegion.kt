package io.github.jlai403.lapcraft.app.events

import java.util.Locale

/**
 * Polyphony publishes one championship schedule file per region folder.
 * The folder is not the JSON's region_id: Europe is 2, Asia 4, Oceania 5,
 * North America 6, South America 7.
 */
object PolyphonyRegion {
    const val EUROPE = 2
    const val ASIA = 4
    const val OCEANIA = 5
    const val NORTH_AMERICA = 6
    const val SOUTH_AMERICA = 7

    fun folder(country: String = Locale.getDefault().country): Int =
        when (country.lowercase(Locale.ROOT)) {
            in EUROPE_COUNTRIES -> EUROPE
            in ASIA_COUNTRIES -> ASIA
            in OCEANIA_COUNTRIES -> OCEANIA
            in NORTH_AMERICA_COUNTRIES -> NORTH_AMERICA
            in SOUTH_AMERICA_COUNTRIES -> SOUTH_AMERICA
            else -> EUROPE
        }

    private val EUROPE_COUNTRIES = setOf(
        "gb", "ie", "be", "lu", "nl", "fr", "de", "at", "ch", "it", "pt", "dk", "fi", "no", "se",
        "es", "ru", "ae", "za", "pl", "gr", "sa", "cz", "tr", "in", "hr", "si", "kw", "il", "ua",
        "bg", "hu", "qa", "ro", "bh", "lb", "om", "mt", "cy", "sk", "is",
    )
    private val ASIA_COUNTRIES = setOf("jp", "hk", "tw", "sg", "my", "kr", "id", "th", "cn")
    private val OCEANIA_COUNTRIES = setOf("au", "nz")
    private val NORTH_AMERICA_COUNTRIES = setOf("us", "ca", "mx")
    private val SOUTH_AMERICA_COUNTRIES = setOf(
        "br", "cl", "ar", "pe", "co", "pa", "cr", "ec", "gt", "sv", "py", "hn", "uy", "bo", "ni",
    )
}
