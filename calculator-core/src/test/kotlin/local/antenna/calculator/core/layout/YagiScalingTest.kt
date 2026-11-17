package local.antenna.calculator.core.layout

import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.layout.YagiScaling.Geometry
import local.antenna.calculator.core.layout.YagiScaling.Mode
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class YagiScalingTest {
    private val elements = listOf(Element(0.0, 2.746, .015875, .012), Element(1.0, 2.450, .015875, .012))

    @Test fun publishedDiameterExamplesKeepTheirPositions() {
        val result = requireNotNull(YagiScaling.calculate(Mode.DIAMETER, Geometry.FREE_ELEMENTS, 50_100_000, 50_100_000, elements))
        assertEquals(2.7593, result.elements[0].length, .00005)
        assertEquals(2.4792, result.elements[1].length, .00005)
        assertEquals(1.0, result.elements[1].position, 0.0)
    }

    @Test fun combinedModeAppliesDiameterCorrectionAfterSimilarity() {
        val scaled = requireNotNull(YagiScaling.calculate(Mode.FREQUENCY, Geometry.FREE_ELEMENTS, 50_100_000, 144_100_000, elements))
        val adjusted = requireNotNull(YagiScaling.calculate(Mode.DIAMETER, Geometry.FREE_ELEMENTS, 144_100_000, 144_100_000,
            scaled.elements.map { it.copy(targetDiameter = .006) }))
        val combined = requireNotNull(YagiScaling.calculate(Mode.BOTH, Geometry.FREE_ELEMENTS, 50_100_000, 144_100_000,
            elements.map { it.copy(targetDiameter = .006) }))
        assertEquals(adjusted.elements, combined.elements)
    }

    @Test fun mountedDimensionsOnlyPermitProportionalScalingAndGapMovesWithDrivenElement() {
        assertNull(YagiScaling.calculate(Mode.DIAMETER, Geometry.MOUNTED, 50_000_000, 50_000_000, elements))
        val result = requireNotNull(YagiScaling.calculate(Mode.FREQUENCY, Geometry.MOUNTED, 50_000_000, 100_000_000, elements, .01))
        assertEquals(.005, requireNotNull(result.feedGap), 1e-12)
        assertEquals(1.225, result.elements[1].length, 1e-12)
        assertNull(YagiScaling.calculate(Mode.FREQUENCY, Geometry.MOUNTED, 50_000_000, 100_000_000,
            listOf(elements[0], elements[1].copy(position = 0.0))))
    }
}
