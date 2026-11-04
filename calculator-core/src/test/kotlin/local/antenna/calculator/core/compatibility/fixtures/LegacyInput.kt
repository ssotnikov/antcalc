package local.antenna.calculator.core.compatibility

// Historical numerical regression fixtures, deliberately excluded from the application artifact.

import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.FrequencyMode
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.ShorteningFactor

/** Original-mode frequency in whole kilohertz, without silent truncation or clamping. */
@JvmInline
value class LegacyFrequency private constructor(val kilohertz: Long) {
    companion object {
        const val MIN_KILOHERTZ = 100L
        const val MAX_KILOHERTZ = 999_999L

        fun ofKilohertz(value: Long): LegacyFrequency {
            require(value in MIN_KILOHERTZ..MAX_KILOHERTZ) { "Frequency is outside the original range" }
            return LegacyFrequency(value)
        }

        fun fromFrequency(value: Frequency): LegacyFrequency {
            require(value.hertz % 1_000L == 0L) { "Original frequency requires whole kilohertz" }
            return ofKilohertz(value.hertz / 1_000L)
        }
    }
}

/** The original calculator's four model coefficients, stored as whole percentages. */
enum class LegacyCoefficient(val percent: Int) {
    PERCENT_100(100), PERCENT_97(97), PERCENT_95(95), PERCENT_93(93);

    companion object {
        val DEFAULT = PERCENT_95

        fun ofPercent(value: Int): LegacyCoefficient =
            entries.singleOrNull { it.percent == value }
                ?: throw IllegalArgumentException("Unsupported original coefficient")

        fun fromShorteningFactor(value: ShorteningFactor): LegacyCoefficient =
            entries.singleOrNull { it.percent * 100 == value.tenThousandths }
                ?: throw IllegalArgumentException("Custom coefficients are unavailable in original mode")
    }
}

/** Reuses decimal syntax rules but enforces the original range and one-kilohertz step. */
object LegacyInput {
    fun frequency(text: String, unit: FrequencyUnit): FieldState<LegacyFrequency> =
        when (val parsed = QuantityInput.frequency(text, unit, FrequencyMode.ORIGINAL)) {
            FieldState.Empty -> FieldState.Empty
            FieldState.Incomplete -> FieldState.Incomplete
            is FieldState.Invalid -> parsed
            is FieldState.Valid -> FieldState.Valid(LegacyFrequency.fromFrequency(parsed.value))
        }
}
