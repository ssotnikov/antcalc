package local.antenna.calculator.nec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NecDiagnosticsTest {
    private val frequencies = listOf(144_000_000L, 146_000_000L)

    private fun point(i: Int, swr: Double, r: Double = 50.0, x: Double = 0.0) =
        OpenNec.Point(frequencies[i], r, x, swr, emptyList())

    @Test fun worstFrequencyMovesAndAnExactTieUsesTheLowerFrequency() {
        assertEquals(frequencies[1], worstNecSwr(listOf(point(0, 1.2), point(1, 1.5))).frequencyHz)
        assertEquals(frequencies[0], worstNecSwr(listOf(point(1, 1.5), point(0, 1.5))).frequencyHz)
    }

    @Test fun usesComplexDifferenceWithinOneMatchedFrequency() {
        val coarse = listOf(point(0, 1.0), point(1, 1.0))
        val fine = listOf(point(0, 1.0, r = 55.0), point(1, 1.0, x = 6.0))
        assertEquals(NecImpedanceShift(6.0, frequencies[1]), maximumNecShift(coarse, fine, frequencies))
        assertNull(maximumNecShift(coarse, fine.reversed(), frequencies))
    }

    @Test fun meshDirectionUsesTheSeparateWorstPointsOfEachGeometry() {
        val base = listOf(point(0, 1.0), point(1, 1.5))
        val improvement = listOf(point(0, 1.3), point(1, 1.0))
        val degradation = listOf(point(0, 1.6), point(1, 1.0))
        val result = checkNotNull(necDirectionRange(base, improvement, base, degradation, frequencies))
        assertEquals(-0.2, result.low, 1e-12)
        assertEquals(0.1, result.high, 1e-12)
        assertTrue(result.indeterminate)
        assertTrue(checkNotNull(necDirectionRange(base, improvement, base, base, frequencies)).indeterminate)
        assertNull(necDirectionRange(base, improvement, base, emptyList(), frequencies))
    }
}
