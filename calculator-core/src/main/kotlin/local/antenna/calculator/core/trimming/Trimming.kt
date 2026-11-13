package local.antenna.calculator.core.trimming

import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor
import local.antenna.calculator.core.sizing.calculateIdealSizing

/** Antenna geometry for calculations and the project journal. */
enum class AntennaType {
    /** One straight radiator, with the ground plane outside the measured length. */
    MONOPOLE,
    /** Two equal straight conductors, measured by their sum without the feed gap. */
    SYMMETRIC_DIPOLE,
    /** A closed full-wave wire loop. */
    FULL_WAVE_LOOP,
    J_POLE;

    companion object {
        val straightConductors = listOf(MONOPOLE, SYMMETRIC_DIPOLE)
    }
}

/** The sign of a physical length change, determined from exact input frequencies. */
enum class AdjustmentDirection { SHORTEN, LENGTHEN, UNCHANGED }

/** Nonblocking notices. The UI supplies localized descriptions independently of input errors. */
enum class TrimmingWarning {
    /** The measured/target ratio is outside 0.8 to 1.2; a UI policy, not a physical accuracy bound. */
    LARGE_FREQUENCY_CHANGE,
    /** The calculated total conductor length is outside the measured-length input range. */
    RESULT_OUTSIDE_INPUT_LENGTH_RANGE,
    /** The measured length differs by more than 25% from the default fundamental-mode estimate. */
    LENGTH_DIFFERS_FROM_MODEL,
}

/**
 * Measured antenna data for proportional tuning without a shortening coefficient.
 *
 * [actualLength] is the monopole radiator length, or the sum of two equal dipole
 * conductors for [AntennaType.SYMMETRIC_DIPOLE]. Ground plane and feed gap are excluded.
 * [measuredFrequency] and [targetFrequency] are exact hertz, validated by [Frequency].
 * The approximation assumes the construction and installation remain the same.
 */
data class TrimmingInput(
    val actualLength: PhysicalLength,
    val measuredFrequency: Frequency,
    val targetFrequency: Frequency,
    val antennaType: AntennaType = AntennaType.MONOPOLE,
) {
    init { require(antennaType in AntennaType.straightConductors) }
}

/**
 * Dimensions for one arm of a symmetric dipole, in meters.
 *
 * The same signed [deltaMeters] applies to each arm. [currentLengthMeters] and
 * [newLengthMeters] are halves of the unrounded total conductor dimensions.
 */
data class DipoleArmAdjustment(
    val currentLengthMeters: Double,
    val newLengthMeters: Double,
    val deltaMeters: Double,
)

/**
 * An unrounded tuning estimate, in meters, with its exact source [input].
 *
 * [deltaMeters] is signed: negative means remove length, positive means add length.
 * [newLengthMeters] is the monopole radiator or the full dipole conductor sum,
 * depending on [TrimmingInput.antennaType]; it is not constrained to the input range.
 * [direction] must be used instead of comparing rounded displayed numbers.
 * [dipoleArmAdjustment] is present only for a symmetric dipole; total lengths and
 * total change above must never be interpreted as the change to each arm.
 * [warnings] are nonblocking model notices and never cause result clipping.
 */
data class TrimmingResult(
    val input: TrimmingInput,
    val newLengthMeters: Double,
    val deltaMeters: Double,
    val direction: AdjustmentDirection,
    val dipoleArmAdjustment: DipoleArmAdjustment?,
    val warnings: Set<TrimmingWarning>,
)

/**
 * Estimates the new physical length from actual length and measured/target hertz.
 * The signed change uses an algebraically equivalent frequency difference to avoid
 * cancellation when the frequencies are very close. No coefficient or display rounding applies.
 */
fun calculateTrimming(input: TrimmingInput): TrimmingResult {
    val actualMeters = input.actualLength.nanometers / 1_000_000_000.0
    val measured = input.measuredFrequency.hertz
    val target = input.targetFrequency.hertz
    val direction = when {
        measured < target -> AdjustmentDirection.SHORTEN
        measured > target -> AdjustmentDirection.LENGTHEN
        else -> AdjustmentDirection.UNCHANGED
    }
    val newLength = if (measured == target) actualMeters else actualMeters * measured.toDouble() / target
    val delta = if (measured == target) 0.0 else actualMeters * (measured - target).toDouble() / target
    val arm = if (input.antennaType == AntennaType.SYMMETRIC_DIPOLE) {
        DipoleArmAdjustment(actualMeters / 2.0, newLength / 2.0, delta / 2.0)
    } else null
    return TrimmingResult(input, newLength, delta, direction, arm, calculateWarnings(input))
}

private fun calculateWarnings(input: TrimmingInput): Set<TrimmingWarning> = buildSet {
    val measured = input.measuredFrequency.hertz
    val target = input.targetFrequency.hertz
    if (5L * measured < 4L * target || 5L * measured > 6L * target) {
        add(TrimmingWarning.LARGE_FREQUENCY_CHANGE)
    }

    // Cross-products exceed Long at valid extremes. BigInteger also preserves exact boundaries.
    val numerator = input.actualLength.nanometers.toBigInteger() * measured.toBigInteger()
    val lower = PhysicalLength.MIN_NANOMETERS.toBigInteger() * target.toBigInteger()
    val upper = PhysicalLength.MAX_NANOMETERS.toBigInteger() * target.toBigInteger()
    if (numerator < lower || numerator > upper) add(TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE)

    val ideal = calculateIdealSizing(input.measuredFrequency)
    val reference = when (input.antennaType) {
        AntennaType.MONOPOLE -> ideal.monopoleMeters
        AntennaType.SYMMETRIC_DIPOLE -> ideal.dipoleTotalMeters
        AntennaType.FULL_WAVE_LOOP, AntennaType.J_POLE -> error("Trimming requires a straight-conductor antenna")
    } * (ShorteningFactor.DEFAULT.tenThousandths / 10_000.0)
    val ratio = input.actualLength.nanometers / 1_000_000_000.0 / reference
    if (ratio < .75 || ratio > 1.25) add(TrimmingWarning.LENGTH_DIFFERS_FROM_MODEL)
}
