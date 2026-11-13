package local.antenna.calculator.core.layout

import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.ShorteningFactor

data class JPoleDimensions(
    val wavelength: Double,
    val radiator: Double,
    val stub: Double,
    val spacing: Double,
    val feedHeight: Double,
) {
    val longElement: Double get() = radiator + stub
}

/** Starting dimensions in metres. Confirm matching on the assembled antenna. */
fun calculateJPole(frequency: Frequency, factor: ShorteningFactor): JPoleDimensions {
    val wavelength = 299_800_000.0 / frequency.hertz
    val correctedLength = wavelength * (factor.tenThousandths / 10_000.0)
    return JPoleDimensions(wavelength, correctedLength / 2, correctedLength / 4,
        wavelength * 0.025, wavelength * 0.022)
}
