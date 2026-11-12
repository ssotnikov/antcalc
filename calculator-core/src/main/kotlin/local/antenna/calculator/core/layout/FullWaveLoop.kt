package local.antenna.calculator.core.layout

import kotlin.math.sqrt

enum class LoopShape(val sides: Int) { SQUARE(4), TRIANGLE(3) }

enum class LoopFeed {
    BASE, CORNER, SIDE_MIDDLE, APEX, QUARTER_FROM_APEX;

    fun fits(shape: LoopShape): Boolean = when (this) {
        BASE, CORNER -> true
        SIDE_MIDDLE -> shape == LoopShape.SQUARE
        APEX, QUARTER_FROM_APEX -> shape == LoopShape.TRIANGLE
    }
}

enum class LoopModel { IDEAL, EMPIRICAL, CUSTOM }

const val FREE_SPACE_METERS_MHZ = 299.792458
const val MIN_LOOP_METERS = .004
const val MAX_LOOP_METERS = 10_000.0

data class LoopLayout(
    val perimeterMeters: Double,
    val frequencyMhz: Double,
    val sideMeters: Double,
    val heightMeters: Double,
) {
    val widthMeters get() = sideMeters
}

/** Supply exactly one input. Both directions use the same perimeter constant. */
fun fullWaveLoop(
    shape: LoopShape, model: LoopModel, q: Double? = null,
    frequencyMhz: Double? = null, perimeterMeters: Double? = null,
): LoopLayout? {
    if ((frequencyMhz == null) == (perimeterMeters == null)) return null
    val constant = when (model) {
        LoopModel.IDEAL -> FREE_SPACE_METERS_MHZ
        LoopModel.EMPIRICAL -> 306.324
        LoopModel.CUSTOM -> (q?.takeIf { it in .5..1.5 } ?: return null) * FREE_SPACE_METERS_MHZ
    }
    val perimeter = perimeterMeters ?: constant / checkNotNull(frequencyMhz)
    if (perimeter !in MIN_LOOP_METERS..MAX_LOOP_METERS) return null
    val frequency = frequencyMhz ?: constant / perimeter
    val side = perimeter / shape.sides
    val height = if (shape == LoopShape.TRIANGLE) side * sqrt(3.0) / 2 else side
    return LoopLayout(perimeter, frequency, side, height)
}

data class QuarterWaveSection(val impedanceOhms: Double, val lengthMeters: Double)

/** A lossless quarter-wave section for a resistive load at the specified frequency. */
fun quarterWaveSection(frequencyMhz: Double, loadOhms: Double, lineOhms: Double, vf: Double): QuarterWaveSection? {
    if (listOf(frequencyMhz, loadOhms, lineOhms, vf).any { !it.isFinite() || it <= 0 } || vf > 1) return null
    val impedance = sqrt(loadOhms) * sqrt(lineOhms)
    val length = (FREE_SPACE_METERS_MHZ / 4) * vf / frequencyMhz
    if (!impedance.isFinite() || !length.isFinite() || length <= 0) return null
    return QuarterWaveSection(impedance, length)
}
