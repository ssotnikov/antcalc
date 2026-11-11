package local.antenna.calculator.core.layout

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.sizing.calculateSizing
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor

sealed interface GroundPlaneResult {
    data object RadialsOverlap : GroundPlaneResult
    data object RadialsBelowGround : GroundPlaneResult

    data class Layout(
        val whipMeters: Double,
        val radialMeters: Double,
        val baseMeters: Double,
        val angleTenths: Int,
        val radiusMeters: Double,
        val dropMeters: Double,
        val endHeightMeters: Double,
    ) : GroundPlaneResult {
        val diameterMeters: Double get() = 2 * radiusMeters
        val tipHeightMeters: Double get() = baseMeters + whipMeters
        val wireMeters: Double get() = whipMeters + 4 * radialMeters
    }
}

fun groundPlaneLayout(whip: PhysicalLength, radial: PhysicalLength, baseNanometers: Long, angleTenths: Int): GroundPlaneResult =
    groundPlaneLayout(whip.nanometers / 1e9, radial.nanometers / 1e9, baseNanometers, angleTenths)

fun groundPlaneLayout(
    estimate: SizingInput, radialFactor: ShorteningFactor, baseNanometers: Long, angleTenths: Int,
): GroundPlaneResult {
    val sizing = calculateSizing(estimate)
    val radial = sizing.ideal.monopoleMeters * radialFactor.tenThousandths / 10_000
    return groundPlaneLayout(sizing.monopoleMeters, radial, baseNanometers, angleTenths)
}

private fun groundPlaneLayout(whip: Double, radial: Double, baseNanometers: Long, angleTenths: Int): GroundPlaneResult {
    require(baseNanometers in 0..PhysicalLength.MAX_NANOMETERS)
    require(angleTenths in 0..900)
    if (angleTenths == 900) return GroundPlaneResult.RadialsOverlap

    val base = baseNanometers / 1e9
    val angle = Math.toRadians(angleTenths / 10.0)
    val drop = radial * sin(angle)
    val clearance = base - drop
    val endHeight = if (abs(clearance) <= 2 * Math.ulp(maxOf(base, drop))) 0.0 else clearance
    if (endHeight < 0) return GroundPlaneResult.RadialsBelowGround
    return GroundPlaneResult.Layout(whip, radial, base, angleTenths, radial * cos(angle), drop, endHeight)
}
