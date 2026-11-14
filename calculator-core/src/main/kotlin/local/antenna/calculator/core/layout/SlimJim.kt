package local.antenna.calculator.core.layout

import local.antenna.calculator.core.sizing.calculateIdealSizing
import local.antenna.calculator.core.units.Frequency

/** Published DU1ANV starting geometry. Vertical dimensions describe projections, including the bends. */
object SlimJimDu1anv {
    const val MODEL_ID = "slim_jim_du1anv"
    const val REVISION = 1
    val frequency = Frequency.ofHertz(145_000_000)
    const val heightMeters = 1.496
    const val stubMeters = 0.498
    const val gapMeters = 0.025
    const val spacingMeters = 0.025
    const val tapMeters = 3 * 0.0254
    const val wireStockMeters = 3.0
    val returnProjectionMeters = heightMeters - stubMeters - gapMeters
}

enum class SlimJimBand(hertz: Long) {
    TWO_METERS(145_000_000),
    SEVENTY_CENTIMETERS(435_000_000),
    MARINE_VHF(156_800_000),
    FOUR_METERS(70_000_000),
    SIX_METERS(51_000_000),
    TEN_METERS(28_500_000),
    TWENTY_METERS(14_200_000);

    val frequency = Frequency.ofHertz(hertz)
}

data class SlimJimEstimate(
    val wavelengthMeters: Double,
    val radiatorMeters: Double,
    val stubMeters: Double,
    val tapMeters: Double,
    val gapMeters: Double,
) {
    val heightMeters get() = radiatorMeters + stubMeters + gapMeters
}

fun estimateSlimJimM0ukd(frequency: Frequency, factor: Double): SlimJimEstimate {
    require(factor > 0 && factor <= 1)
    val wavelength = calculateIdealSizing(frequency).wavelengthMeters
    val shortened = wavelength * factor
    return SlimJimEstimate(wavelength, shortened / 2, shortened / 4, shortened / 40, wavelength / 100)
}

class SlimJimG0kya(frequency: Frequency) {
    private val scale = SlimJimBand.TEN_METERS.frequency.hertz.toDouble() / frequency.hertz
    val stockMeters = 7.48 * scale
    val gapStartMeters = 2.31 * scale
    val gapMeters = 0.06 * scale
    val gapEndMeters = gapStartMeters + gapMeters
    val tapMeters = 0.13 * scale
}

object Slimtenna4X4lh {
    const val HEIGHT_METERS = 10.5
    const val STUB_VF = 0.73

    fun stubMeters(frequency: Frequency) =
        calculateIdealSizing(frequency).monopoleMeters * STUB_VF
}
