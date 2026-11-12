package local.antenna.calculator.core.measurements

import org.junit.Assert.*
import org.junit.Test

class SwrBandwidthTest {
    private fun sweep(vararg real: Double) = S11Sweep(50.0, real.mapIndexed { index, value ->
        S11Point((100 + 10 * index) * 1e6, value, 0.0)
    })

    @Test fun findsTwoWindowsAndInterpolatesMagnitudeWithoutChangingSamples() {
        val source = sweep(0.5, 0.0, 0.5, 0.0, 0.5)
        val samples = source.points.toList()
        val result = checkNotNull(source.swrBandwidth(2.0))
        assertEquals(2, result.bands.size)
        for ((index, band) in result.bands.withIndex()) {
            val offset = index * 20e6
            assertEquals(103_333_333.333 + offset, band.lower.hertz, 1.0)
            assertEquals(116_666_666.667 + offset, band.upper.hertz, 1.0)
            assertEquals(13_333_333.333, band.widthHertz, 1.0)
            assertEquals(BandEdgeKind.ESTIMATED, band.lower.kind)
            assertEquals(BandEdgeKind.ESTIMATED, band.upper.kind)
            assertEquals(SwrMinimum(110e6 + offset, 1.0), band.minimum)
        }
        assertEquals(SwrMinimum(110e6, 1.0), result.minimum)
        assertEquals(samples, source.points)
    }

    @Test fun includesThresholdEqualityAndFlagsLimitedCoverage() {
        val exact = checkNotNull(sweep(0.5, 1.0 / 3.0, 0.5).swrBandwidth(2.0)).bands.single()
        assertEquals(BandEdge(110e6, BandEdgeKind.MEASURED), exact.lower)
        assertEquals(exact.lower, exact.upper)
        assertEquals(0.0, exact.widthHertz, 0.0)

        val covered = checkNotNull(sweep(0.1, 0.2, 0.0).swrBandwidth(2.0)).bands.single()
        assertEquals(BandEdge(100e6, BandEdgeKind.SWEEP_LIMIT), covered.lower)
        assertEquals(BandEdge(120e6, BandEdgeKind.SWEEP_LIMIT), covered.upper)
        assertEquals(20e6, covered.widthHertz, 0.0)
        assertTrue(checkNotNull(sweep(0.4, 0.5).swrBandwidth(2.0)).bands.isEmpty())
        assertNull(sweep(0.0).swrBandwidth(0.99))
        assertNull(sweep(0.0).swrBandwidth(1001.0))
        assertNull(sweep(0.0).swrBandwidth(Double.NaN))
        assertEquals(0.0, checkNotNull(sweep(0.0).swrBandwidth(1.0)).bands.single().widthHertz, 0.0)
    }

    @Test fun separatesGapsAndDoesNotUseAnOpenCircuitForReactance() {
        val result = checkNotNull(sweep(0.0, 1.1, 0.1, 1.0).swrBandwidth(2.0))
        assertEquals(1, result.excludedPoints)
        assertEquals(2, result.bands.size)
        assertEquals(BandEdge(100e6, BandEdgeKind.INVALID_NEIGHBOR), result.bands.first().upper)
        assertEquals(BandEdge(120e6, BandEdgeKind.INVALID_NEIGHBOR), result.bands.last().lower)
        assertEquals(listOf(ReactanceZero(100e6), ReactanceZero(120e6)), result.reactanceZeros)
        val openOnly = checkNotNull(sweep(1.0).swrBandwidth(2.0))
        assertEquals(Double.POSITIVE_INFINITY, openOnly.minimum?.swr)
        assertTrue(openOnly.reactanceZeros.isEmpty())
    }

    @Test fun findsReactanceCrossingsIndependentlyOfSwrMinimum() {
        val source = S11Sweep(50.0, listOf(
            S11Point(100e6, 0.0, -0.1),
            S11Point(110e6, 0.0, 0.3),
            S11Point(120e6, 0.0, 1.1),
            S11Point(130e6, 0.0, -0.2),
            S11Point(140e6, 0.2, 0.0),
            S11Point(150e6, 0.3, 0.0),
        ))
        val result = checkNotNull(source.swrBandwidth(2.0))
        assertEquals(100e6, result.minimum?.hertz ?: 0.0, 0.0)
        assertEquals(2, result.reactanceZeros.size)
        val leftX = 100.0 * -0.1 / 1.01
        val rightX = 100.0 * 0.3 / 1.09
        assertTrue(result.reactanceZeros.first().estimated)
        assertEquals(100e6 + 10e6 * -leftX / (rightX - leftX), result.reactanceZeros.first().firstHertz, 1.0)
        assertEquals(ReactanceZero(140e6, 150e6), result.reactanceZeros.last())
    }
}
