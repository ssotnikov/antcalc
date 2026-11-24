package local.antenna.calculator.nec

import local.antenna.calculator.core.matching.MatchingMeasure
import local.antenna.calculator.core.matching.matchingFromImpedance
import kotlin.math.hypot

internal data class NecPmlRadialSample(val eightLayers: OpenNec.Point, val twelveLayers: OpenNec.Point) {
    val deltaImpedance get() = hypot(
        twelveLayers.resistance - eightLayers.resistance,
        twelveLayers.reactance - eightLayers.reactance,
    )
    val deltaSwr get() = twelveLayers.swr - eightLayers.swr
}

internal object NecReferenceExamples {
    // AntCalc control fitted-radial12-contrast.csv, fixed geometry and source.
    val radial = listOf(
        NecPmlRadialSample(
            point(143_000_000, 29.78160969202366, 6.170185190589157),
            point(143_000_000, 29.78015625554852, 6.169313328158862),
        ),
        NecPmlRadialSample(
            point(147_000_000, 29.2932281249687, -2.4800314497613263),
            point(147_000_000, 29.294433754377515, -2.4788028157271307),
        ),
    )

    // Worst points from the 129-point spectrum.csv near 997 mm.
    val wire8 = NecWorstSwrChange(
        point(147_000_000, 26.5327, 1.46194),
        point(147_000_000, 26.5731, 1.64506),
    )
    val wire4 = NecWorstSwrChange(
        point(147_000_000, 25.577, 1.56138),
        point(147_000_000, 25.6159, 1.74189),
    )
    val pml8 = NecWorstSwrChange(
        point(143_000_000, 30.78222449864977, 11.267983689855503),
        point(143_000_000, 30.827137789190346, 11.493296205061382),
    )

    private fun point(hz: Long, r: Double, x: Double): OpenNec.Point {
        val swr = matchingFromImpedance(r, x, 50.0).swr as MatchingMeasure.Finite
        return OpenNec.Point(hz, r, x, swr.value, emptyList())
    }
}
