package local.antenna.calculator.core.layout

import kotlin.math.log10
import local.antenna.calculator.core.units.Frequency

data class MoxonDimensions(
    val widthMm: Double,
    val drivenTailMm: Double,
    val gapMm: Double,
    val reflectorTailMm: Double,
    val depthMm: Double = drivenTailMm + gapMm + reflectorTailMm,
    val drivenLengthMm: Double? = null,
    val reflectorLengthMm: Double? = null,
) {
    val valuesMm get() = listOfNotNull(
        widthMm, drivenTailMm, gapMm, reflectorTailMm, depthMm, drivenLengthMm, reflectorLengthMm,
    )

    init {
        require((drivenLengthMm == null) == (reflectorLengthMm == null))
        require(valuesMm.all { it.isFinite() && it > 0 })
    }
}

/** Cebik's round-conductor geometry regression for a nominal 50-ohm feed, revision 1. */
object MoxonEstimate {
    const val MODEL = "moxon_calculated"
    const val REVISION = 1

    data class Result(val dimensions: MoxonDimensions, val nearUpperLimit: Boolean)

    fun calculate(frequency: Frequency, diameterMm: Double): Result? {
        val wavelengthMm = 299_792_458_000.0 / frequency.hertz
        val ratio = diameterMm / wavelengthMm
        if (ratio !in 1e-5..1e-2) return null
        val log = log10(ratio)
        val width = ((-.0008571428571 * log - .009571428571) * log + .3398571429) * wavelengthMm
        val driven = ((-.002142857143 * log - .02035714286) * log + .008285714286) * wavelengthMm
        val gap = ((.001809523381 * log + .01780952381) * log + .05164285714) * wavelengthMm
        val reflector = (.001 * log + .07178571429) * wavelengthMm
        return Result(
            MoxonDimensions(width, driven, gap, reflector, driven + gap + reflector, width + 2 * driven, width + 2 * reflector),
            nearUpperLimit = ratio >= .0075,
        )
    }
}
