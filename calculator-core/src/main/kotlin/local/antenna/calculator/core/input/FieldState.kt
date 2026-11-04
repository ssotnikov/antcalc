package local.antenna.calculator.core.input

/** Parsing results contain no UI strings; a screen decides when to show an error. */
sealed interface FieldState<out T> {
    data object Empty : FieldState<Nothing>
    data object Incomplete : FieldState<Nothing>
    data class Invalid(val error: InputError) : FieldState<Nothing>
    data class Valid<T>(val value: T) : FieldState<T>
}

enum class InputError {
    TOO_LONG,
    INVALID_NUMBER,
    NON_POSITIVE,
    OUT_OF_RANGE,
    TOO_PRECISE,
}
