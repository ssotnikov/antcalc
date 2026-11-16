package local.antenna.calculator.core

import kotlin.math.PI
import kotlin.math.sqrt

object TrapDipole {
    enum class Band(val meters: Int, val lowerMhz: Double, val upperMhz: Double) {
        M20(20, 14.0, 14.35), M40(40, 7.0, 7.2), M80(80, 3.5, 3.8)
    }

    enum class Quantity(val limits: ClosedFloatingPointRange<Double>) {
        FREQUENCY(1.0..50.0), INDUCTANCE(.01..1000.0), CAPACITANCE(1.0..10000.0)
    }

    data class Trap(
        val diameterMm: Double,
        val turns: Double,
        val formerLengthMm: Double? = null,
        val capacitancePf: Double? = null,
        val measuredMhz: Double? = null,
        val blankMeters: Double? = null,
        val stripNearMeters: Double? = null,
        val stripFarMeters: Double? = null,
    )

    enum class Profile(val id: String, val sections: List<Double>, val traps: List<Trap>) {
        R2AUK("r2auk-2019", listOf(4.85, 3.62, 5.30), listOf(Trap(41.30, 6.0, 45.0), Trap(57.15, 8.0, 50.0))),
        VK2EVB("vk2evb", listOf(5.09, 3.00, 6.60), listOf(Trap(43.0, 5.5, 70.0),
            Trap(43.0, 10.5, 120.0, blankMeters = 1.74, stripNearMeters = .02, stripFarMeters = .12))),
        AA4LR("aa4lr-2020", listOf(4.9784, 3.1496, 6.6040), listOf(
            Trap(82.6, 6.0, capacitancePf = 27.0, measuredMhz = 13.84),
            Trap(82.6, 9.75, capacitancePf = 56.0, measuredMhz = 6.88))),
        ;

        val coaxial get() = this != AA4LR

        fun blanks(allowanceMm: Double): List<Double>? =
            allowanceMm.takeIf { it in 0.0..1000.0 }?.let { mm -> sections.map { it + 2 * mm / 1000 } }
    }

    private val lcConstant = 1e6 / (4 * PI * PI)

    fun solve(unknown: Quantity, frequency: Double?, inductance: Double?, capacitance: Double?): Double? {
        val f = frequency?.takeIf { it in Quantity.FREQUENCY.limits }
        val l = inductance?.takeIf { it in Quantity.INDUCTANCE.limits }
        val c = capacitance?.takeIf { it in Quantity.CAPACITANCE.limits }
        val result = when (unknown) {
            Quantity.FREQUENCY -> sqrt(lcConstant / ((l ?: return null) * (c ?: return null)))
            Quantity.INDUCTANCE -> lcConstant / ((f ?: return null) * f * (c ?: return null))
            Quantity.CAPACITANCE -> lcConstant / ((f ?: return null) * f * (l ?: return null))
        }
        return result.takeIf { it in unknown.limits }
    }

    fun wheeler(formerMm: Double, wireMm: Double, windingMm: Double, turns: Double): Double? {
        if (formerMm !in 1.0..300.0 || wireMm !in .1..10.0 || windingMm !in 1.0..1000.0 ||
            turns !in 1.0..100.0 || windingMm < turns * wireMm) return null
        val d = (formerMm + wireMm) / 25.4
        val winding = windingMm / 25.4
        return turns * turns * d * d / (18 * d + 40 * winding)
    }
}
