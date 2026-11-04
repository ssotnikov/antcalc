package local.antenna.calculator.core.input

import java.math.BigDecimal

/** Strict positive decimal input; accepts either decimal separator without grouping. */
object PositiveDecimalParser {
    const val MAX_CHARACTERS = 32

    private val completeNumber = Regex("(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)")
    private val incompleteNumber = Regex("[0-9]*[.,]")

    fun parse(text: String): FieldState<BigDecimal> {
        val value = text.trim()
        return when {
            value.isEmpty() -> FieldState.Empty
            value.length > MAX_CHARACTERS -> FieldState.Invalid(InputError.TOO_LONG)
            incompleteNumber.matches(value) -> FieldState.Incomplete
            !completeNumber.matches(value) -> FieldState.Invalid(InputError.INVALID_NUMBER)
            else -> {
                val number = BigDecimal(value.replace(',', '.')).stripTrailingZeros()
                if (number.signum() == 0) FieldState.Invalid(InputError.NON_POSITIVE)
                else FieldState.Valid(number)
            }
        }
    }
}
