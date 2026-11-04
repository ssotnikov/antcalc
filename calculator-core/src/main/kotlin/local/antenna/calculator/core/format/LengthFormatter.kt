package local.antenna.calculator.core.format

import java.math.BigDecimal
import java.math.RoundingMode
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.inUnit

/** The UI supplies the unit label and the localized "less than" prefix. */
data class LengthDisplay(
    val number: String,
    val unit: LengthUnit,
    val isBelowResolution: Boolean,
)

object LengthFormatter {
    /** Formats nonnegative calculated meters; this does not alter the calculation. */
    fun format(meters: Double, unit: LengthUnit): LengthDisplay {
        require(meters.isFinite() && meters >= 0.0) { "Expected finite nonnegative meters" }
        val inUnit = BigDecimal.valueOf(meters).movePointRight(9)
            .divide(BigDecimal.valueOf(unit.nanometersPerUnit))
        return formatInUnit(inUnit, unit)
    }

    fun format(length: PhysicalLength, unit: LengthUnit): LengthDisplay =
        formatInUnit(length.inUnit(unit), unit)

    private fun formatInUnit(value: BigDecimal, unit: LengthUnit): LengthDisplay {
        val step = BigDecimal.ONE.movePointLeft(unit.displayScale)
        val belowResolution = value.signum() > 0 && value < step
        val displayed = if (belowResolution) step else value
        return LengthDisplay(
            number = displayed.setScale(unit.displayScale, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ','),
            unit = unit,
            isBelowResolution = belowResolution,
        )
    }
}
