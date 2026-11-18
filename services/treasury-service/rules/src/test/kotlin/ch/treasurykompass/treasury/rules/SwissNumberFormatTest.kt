package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Expected strings were produced with `Intl.NumberFormat('de-CH')` as the prototype uses it. */
class SwissNumberFormatTest {

    @ParameterizedTest(name = "{0} with {1} decimals is «{2}»")
    @CsvSource(
        quoteCharacter = '"',
        value = [
            "14.2, 1, 14.2",
            "21, 1, 21.0",
            "1.3348, 2, 1.33",
            "0.098714, 2, 0.10",
            "1.005, 2, 1.01",
            "0.125, 2, 0.13",
            "72.5, 0, 73",
            "65, 0, 65",
            "1234567.5, 0, 1'234'568",
            "1000, 1, 1'000.0",
            "999.95, 1, 1'000.0",
        ],
    )
    fun `formats like Intl de-CH`(value: Double, decimals: Int, expected: String) {
        assertEquals(expected, SwissNumberFormat.number(value, decimals))
    }

    @Test
    fun `uses the typographic minus sign and keeps it for negative values rounding to zero`() {
        assertEquals("−1.33", SwissNumberFormat.number(-1.3348, 2))
        assertEquals("−3", SwissNumberFormat.number(-2.5, 0))
        assertEquals("−0.00", SwissNumberFormat.number(-0.0001, 2))
        assertEquals("−0.0", SwissNumberFormat.number(-0.0))
        assertEquals("0.0", SwissNumberFormat.number(0.0))
    }

    @Test
    fun `percent multiplies by 100 and appends a spaced percent sign`() {
        assertEquals("58 %", SwissNumberFormat.percent(0.5803478260869566))
        assertEquals("8.1 %", SwissNumberFormat.percent(0.08098591549295775, 1))
        assertEquals("100 %", SwissNumberFormat.percent(1.0))
    }

    @Test
    fun `rejects non-finite values`() {
        assertFailsWith<IllegalArgumentException> { SwissNumberFormat.number(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SwissNumberFormat.number(Double.POSITIVE_INFINITY) }
    }
}
