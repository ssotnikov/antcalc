package local.antenna.calculator.core.layout

import kotlin.math.log10

object YagiScaling {
    const val MODEL = "yagi_scaling"
    const val MAX_ELEMENTS = 24
    const val MAX_SNAPSHOT_VALUES = 5 + 4 * MAX_ELEMENTS
    const val MAX_LENGTH_METRES = 1000.0

    enum class Mode { FREQUENCY, DIAMETER, BOTH }
    enum class Geometry { FREE_ELEMENTS, MOUNTED }

    data class Element(
        val position: Double,
        val length: Double,
        val diameter: Double,
        val targetDiameter: Double? = null,
    )

    data class Result(val factor: Double, val elements: List<Element>, val feedGap: Double?)

    fun calculate(
        mode: Mode,
        geometry: Geometry,
        sourceHz: Long,
        targetHz: Long,
        elements: List<Element>,
        feedGap: Double? = null,
    ): Result? {
        if (sourceHz !in 100_000L..6_000_000_000L || targetHz !in 100_000L..6_000_000_000L ||
            (mode == Mode.DIAMETER && sourceHz != targetHz) ||
            (geometry == Geometry.MOUNTED && mode != Mode.FREQUENCY) ||
            elements.size !in 2..MAX_ELEMENTS || elements.first().position != 0.0 ||
            elements.zipWithNext().any { (a, b) -> b.position <= a.position } ||
            elements.any { it.position !in 0.0..MAX_LENGTH_METRES || !validElement(it.length, it.diameter) } ||
            (feedGap != null && !(feedGap >= 0 && feedGap < elements[1].length))
        ) return null

        val factor = sourceHz.toDouble() / targetHz
        val wavelength = 299_792_458.0 / targetHz
        val gap = feedGap?.times(factor)
        val scaled = elements.mapIndexed { index, element ->
            val position = element.position * factor
            val diameter = element.diameter * factor
            val length = element.length * factor
            val target = if (mode == Mode.FREQUENCY) diameter else element.targetDiameter ?: return null
            if (!validElement(length, diameter) || target !in Double.MIN_VALUE..MAX_LENGTH_METRES) return null
            // A split driven element retains its geometrically scaled span and gap.
            if (index == 1 && gap != null && gap > 0.0 && target != diameter) return null
            val adjusted = if (diameter == target) {
                length
            } else {
                correctedLength(wavelength, length, diameter, target) ?: return null
            }
            if (position > MAX_LENGTH_METRES || !validElement(adjusted, target)) return null
            Element(position, adjusted, target)
        }
        return Result(factor, scaled, gap)
    }

    private fun validElement(length: Double, diameter: Double) =
        length in Double.MIN_VALUE..MAX_LENGTH_METRES && diameter > 0 && diameter < length

    private fun correctedLength(wavelength: Double, length: Double, diameter: Double, target: Double): Double? {
        fun coefficient(d: Double) = 430.3 * log10(2 * wavelength / d) - 320
        val before = coefficient(diameter)
        val after = coefficient(target)
        if (!before.isFinite() || !after.isFinite() || before <= 0 || after <= 1e-8) return null
        return wavelength / 2 * (1 + before / after * (2 * length / wavelength - 1))
    }
}
