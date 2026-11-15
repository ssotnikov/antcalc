package local.antenna.calculator.core

import kotlin.math.PI
import kotlin.math.asin

object HaloCb {
    enum class Shape { ROUND, SQUARE }

    data class Geometry(val wavelength: Double, val perimeter: Double, val span: Double, val conductor: Double)

    fun calculate(shape: Shape, mhz: Double, factor: Double, gapMm: Double): Geometry? {
        if (mhz !in 26.0..28.0 || factor !in 0.90..1.10) return null
        val wavelength = 299.792458 / mhz
        val perimeter = factor * wavelength / 2
        val span = perimeter / if (shape == Shape.ROUND) PI else 4.0
        val gap = gapMm / 1000
        if (gapMm < 1 || gap !in 0.001..(span / 4)) return null
        val interrupted = if (shape == Shape.ROUND) span * asin(gap / span) else gap
        return Geometry(wavelength, perimeter, span, perimeter - interrupted)
    }
}
