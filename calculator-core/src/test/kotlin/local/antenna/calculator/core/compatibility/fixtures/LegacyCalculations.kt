package local.antenna.calculator.core.compatibility

// Historical numerical regression fixtures, deliberately excluded from the application artifact.

import kotlin.math.abs

/** Exact source inputs for the original LEN algorithm; coefficients are explicitly restricted. */
data class LegacyLengthsInput(
    val frequency: LegacyFrequency,
    val coefficient: LegacyCoefficient = LegacyCoefficient.DEFAULT,
)

/**
 * Original LEN values in whole millimeters, including its intermediate truncation.
 * [correctedFullLengthMillimeters] is the original corrected L, not free-space wavelength.
 * The remaining properties reproduce the L/2, L/4 and 5/8 rows without correction.
 */
data class LegacyLengthsResult(
    val input: LegacyLengthsInput,
    val correctedFullLengthMillimeters: Long,
    val halfLengthMillimeters: Long,
    val quarterLengthMillimeters: Long,
    val fiveEighthsLengthMillimeters: Long,
)

/** The original TRIM command. ADD also represents zero change, matching its +ADD 0 output. */
enum class LegacyTrimCommand { ADD, CUT }

/** Original TRIM compares theoretical quarter-wave lengths; no measured physical length is supplied. */
data class LegacyTrimInput(
    val currentFrequency: LegacyFrequency,
    val targetFrequency: LegacyFrequency,
    val coefficient: LegacyCoefficient = LegacyCoefficient.DEFAULT,
)

/**
 * Original TRIM output in whole millimeters. Negative [deltaMillimeters] means CUT.
 * [amountMillimeters] is the absolute displayed amount; [command] uses ADD for zero.
 */
data class LegacyTrimResult(
    val input: LegacyTrimInput,
    val currentLengthMillimeters: Long,
    val targetLengthMillimeters: Long,
    val deltaMillimeters: Long,
) {
    val command: LegacyTrimCommand
        get() = if (deltaMillimeters >= 0) LegacyTrimCommand.ADD else LegacyTrimCommand.CUT
    val amountMillimeters: Long
        get() = abs(deltaMillimeters)
}

/** Reproduces the original floor operations, never using floating-point geometry or formatters. */
fun calculateLegacyLengths(input: LegacyLengthsInput): LegacyLengthsResult {
    val full = correctedFullLength(input.frequency, input.coefficient)
    return LegacyLengthsResult(input, full, full / 2L, full / 4L, (5L * full) / 8L)
}

/** Reproduces the original two-frequency theoretical comparison independently of practical tuning. */
fun calculateLegacyTrim(input: LegacyTrimInput): LegacyTrimResult {
    val current = correctedFullLength(input.currentFrequency, input.coefficient) / 4L
    val target = correctedFullLength(input.targetFrequency, input.coefficient) / 4L
    return LegacyTrimResult(input, current, target, target - current)
}

private fun correctedFullLength(frequency: LegacyFrequency, coefficient: LegacyCoefficient): Long =
    (3_000_000L * coefficient.percent) / frequency.kilohertz
