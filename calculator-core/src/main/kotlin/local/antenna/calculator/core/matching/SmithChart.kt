package local.antenna.calculator.core.matching

import kotlin.math.*

data class SmithPoint(val real: Double, val imaginary: Double) {
    val magnitude get() = hypot(real, imaginary)

    fun alongLine(distanceWavelengths: Double): SmithPoint {
        require(distanceWavelengths in 0.0..0.5)
        val angle = -4 * PI * distanceWavelengths
        return SmithPoint(real * cos(angle) - imaginary * sin(angle), real * sin(angle) + imaginary * cos(angle))
    }

    /** Find the distance toward the generator whose reflection has the requested phase. */
    fun distanceToward(target: SmithPoint, previousDistance: Double): Double {
        if (magnitude == 0.0 || target.magnitude < 0.001) return previousDistance
        val turns = (atan2(imaginary, real) - atan2(target.imaginary, target.real)) / (4 * PI)
        val distance = turns - floor(turns * 2) / 2
        return if (distance < 0.0005 && previousDistance > 0.25) 0.5 else distance
    }

    fun toSmithLoad(reference: Double): SmithLoad {
        val scale = maxOf(1.0, magnitude)
        val a = real / scale
        val b = imaginary / scale
        val divisor = (1 - a).pow(2) + b * b
        if (divisor == 0.0) return SmithLoad(reference, 500.0, 0.0)
        return SmithLoad(
            reference,
            round((reference * (1 - a * a - b * b) / divisor).coerceIn(0.0, 500.0) * 10) / 10,
            round((reference * 2 * b / divisor).coerceIn(-500.0, 500.0) * 10) / 10,
        )
    }
}

data class SmithLoad(val reference: Double, val resistance: Double, val reactance: Double) {
    init {
        require(reference in 10.0..300.0 && resistance in 0.0..500.0 && reactance in -500.0..500.0)
    }

    val matching = matchingFromImpedance(resistance, reactance, reference)
    val reflection = checkNotNull(matching.complex).let { SmithPoint(it.real, it.imaginary) }
}
