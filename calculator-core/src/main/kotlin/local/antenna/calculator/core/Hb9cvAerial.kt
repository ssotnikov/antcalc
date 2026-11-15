package local.antenna.calculator.core

/** Geometry and starting capacitance reconstructed from the 3G-Aerial HB9CV calculator. */
object Hb9cvAerial {
    const val MODEL = "hb9cv_3g_aerial"
    const val MIN_MHZ = 100.0
    const val MAX_MHZ = 1500.0
    const val MAX_OVERHANG_MM = 10_000.0
    private const val REFERENCE_MHZ = 144.0
    private const val WAVELENGTH_MM_MHZ = 299_792.458

    enum class Dimension(val referenceMm: Double) {
        LR(1026.0), LF(913.0), MR(188.0), MF(178.0), A(208.0),
        DIAMETER(12.7), GAMMA_DIAMETER(6.35), GAP(6.35);

        val key: String get() = "hb9cv_aerial_${name.lowercase()}"
    }

    data class Result(val frequencyMhz: Double) {
        init { require(frequencyMhz in MIN_MHZ..MAX_MHZ) }

        val wavelengthMeters get() = WAVELENGTH_MM_MHZ / frequencyMhz / 1000
        val capacitancePf get() = 2304.0 / frequencyMhz
        fun millimeters(dimension: Dimension) = dimension.referenceMm * REFERENCE_MHZ / frequencyMhz

        fun boom(rearMm: Double, frontMm: Double): Boom {
            require(rearMm in 0.0..MAX_OVERHANG_MM && frontMm in 0.0..MAX_OVERHANG_MM)
            val frontPosition = rearMm + millimeters(Dimension.A)
            return Boom(rearMm, frontPosition, frontPosition + frontMm)
        }
    }

    data class Boom(val rearPositionMm: Double, val frontPositionMm: Double, val lengthMm: Double)
}
