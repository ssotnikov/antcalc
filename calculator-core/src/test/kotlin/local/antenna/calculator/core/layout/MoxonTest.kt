package local.antenna.calculator.core.layout

import local.antenna.calculator.core.units.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoxonTest {
    @Test
    fun matchesPublishedMoxGenExamplesWithinTheirPrecision() {
        val hf = calculate(14_200_000, 1.29)
        val uhf = calculate(435_000_000, 2.052)
        checkGeometry(hf, listOf(7705.2, 1182.6, 184.3, 1426.6, 2793.5), 0.15)
        checkGeometry(uhf, listOf(9.73, 1.25, .49, 1.88, 3.62).map { it * 25.4 }, 0.16)
    }

    @Test
    fun agreesWithIndependentVhfCalculationAndKeepsElementLengths() {
        val dimensions = calculate(146_000_000, 4.7625)
        checkGeometry(dimensions, listOf(737.41689, 96.60118, 35.48583, 141.99296, 274.07997), 1e-5)
        assertEquals(dimensions.widthMm + 2 * dimensions.drivenTailMm, dimensions.drivenLengthMm!!, 1e-12)
        assertEquals(dimensions.widthMm + 2 * dimensions.reflectorTailMm, dimensions.reflectorLengthMm!!, 1e-12)
    }

    @Test
    fun limitsTheRegressionToThePublishedDiameterDomain() {
        val frequency = Frequency.ofHertz(299_792_458)
        assertNull(MoxonEstimate.calculate(frequency, 0.009999))
        assertNotNull(MoxonEstimate.calculate(frequency, 0.010001))
        assertTrue(checkNotNull(MoxonEstimate.calculate(frequency, 9.999)).nearUpperLimit)
        assertNull(MoxonEstimate.calculate(frequency, 10.001))
        assertNull(MoxonEstimate.calculate(frequency, Double.NaN))
    }

    private fun calculate(hertz: Long, diameterMm: Double) =
        checkNotNull(MoxonEstimate.calculate(Frequency.ofHertz(hertz), diameterMm)).dimensions

    private fun checkGeometry(dimensions: MoxonDimensions, expected: List<Double>, toleranceMm: Double) {
        expected.zip(dimensions.valuesMm).forEach { (reference, actual) -> assertEquals(reference, actual, toleranceMm) }
        assertEquals(dimensions.drivenTailMm + dimensions.gapMm + dimensions.reflectorTailMm, dimensions.depthMm, 1e-12)
    }
}
