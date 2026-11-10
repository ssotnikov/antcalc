package local.antenna.calculator.core.layout

import kotlin.math.abs
import kotlin.math.sin
import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.sizing.calculateSizing
import local.antenna.calculator.core.units.PhysicalLength

data class InvertedVResult(
    val armMeters: Double,
    val apexMeters: Double,
    val angleTenths: Int,
    val spanMeters: Double,
    val endHeightMeters: Double,
) {
    val wireMeters: Double get() = 2 * armMeters
}

fun invertedVLayout(arm: PhysicalLength, apexNanometers: Long, angleTenths: Int): InvertedVResult =
    layout(arm.nanometers / 1e9, apexNanometers, angleTenths)

fun invertedVLayout(estimate: SizingInput, apexNanometers: Long, angleTenths: Int): InvertedVResult =
    layout(calculateSizing(estimate).dipoleArmMeters, apexNanometers, angleTenths)

private fun layout(armMeters: Double, apexNanometers: Long, angleTenths: Int): InvertedVResult {
    require(apexNanometers in 0..PhysicalLength.MAX_NANOMETERS)
    require(angleTenths in 300..1800)
    val apexMeters = apexNanometers / 1e9
    val halfAngle = Math.toRadians(angleTenths / 20.0)
    val drop = armMeters * sin(Math.toRadians((1800 - angleTenths) / 20.0))
    val clearance = apexMeters - drop
    // Suppress only floating-point noise at contact, not physically negative heights.
    val endHeight = if (abs(clearance) <= 2 * Math.ulp(maxOf(apexMeters, drop))) 0.0 else clearance
    return InvertedVResult(armMeters, apexMeters, angleTenths, 2 * armMeters * sin(halfAngle), endHeight)
}
