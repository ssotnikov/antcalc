package local.antenna.calculator.core.sizing

import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.ShorteningFactor

/** Validated frequency in hertz and an explicit physical-length model coefficient. */
data class SizingInput(
    val frequency: Frequency,
    val factor: ShorteningFactor = ShorteningFactor.DEFAULT,
)

/**
 * Starting physical dimensions for straight conductors, expressed in meters.
 *
 * [ideal] is independent of the model coefficient. [dipoleTotalMeters] is the sum
 * of two equal conductors without the feed gap; [dipoleArmMeters] is one conductor.
 * Using the same coefficient for [monopoleMeters] and the dipole arm is an explicit
 * approximation. Ground plane, diameter, insulation and matching are not modeled.
 * [input] preserves the exact inputs associated with this result.
 */
data class SizingResult(
    val input: SizingInput,
    val ideal: IdealSizingResult,
    val monopoleMeters: Double,
    val dipoleTotalMeters: Double,
    val dipoleArmMeters: Double,
)

/** Estimates physical conductor lengths; rounding is exclusively a presentation concern. */
fun calculateSizing(input: SizingInput): SizingResult {
    val ideal = calculateIdealSizing(input.frequency)
    val factor = input.factor.tenThousandths.toDouble() / ShorteningFactor.MAX_TEN_THOUSANDTHS
    val total = ideal.dipoleTotalMeters * factor
    val arm = total / 2.0
    return SizingResult(input, ideal, arm, total, arm)
}
