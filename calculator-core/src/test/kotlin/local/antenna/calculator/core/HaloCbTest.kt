package local.antenna.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloCbTest {
    @Test fun comparesBothShapesAtCbFrequencies() {
        val examples = listOf(
            Triple(26.965, 1.804848, 1.417524),
            Triple(27.185, 1.790242, 1.406053),
            Triple(27.405, 1.775870, 1.394765),
        )
        for ((mhz, diameter, side) in examples) {
            val round = requireNotNull(HaloCb.calculate(HaloCb.Shape.ROUND, mhz, 1.02, 200.0))
            val square = requireNotNull(HaloCb.calculate(HaloCb.Shape.SQUARE, mhz, 1.02, 200.0))
            assertEquals(diameter, round.span, 0.000001)
            assertEquals(side, square.span, 0.000001)
            assertEquals(round.perimeter, square.perimeter, 0.0)
            assertEquals(square.perimeter - 0.2, square.conductor, 1e-12)
        }
    }

    @Test fun usesTheMissingArcAndRejectsOutOfRangeValues() {
        val round = requireNotNull(HaloCb.calculate(HaloCb.Shape.ROUND, 27.185, 1.02, 400.0))
        val square = HaloCb.calculate(HaloCb.Shape.SQUARE, 27.185, 1.02, 400.0)
        assertTrue(round.perimeter - round.conductor > .4)
        assertNull(square)
        assertNull(HaloCb.calculate(HaloCb.Shape.ROUND, 27.185, 1.02, .99))
        assertNull(HaloCb.calculate(HaloCb.Shape.ROUND, 27.185, 1.02, Double.NaN))
        assertNull(HaloCb.calculate(HaloCb.Shape.ROUND, 28.001, 1.02, 200.0))
        assertNull(HaloCb.calculate(HaloCb.Shape.ROUND, 27.185, 1.101, 200.0))
    }
}
