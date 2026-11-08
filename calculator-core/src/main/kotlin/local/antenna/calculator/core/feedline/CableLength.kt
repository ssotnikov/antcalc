package local.antenna.calculator.core.feedline

import java.math.BigDecimal
import java.math.MathContext
import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.units.*

enum class CableMode { ANGLE_TO_LENGTH, LENGTH_TO_ANGLE }
enum class CableField(val minimum: Long, val maximum: Long) {
    FREQUENCY(Frequency.MIN_HERTZ, Frequency.MAX_HERTZ), VELOCITY(1, 10_000),
    LENGTH(0, 10_000_000_000_000), ANGLE(0, 360_000_000)
}

/** A line's propagation speed; independent of any antenna shortening coefficient. */
@JvmInline
value class VelocityFactor private constructor(val tenThousandths: Int) {
    companion object {
        fun ofTenThousandths(value: Int): VelocityFactor {
            require(value in 1..10_000)
            return VelocityFactor(value)
        }
    }
}

/** A measured cable can have zero length, unlike an antenna conductor. */
@JvmInline
value class CableLength private constructor(val nanometers: Long) {
    companion object {
        fun ofNanometers(value: Long): CableLength {
            require(value in 0..10_000_000_000_000L)
            return CableLength(value)
        }
    }
}

fun cableMultiplier(field: CableField, frequencyUnit: FrequencyUnit, lengthUnit: LengthUnit): Long = when (field) {
    CableField.FREQUENCY -> frequencyUnit.hertzPerUnit
    CableField.VELOCITY -> 10_000
    CableField.LENGTH -> lengthUnit.nanometersPerUnit
    CableField.ANGLE -> 1000
}

/** Exact range and step checks before constructing canonical integers. */
fun parseCableNumber(text: String, field: CableField, frequencyUnit: FrequencyUnit, lengthUnit: LengthUnit): FieldState<Long> {
    val input = text.trim()
    if (input.isEmpty()) return FieldState.Empty
    if (input.length > 32) return FieldState.Invalid(InputError.TOO_LONG)
    if (input in listOf(".", ",") || Regex("[0-9]+[.,]").matches(input)) return FieldState.Incomplete
    if (!Regex("[0-9]+(?:[.,][0-9]+)?").matches(input)) return FieldState.Invalid(InputError.INVALID_NUMBER)
    val canonical = BigDecimal(input.replace(',', '.')).multiply(BigDecimal.valueOf(cableMultiplier(field, frequencyUnit, lengthUnit))).stripTrailingZeros()
    if (canonical < BigDecimal.valueOf(field.minimum) || canonical > BigDecimal.valueOf(field.maximum)) return FieldState.Invalid(InputError.OUT_OF_RANGE)
    if (canonical.scale() > 0) return FieldState.Invalid(InputError.TOO_PRECISE)
    return FieldState.Valid(canonical.longValueExact())
}

data class CableResult(
    val lengthMeters: BigDecimal,
    val fullDegrees: BigDecimal,
    val wrappedDegrees: BigDecimal,
    val delayNanoseconds: BigDecimal,
)

private val precision = MathContext.DECIMAL128
private val fullTurn = BigDecimal(360)
private fun speed(factor: VelocityFactor) = BigDecimal("299792458").multiply(BigDecimal.valueOf(factor.tenThousandths.toLong(), 4))

fun cableFromAngle(frequency: Frequency, factor: VelocityFactor, milliDegrees: Long): CableResult {
    require(milliDegrees in 0..360_000_000L)
    val degrees = BigDecimal.valueOf(milliDegrees, 3)
    val delaySeconds = degrees.divide(fullTurn.multiply(BigDecimal.valueOf(frequency.hertz)), precision)
    return CableResult(delaySeconds.multiply(speed(factor), precision), degrees, degrees.remainder(fullTurn), delaySeconds.movePointRight(9))
}

fun cableFromLength(frequency: Frequency, factor: VelocityFactor, length: CableLength): CableResult {
    val meters = BigDecimal.valueOf(length.nanometers, 9)
    val delaySeconds = meters.divide(speed(factor), precision)
    val degrees = meters.multiply(BigDecimal.valueOf(frequency.hertz)).multiply(fullTurn).divide(speed(factor), precision)
    // Compute the modulo before converting to a floating display value: many whole
    // turns must not erase the small fractional phase through cancellation.
    return CableResult(meters, degrees, degrees.remainder(fullTurn), delaySeconds.movePointRight(9))
}
