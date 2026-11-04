package local.antenna.calculator.core.input

import java.math.BigDecimal
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor

enum class FrequencyMode(val maximumHertz: Long, val stepHertz: Long) {
    STANDARD(Frequency.MAX_HERTZ, 1),
    ORIGINAL(999_999_000, 1_000),
}

/** Validation converts to canonical integers only after bounds and resolution are checked. */
object QuantityInput {
    fun frequency(
        text: String,
        unit: FrequencyUnit,
        mode: FrequencyMode = FrequencyMode.STANDARD,
    ): FieldState<Frequency> = parseScaled(
        text, unit.hertzPerUnit, Frequency.MIN_HERTZ, mode.maximumHertz, mode.stepHertz,
        Frequency::ofHertz,
    )

    fun physicalLength(text: String, unit: LengthUnit): FieldState<PhysicalLength> = parseScaled(
        text, unit.nanometersPerUnit, PhysicalLength.MIN_NANOMETERS, PhysicalLength.MAX_NANOMETERS,
        create = PhysicalLength::ofNanometers,
    )

    fun shorteningFactor(text: String): FieldState<ShorteningFactor> = parseScaled(
        text, 10_000, ShorteningFactor.MIN_TEN_THOUSANDTHS.toLong(),
        ShorteningFactor.MAX_TEN_THOUSANDTHS.toLong(),
        create = { ShorteningFactor.ofTenThousandths(it.toInt()) },
    )

    private fun <T> parseScaled(
        text: String,
        multiplier: Long,
        minimum: Long,
        maximum: Long,
        step: Long = 1,
        create: (Long) -> T,
    ): FieldState<T> = when (val parsed = PositiveDecimalParser.parse(text)) {
        FieldState.Empty -> FieldState.Empty
        FieldState.Incomplete -> FieldState.Incomplete
        is FieldState.Invalid -> parsed
        is FieldState.Valid -> {
            val canonical = parsed.value.multiply(BigDecimal.valueOf(multiplier)).stripTrailingZeros()
            when {
                canonical < BigDecimal.valueOf(minimum) || canonical > BigDecimal.valueOf(maximum) ->
                    FieldState.Invalid(InputError.OUT_OF_RANGE)
                canonical.scale() > 0 -> FieldState.Invalid(InputError.TOO_PRECISE)
                else -> {
                    val whole = canonical.longValueExact()
                    if (whole % step != 0L) FieldState.Invalid(InputError.TOO_PRECISE)
                    else FieldState.Valid(create(whole))
                }
            }
        }
    }
}
