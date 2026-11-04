package local.antenna.calculator.core.sizing

import local.antenna.calculator.core.units.Frequency

private const val SPEED_OF_LIGHT_METERS_PER_SECOND = 299_792_458.0

/**
 * Free-space reference geometry, with every length expressed in meters.
 *
 * These are ideal wavelength fractions, not guaranteed resonant conductor lengths.
 * [dipoleTotalMeters] excludes the feed gap; [dipoleArmMeters] is one of two equal arms.
 * [monopoleMeters] describes only the radiator; ground plane dimensions are not computed.
 * [fiveEighthsMeters] is a geometric reference, not a complete matched antenna design.
 */
data class IdealSizingResult(
    val wavelengthMeters: Double,
    val monopoleMeters: Double,
    val dipoleTotalMeters: Double,
    val dipoleArmMeters: Double,
    val fiveEighthsMeters: Double,
)

/** Calculates free-space dimensions from whole hertz, independently of any shortening factor. */
fun calculateIdealSizing(frequency: Frequency): IdealSizingResult {
    val wavelength = SPEED_OF_LIGHT_METERS_PER_SECOND / frequency.hertz
    val totalDipole = wavelength / 2.0
    val arm = totalDipole / 2.0
    return IdealSizingResult(wavelength, arm, totalDipole, arm, wavelength * 5.0 / 8.0)
}
