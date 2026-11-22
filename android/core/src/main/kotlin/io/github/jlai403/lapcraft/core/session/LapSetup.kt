package io.github.jlai403.lapcraft.core.session

/**
 * The car's driving-aid settings for a lap. GT7's telemetry carries the TCS-active flag only,
 * never the TCS level or brake balance, so both are entered by the driver.
 */
val TCS_LEVELS = 0..5
val BRAKE_BALANCE_LEVELS = -5..5

fun tcsLabel(tcs: Int?): String? = when {
    tcs == null -> null
    tcs <= 0 -> "TCS off"
    else -> "TCS $tcs"
}

fun brakeBalanceLabel(balance: Int?): String? =
    balance?.let { if (it > 0) "BB +$it" else "BB $it" }

fun setupLabel(tcs: Int?, brakeBalance: Int?): String? =
    listOfNotNull(tcsLabel(tcs), brakeBalanceLabel(brakeBalance))
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
