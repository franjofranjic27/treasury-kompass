package ch.treasurykompass.treasury.rules

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Number formatting of the prototype (`fmt`, `pct`): `Intl.NumberFormat('de-CH')` with fixed decimals,
 * decimal point, apostrophe as grouping separator and «−» (U+2212) as minus sign.
 *
 * Intl rounds the shortest decimal representation of the double half away from zero (1.005 → «1.01»);
 * [BigDecimal.valueOf] uses the same shortest representation, so the texts match the prototype digit by digit.
 * Like Intl, negative values that round to zero keep their sign («−0.00»).
 */
object SwissNumberFormat {
    private const val MINUS_SIGN = "−"
    private const val GROUPING_SEPARATOR = "'"
    private const val GROUP_SIZE = 3

    /** Prototype `fmt(value, decimals)`. */
    fun number(value: Double, decimals: Int = 1): String {
        require(value.isFinite()) { "Only finite numbers can be formatted" }
        require(decimals >= 0) { "Decimals must not be negative" }
        val negative = value < 0.0 || 1.0 / value == Double.NEGATIVE_INFINITY
        val digits = BigDecimal.valueOf(abs(value)).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        val integerPart = digits.substringBefore('.')
        val fractionPart = digits.substringAfter('.', missingDelimiterValue = "")
        val grouped = integerPart.reversed().chunked(GROUP_SIZE).joinToString(GROUPING_SEPARATOR).reversed()
        val sign = if (negative) MINUS_SIGN else ""
        return if (fractionPart.isEmpty()) "$sign$grouped" else "$sign$grouped.$fractionPart"
    }

    /** Prototype `pct(fraction, decimals)`: `0.58 → «58 %»`. */
    fun percent(fraction: Double, decimals: Int = 0): String = "${number(fraction * 100, decimals)} %"
}
