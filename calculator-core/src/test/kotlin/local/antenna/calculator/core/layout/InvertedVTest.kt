package local.antenna.calculator.core.layout

import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.sizing.calculateSizing
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import org.junit.Assert.*
import org.junit.Test

class InvertedVTest {
    private val arm = PhysicalLength.ofNanometers(5_000_000_000)

    @Test fun controlGeometryAndFlatLimit() {
        val layout = invertedVLayout(arm, 8_000_000_000, 1200)
        assertEquals(8.660254037844386, layout.spanMeters, 1e-9)
        assertEquals(5.5, layout.endHeightMeters, 1e-9)
        assertEquals(10.0, layout.wireMeters, 0.0)
        val flat = invertedVLayout(arm, 8_000_000_000, 1800)
        assertEquals(10.0, flat.spanMeters, 0.0)
        assertEquals(8.0, flat.endHeightMeters, 0.0)
        assertTrue(invertedVLayout(arm, 8_000_000_000, 300).spanMeters < layout.spanMeters)
    }

    @Test fun groundContactIsDistinctFromOneNanometerBelow() {
        assertEquals(-1.5, invertedVLayout(arm, 1_000_000_000, 1200).endHeightMeters, 1e-9)
        assertEquals(0.0, invertedVLayout(arm, 2_500_000_000, 1200).endHeightMeters, 0.0)
        assertTrue(invertedVLayout(arm, 2_499_999_999, 1200).endHeightMeters < 0)
    }

    @Test fun changingAngleDoesNotModifyTheSizingEstimate() {
        val estimate = SizingInput(Frequency.ofHertz(14_200_000))
        val length = calculateSizing(estimate).dipoleArmMeters
        assertEquals(length, invertedVLayout(estimate, 8_000_000_000, 900).armMeters, 0.0)
        assertEquals(length, invertedVLayout(estimate, 8_000_000_000, 1200).armMeters, 0.0)
    }
}
