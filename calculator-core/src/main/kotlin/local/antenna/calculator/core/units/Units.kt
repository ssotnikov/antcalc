package local.antenna.calculator.core.units

import java.math.BigDecimal

enum class FrequencyUnit(val hertzPerUnit: Long) {
    KILOHERTZ(1_000),
    MEGAHERTZ(1_000_000),
    GIGAHERTZ(1_000_000_000),
}

enum class LengthUnit(val nanometersPerUnit: Long, val displayScale: Int) {
    MILLIMETERS(1_000_000, 1),
    CENTIMETERS(10_000_000, 2),
    METERS(1_000_000_000, 4),
}

/** Exact decimal conversions for editable fields, never rounded to display precision. */
fun Frequency.inUnit(unit: FrequencyUnit): BigDecimal =
    BigDecimal.valueOf(hertz).divide(BigDecimal.valueOf(unit.hertzPerUnit))

fun PhysicalLength.inUnit(unit: LengthUnit): BigDecimal =
    BigDecimal.valueOf(nanometers).divide(BigDecimal.valueOf(unit.nanometersPerUnit))
