package local.antenna.calculator.core

import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.core.TrapDipole.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrapDipoleTest {
    @Test fun publishedLengthsAndAllowanceApplyToBothArms() {
        val totals = listOf(27.54, 29.38, 29.464)
        Profile.entries.forEachIndexed { index, profile ->
            assertEquals(totals[index], 2 * profile.blanks(0.0)!!.sum(), 1e-10)
            assertEquals(totals[index] + .6, 2 * profile.blanks(50.0)!!.sum(), 1e-10)
        }
        assertNull(Profile.R2AUK.blanks(1000.001))
    }

    @Test fun solvesAllThreeLcQuantitiesAndBoundsTheResults() {
        val l = requireNotNull(TrapDipole.solve(Quantity.INDUCTANCE, 7.1, null, 100.0))
        assertEquals(5.02485537, l, 1e-8)
        assertEquals(7.1, TrapDipole.solve(Quantity.FREQUENCY, null, l, 100.0)!!, 1e-9)
        assertEquals(100.0, TrapDipole.solve(Quantity.CAPACITANCE, 7.1, l, null)!!, 1e-9)
        assertNull(TrapDipole.solve(Quantity.INDUCTANCE, 0.0, null, 100.0))
        assertNull(TrapDipole.solve(Quantity.INDUCTANCE, 1.0, null, 1.0))
        assertNull(TrapDipole.solve(Quantity.FREQUENCY, null, Double.NaN, 100.0))
    }

    @Test fun wheelerUsesWireCentresAndRejectsAStackedWinding() {
        assertEquals(5.01960784, TrapDipole.wheeler(49.8, 1.0, 106.68, 16.0)!!, 1e-8)
        assertNull(TrapDipole.wheeler(49.8, 1.0, 15.0, 16.0))
    }
}
