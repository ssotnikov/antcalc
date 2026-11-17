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

    @Test fun directorScaledFrom144Point1To50Point1MatchesReferenceForThreeDiameters() {
        val original = listOf(Element(0.0, 1.04, .010), Element(.215, .970, .010), Element(.415, .900, .010))
        val source = 144_100_000L
        val target = 50_100_000L
        val scaled = requireNotNull(YagiScaling.calculate(Mode.FREQUENCY, Geometry.FREE_ELEMENTS, source, target, original))
        val director = scaled.elements.last()
        assertEquals(2588.6228, director.length * 1000, .00005)
        assertEquals(28.7625, director.diameter * 1000, .00005)
        for ((diameterMm, expectedLengthMm) in listOf(10 to 2667.8967, 12 to 2656.5192, 15 to 2641.4581)) {
            val targetDiameter = diameterMm / 1000.0
            val separate = requireNotNull(YagiScaling.calculate(Mode.DIAMETER, Geometry.FREE_ELEMENTS, target, target,
                scaled.elements.map { it.copy(targetDiameter = targetDiameter) }))
            val combined = requireNotNull(YagiScaling.calculate(Mode.BOTH, Geometry.FREE_ELEMENTS, source, target,
                original.map { it.copy(targetDiameter = targetDiameter) }))
            assertEquals(separate.elements, combined.elements)
            assertEquals(expectedLengthMm, combined.elements.last().length * 1000, .00005)
        }
    }

    @Test fun diameterChangeCanShortenAnElementWithoutMovingIt() {
        val original = listOf(Element(0.0, 3.2, .028762475, .010), Element(1.0, 3.1, .028762475, .012))
        val result = requireNotNull(YagiScaling.calculate(Mode.DIAMETER, Geometry.FREE_ELEMENTS, 50_100_000, 50_100_000, original))
        assertEquals(original.map { it.position }, result.elements.map { it.position })
        assertEquals(listOf(.010, .012), result.elements.map { it.diameter })
        assertEquals(3081.8088, result.elements[1].length * 1000, .00005)
    }

    @Test fun mountedDimensionsOnlyPermitProportionalScalingAndGapMovesWithDrivenElement() {
        assertNull(YagiScaling.calculate(Mode.DIAMETER, Geometry.MOUNTED, 50_000_000, 50_000_000, elements))
        assertNull(YagiScaling.calculate(Mode.BOTH, Geometry.MOUNTED, 50_000_000, 100_000_000, elements))
        val result = requireNotNull(YagiScaling.calculate(Mode.FREQUENCY, Geometry.MOUNTED, 50_000_000, 100_000_000, elements, .01))
        assertEquals(.005, requireNotNull(result.feedGap), 1e-12)
        assertEquals(1.225, result.elements[1].length, 1e-12)
        assertNull(YagiScaling.calculate(Mode.FREQUENCY, Geometry.MOUNTED, 50_000_000, 100_000_000,
            listOf(elements[0], elements[1].copy(position = 0.0))))
    }
}
