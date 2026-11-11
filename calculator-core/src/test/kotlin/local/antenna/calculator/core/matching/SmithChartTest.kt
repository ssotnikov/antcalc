package local.antenna.calculator.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmithChartTest {
    @Test
    fun matchesReferenceLoadAndConjugate() {
        val load = SmithLoad(217.0, 74.0, 40.0)
        val conjugate = SmithLoad(217.0, 74.0, -40.0)
        assertEquals(0.506, load.reflection.magnitude, 0.001)
        assertEquals(3.04, (load.matching.swr as MatchingMeasure.Finite).value, 0.01)
        assertEquals(156.5, load.matching.complex!!.phaseDegrees!!, 0.1)
        assertEquals(load.reflection.real, conjugate.reflection.real, 1e-12)
        assertEquals(-load.reflection.imaginary, conjugate.reflection.imaginary, 1e-12)
        assertEquals(load, load.reflection.toSmithLoad(load.reference))
    }

    @Test
    fun losslessLineRotatesClockwiseAndRepeatsAtHalfWavelength() {
        val point = SmithPoint(0.5, 0.0)
        assertEquals(-0.5, point.alongLine(0.125).imaginary, 1e-12)
        assertEquals(-0.5, point.alongLine(0.25).real, 1e-12)
        assertEquals(0.5, point.alongLine(0.5).real, 1e-12)
        val moved = point.alongLine(0.375)
        assertEquals(point.magnitude, moved.magnitude, 1e-12)
        assertEquals(0.375, point.distanceToward(moved, 0.3), 1e-12)
        assertEquals(0.5, point.distanceToward(point, 0.49), 1e-12)
        assertEquals(0.0, point.distanceToward(point, 0.01), 1e-12)
        assertEquals(0.375, SmithPoint(0.0, 0.0).distanceToward(moved, 0.375), 1e-12)
    }

    @Test
    fun dragInverseStaysInsideTheInputDomain() {
        assertEquals(SmithLoad(50.0, 50.0, 0.0), SmithPoint(0.0, 0.0).toSmithLoad(50.0))
        assertEquals(SmithLoad(50.0, 0.0, 0.0), SmithPoint(-2.0, 0.0).toSmithLoad(50.0))
        assertEquals(SmithLoad(50.0, 500.0, 0.0), SmithPoint(1.0, 0.0).toSmithLoad(50.0))
        assertTrue(SmithPoint(0.999, -0.1).toSmithLoad(300.0).reactance in -500.0..0.0)
    }
}
