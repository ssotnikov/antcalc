package local.antenna.calculator.core

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt

object Cantenna {
    const val LIGHT_SPEED = 299_792_458.0
    const val TE11_ROOT = 1.8411837813406593
    const val TM01_ROOT = 2.404825557695773

    data class Band(val lowHz: Double, val highHz: Double = lowHz) {
        init {
            require(lowHz.isFinite() && lowHz > 0 && highHz.isFinite() && highHz >= lowHz)
        }
    }

    data class Can(val diameter: Double, val depth: Double? = null, val tolerance: Double = 0.0,
                   val contact: Double? = null, val probeDiameter: Double? = null) {
        init {
            require(diameter.isFinite() && diameter > 0)
            require(depth == null || depth.isFinite() && depth > 0)
            require(tolerance.isFinite() && tolerance >= 0 && tolerance < diameter)
            require(contact == null || contact.isFinite() && contact >= 0)
            require(probeDiameter == null || probeDiameter.isFinite() && probeDiameter > 0)
        }
    }

    enum class Mode { BELOW_CUTOFF, TE11, HIGHER_MODES }

    data class Result(
        val te11Hz: Double,
        val tm01Hz: Double,
        val wavelength: Double,
        val guideWavelength: Double?,
        val probe: Double,
        val addedProbe: Double?,
        val clearance: Double?,
        val mode: Mode,
        val lowMarginHz: Double,
        val highMarginHz: Double,
        val toleranceOffsets: ClosedFloatingPointRange<Double>?,
    ) {
        val offset get() = guideWavelength?.div(4)
        val suggestedDepth get() = guideWavelength?.times(0.75)
        val bandFits get() = lowMarginHz > 0 && highMarginHz > 0
    }

    private fun cutoff(root: Double, diameter: Double) = root * LIGHT_SPEED / (PI * diameter)

    private fun wavelengthInGuide(hz: Double, cutoff: Double): Double? {
        if (hz <= cutoff) return null
        val ratio = cutoff / hz
        return LIGHT_SPEED / hz / sqrt(((hz - cutoff) / hz) * (1 + ratio))
    }

    fun analyze(can: Can, hz: Double, band: Band = Band(hz)): Result {
        require(hz.isFinite() && hz > 0)
        val te11 = cutoff(TE11_ROOT, can.diameter)
        val tm01 = cutoff(TM01_ROOT, can.diameter)
        val worstTe11 = cutoff(TE11_ROOT, can.diameter - can.tolerance)
        val worstTm01 = cutoff(TM01_ROOT, can.diameter + can.tolerance)
        val guide = wavelengthInGuide(hz, te11)
        val probe = LIGHT_SPEED / hz / 4
        val nearest = wavelengthInGuide(hz, cutoff(TE11_ROOT, can.diameter + can.tolerance))
        val farthest = wavelengthInGuide(hz, worstTe11)
        return Result(te11, tm01, LIGHT_SPEED / hz, guide, probe,
            can.contact?.let { (probe - it).takeIf { length -> length >= 0 } },
            guide?.let { wavelength -> can.depth?.minus(wavelength / 4) },
            when {
                hz <= te11 -> Mode.BELOW_CUTOFF
                hz >= tm01 -> Mode.HIGHER_MODES
                else -> Mode.TE11
            },
            band.lowHz - worstTe11, worstTm01 - band.highHz,
            if (nearest != null && farthest != null) nearest / 4..farthest / 4 else null,
        )
    }

    /** Both endpoints of the returned interval are excluded. */
    fun diameterRange(band: Band): Pair<Double, Double>? {
        val minimum = cutoff(TE11_ROOT, band.lowHz)
        val maximum = cutoff(TM01_ROOT, band.highHz)
        return (minimum to maximum).takeIf { minimum < maximum }
    }

    data class Sector(val slant: Double, val innerRadius: Double, val outerRadius: Double, val angle: Double)

    fun flare(throat: Double, mouth: Double, depth: Double): Sector? {
        if (!throat.isFinite() || !mouth.isFinite() || !depth.isFinite() ||
            throat <= 0 || mouth <= throat || depth <= 0) return null
        val step = (mouth - throat) / 2
        val slant = hypot(depth, step)
        return Sector(slant, slant * (throat / 2) / step, slant * (mouth / 2) / step, 360 * step / slant)
    }

    val focalRatios = listOf(0.50, 0.45, 0.40, 0.35, 0.30, 0.25)
    private val lowNoise = listOf(85.2, 90.8, 106.0, 113.6, 124.0, 128.0)
    private val highGain = listOf(100.8, 106.0, 116.0, 124.0, 132.0, null)

    fun ringPosition(index: Int, maximizeGain: Boolean): Double? =
        (if (maximizeGain) highGain else lowNoise).getOrNull(index)?.div(1000)
}
