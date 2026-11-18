package ch.treasurykompass.treasury.common

import ch.treasurykompass.treasury.rules.SwissNumberFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * `de-CH` display formatting of the prototype (`fmt`, `sgn`, `pct`). Delegates to the rules' [SwissNumberFormat]
 * so that rule texts and application texts (protocol, audit) format numbers identically.
 */
object SwissFormat {

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Fixed number of decimals, e.g. `number(13.348, 2)` → `13.35`. */
    fun number(value: Double, decimals: Int = 1): String = SwissNumberFormat.number(value, decimals)

    /** Between [minDecimals] and [maxDecimals] decimals, e.g. `21.0` → `21.0`, `3.25` → `3.25` with (1, 2). */
    fun number(value: Double, minDecimals: Int, maxDecimals: Int): String {
        val scale = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().scale()
        return number(value, scale.coerceIn(minDecimals, maxDecimals))
    }

    /** Ratio as percent, e.g. `percent(0.4115)` → `41 %`. */
    fun percent(ratio: Double, decimals: Int = 0): String = SwissNumberFormat.percent(ratio, decimals)

    /** Value with explicit sign like the prototype `sgn`, e.g. `+0.034` or `−0.099`; zero without sign. */
    fun signed(value: Double, decimals: Int = 2): String {
        val rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        return when (rounded.signum()) {
            0 -> number(0.0, decimals)
            1 -> "+${number(value, decimals)}"
            else -> "−${number(abs(value), decimals)}"
        }
    }

    fun date(date: LocalDate): String = date.format(dateFormat)
}
