package local.antenna.calculator.core.matching

import java.math.BigDecimal
import kotlin.math.*

enum class MatchingMode { SWR, GAMMA, RETURN_LOSS, IMPEDANCE }
enum class MatchingField(val minimum: Double, val maximum: Double, val signed: Boolean = false) {
    SWR(1.0, 1e6), GAMMA(0.0, 1.0), RETURN_LOSS(0.0, 300.0),
    RESISTANCE(0.0, 1e6), REACTANCE(-1e6, 1e6, true), REFERENCE(1.0, 1000.0)
}
enum class MatchingError { EMPTY, INCOMPLETE, INVALID, RANGE }
data class MatchingNumber(val value: Double? = null, val error: MatchingError? = null)

/** Deliberately separate from the strictly positive frequency/length parser. */
fun parseMatchingNumber(text: String, field: MatchingField): MatchingNumber {
    val input = text.trim()
    if (input.isEmpty()) return MatchingNumber(error = MatchingError.EMPTY)
    if (input.length > 32) return MatchingNumber(error = MatchingError.INVALID)
    if (input in listOf(".", ",") || field.signed && input in listOf("-", "+", "-.", "-,", "+.", "+,"))
        return MatchingNumber(error = MatchingError.INCOMPLETE)
    val prefix = if (field.signed) "[+-]?" else ""
    if (Regex("${prefix}\\d+[.,]").matches(input)) return MatchingNumber(error = MatchingError.INCOMPLETE)
    if (!Regex("${prefix}\\d+(?:[.,]\\d+)?").matches(input)) return MatchingNumber(error = MatchingError.INVALID)
    val decimal = BigDecimal(input.replace(',', '.'))
    if (decimal < BigDecimal.valueOf(field.minimum) || decimal > BigDecimal.valueOf(field.maximum))
        return MatchingNumber(error = MatchingError.RANGE)
    return MatchingNumber(value = decimal.toDouble())
}

sealed interface MatchingMeasure {
    data class Finite(val value: Double) : MatchingMeasure { init { require(value.isFinite() && value >= 0.0) } }
    data object Infinite : MatchingMeasure
}
data class ComplexReflection(val real: Double, val imaginary: Double, val phaseDegrees: Double?)
data class MatchingResult(
    val magnitude: Double, val swr: MatchingMeasure, val returnLoss: MatchingMeasure,
    val mismatchLoss: MatchingMeasure, val reflectedFraction: Double, val complex: ComplexReflection? = null,
)

private fun measure(value: Double) = if (value.isInfinite()) MatchingMeasure.Infinite else MatchingMeasure.Finite(if (value == 0.0) 0.0 else value)

private fun result(g: Double, accepted: Double, complex: ComplexReflection? = null): MatchingResult = MatchingResult(
    g, measure(if (accepted == 0.0) Double.POSITIVE_INFINITY else (1 + g).pow(2) / accepted),
    measure(if (g == 0.0) Double.POSITIVE_INFINITY else -20 * log10(g)),
    measure(if (accepted == 0.0) Double.POSITIVE_INFINITY else -10 * log10(accepted)), g * g, complex,
)

fun matchingFromMagnitude(magnitude: Double): MatchingResult {
    require(magnitude.isFinite() && magnitude in 0.0..1.0)
    return result(magnitude, (1 - magnitude) * (1 + magnitude))
}
fun matchingFromSwr(swr: Double): MatchingResult {
    require(swr.isFinite() && swr in 1.0..1e6)
    return matchingFromMagnitude((swr - 1) / (swr + 1))
}
fun matchingFromReturnLoss(decibels: Double): MatchingResult {
    require(decibels.isFinite() && decibels in 0.0..300.0)
    return matchingFromMagnitude(10.0.pow(-decibels / 20))
}
fun matchingFromImpedance(resistance: Double, reactance: Double, reference: Double): MatchingResult {
    require(resistance.isFinite() && resistance in 0.0..1e6)
    require(reactance.isFinite() && reactance in -1e6..1e6)
    require(reference.isFinite() && reference in 1.0..1000.0)
    // Bounded operands keep the denominator finite. Compute accepted power directly
    // rather than subtracting nearly equal values at extreme mismatch.
    val denominator = (resistance + reference).pow(2) + reactance.pow(2)
    val re = ((resistance - reference) * (resistance + reference) + reactance.pow(2)) / denominator
    val im = 2 * reference * reactance / denominator
    val accepted = (4 * resistance * reference / denominator).coerceIn(0.0, 1.0)
    // Only roundoff in this computed passive model is bounded; no measurement is clamped.
    val g = if (resistance == 0.0) 1.0 else hypot(re, im).coerceAtMost(1.0)
    return result(g, accepted, ComplexReflection(re, im, if (g == 0.0) null else atan2(im, re) * 180 / PI))
}
