package local.antenna.calculator.ui

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import local.antenna.calculator.core.TrapDipole.Profile

internal fun trapDipoleVector(profile: Profile): AntennaVector {
    val drop = when (profile) {
        Profile.R2AUK -> 58f
        Profile.VK2EVB -> 45f
        Profile.AA4LR -> 9f
    }
    val total = profile.sections.sum()
    val rods = buildList {
        fun rod(a: AntennaPoint, b: AntennaPoint, width: Float, material: Int) {
            add(AntennaRod(a, b, width, material))
        }
        for (side in listOf(-1, 1)) {
            fun arm(s: Float, offset: Float = 0f, z: Float = 0f) =
                AntennaPoint(s * side, 28f - s * drop / 190f + offset, z)
            var begin = 6f
            var distance = 0.0
            profile.traps.forEachIndexed { index, trap ->
                distance += profile.sections[index]
                val center = (190 * distance / total).toFloat()
                val halfWidth = (trap.formerLengthMm?.times(.16) ?: (trap.turns * 1.7)).toFloat()
                val radius = (trap.diameterMm * .12).toFloat()
                val start = center - halfWidth
                val end = center + halfWidth
                rod(arm(begin), arm(start), 2.8f, 0)
                rod(arm(start - 2), arm(end + 2), radius * 1.55f, if (profile.coaxial) 3 else 1)
                for (edge in listOf(start, end)) {
                    rod(arm(edge - 1), arm(edge + 1), 2 * radius, 3)
                }
                val steps = ceil(trap.turns * 16).toInt()
                (0..steps).map { step ->
                    val fraction = step.toFloat() / steps
                    val angle = 2 * PI * trap.turns * fraction
                    arm(start + 2 * halfWidth * fraction, radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
                }.zipWithNext().forEach { (a, b) -> rod(a, b, if (profile.coaxial) 2.6f else 1.8f, if (profile.coaxial) 1 else 2) }
                if (!profile.coaxial) {
                    val y = -radius - 8
                    val leads = listOf(arm(start), arm(start, y), arm(end, y), arm(end))
                    leads.zipWithNext().forEach { (a, b) -> rod(a, b, 1.7f, 0) }
                    rod(arm(center, y, -2f), arm(center, y, 2f), 8f, 2)
                }
                begin = end
            }
            rod(arm(begin), arm(190f), 2.8f, 0)
            rod(arm(190f), arm(202f), 6f, 3)
            rod(arm(202f), arm(220f, -7f), 1.5f, 1)
        }
        rod(AntennaPoint(-6f, 28f, 0f), AntennaPoint(6f, 28f, 0f), 9f, 3)
        rod(AntennaPoint(0f, 25f, 0f), AntennaPoint(0f, 11f, 0f), 6f, 2)
        rod(AntennaPoint(0f, 11f, 0f), AntennaPoint(-5f, -47f, 9f), 3.6f, 1)
        if (profile == Profile.AA4LR) {
            for (index in 0 until 16) {
                val t = index * .027f
                val at = AntennaPoint(-5 * t, 11 - 58 * t, 9 * t)
                rod(at, at + AntennaPoint(0f, -1.1f, 0f), 5.2f, 3)
            }
        }
    }
    return AntennaVector(rods, emptyList())
}
