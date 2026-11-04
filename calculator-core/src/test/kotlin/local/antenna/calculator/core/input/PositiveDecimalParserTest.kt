package local.antenna.calculator.core.input

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PositiveDecimalParserTest(
    private val text: String,
    private val expected: FieldState<BigDecimal>,
) {
    @Test
    fun `parse matches the complete input contract`() {
        assertEquals(expected, PositiveDecimalParser.parse(text))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: input={0}")
        fun cases(): List<Array<Any>> {
            fun valid(value: String) = FieldState.Valid(BigDecimal(value).stripTrailingZeros())
            fun invalid(error: InputError) = FieldState.Invalid(error)
            return listOf(
                "" to FieldState.Empty,
                " \t\n\r" to FieldState.Empty,
                "." to FieldState.Incomplete,
                "," to FieldState.Incomplete,
                "145," to FieldState.Incomplete,
                "145." to FieldState.Incomplete,
                "0," to FieldState.Incomplete,
                "145.5" to valid("145.5"),
                "145,5" to valid("145.5"),
                " \t145,5\n" to valid("145.5"),
                "\u00a0145,5\u00a0" to valid("145.5"),
                ".5" to valid("0.5"),
                ",5" to valid("0.5"),
                "000145.5000" to valid("145.5"),
                "0.95000" to valid("0.95"),
                "9".repeat(32) to valid("9".repeat(32)),
                " " + "9".repeat(32) + " " to valid("9".repeat(32)),
                "9".repeat(33) to invalid(InputError.TOO_LONG),
                "0" to invalid(InputError.NON_POSITIVE),
                "000.000" to invalid(InputError.NON_POSITIVE),
                "-145" to invalid(InputError.INVALID_NUMBER),
                "+145" to invalid(InputError.INVALID_NUMBER),
                "1,234.5" to invalid(InputError.INVALID_NUMBER),
                "1.2.3" to invalid(InputError.INVALID_NUMBER),
                "145 000" to invalid(InputError.INVALID_NUMBER),
                "145\u00a0000" to invalid(InputError.INVALID_NUMBER),
                "145\t000" to invalid(InputError.INVALID_NUMBER),
                "1e3" to invalid(InputError.INVALID_NUMBER),
                "1E3" to invalid(InputError.INVALID_NUMBER),
                "NaN" to invalid(InputError.INVALID_NUMBER),
                "Infinity" to invalid(InputError.INVALID_NUMBER),
                "145MHz" to invalid(InputError.INVALID_NUMBER),
                "1,2," to invalid(InputError.INVALID_NUMBER),
            ).map { (input, result) -> arrayOf(input, result) }
        }
    }
}
