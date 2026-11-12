package local.antenna.calculator.core.layout

import org.junit.Assert.*
import org.junit.Test

class FullWaveLoopTest {
    @Test fun dimensionsAndFeedPointsFollowShape() {
        val square = requireNotNull(fullWaveLoop(LoopShape.SQUARE, LoopModel.IDEAL, perimeterMeters = 12.0))
        val triangle = requireNotNull(fullWaveLoop(LoopShape.TRIANGLE, LoopModel.IDEAL, perimeterMeters = 12.0))
        assertEquals(3.0, square.widthMeters, 0.0)
        assertEquals(3.0, square.heightMeters, 0.0)
        assertEquals(4.0, triangle.widthMeters, 0.0)
        assertEquals(3.464101615, triangle.heightMeters, 1e-9)
        assertEquals(24.982704833, triangle.frequencyMhz, 1e-9)
        assertTrue(LoopFeed.QUARTER_FROM_APEX.fits(LoopShape.TRIANGLE))
        assertFalse(LoopFeed.QUARTER_FROM_APEX.fits(LoopShape.SQUARE))
    }

    @Test fun forwardAndReverseUseTheSameModel() {
        val expected = mapOf(LoopModel.IDEAL to 21.112144929577466,
            LoopModel.EMPIRICAL to 21.572112676056338, LoopModel.CUSTOM to 23.223359422535212)
        expected.forEach { (model, perimeter) ->
            val layout = requireNotNull(fullWaveLoop(LoopShape.SQUARE, model, q = 1.1, frequencyMhz = 14.2))
            assertEquals(perimeter, layout.perimeterMeters, 1e-9)
            val inverse = requireNotNull(fullWaveLoop(LoopShape.TRIANGLE, model, q = 1.1, perimeterMeters = layout.perimeterMeters))
            assertEquals(14.2, inverse.frequencyMhz, 1e-9)
        }
    }

    @Test fun rejectsOutOfRangeOrAmbiguousInputs() {
        assertNull(fullWaveLoop(LoopShape.TRIANGLE, LoopModel.IDEAL, perimeterMeters = .003))
        assertNull(fullWaveLoop(LoopShape.SQUARE, LoopModel.IDEAL, perimeterMeters = 10_001.0))
        assertNull(fullWaveLoop(LoopShape.SQUARE, LoopModel.CUSTOM, q = 1.6, frequencyMhz = 14.2))
        assertNull(fullWaveLoop(LoopShape.SQUARE, LoopModel.IDEAL, frequencyMhz = Double.NaN))
        assertNull(fullWaveLoop(LoopShape.SQUARE, LoopModel.IDEAL, frequencyMhz = 14.2, perimeterMeters = 12.0))
    }

    @Test fun transformsAResistiveLoadAtTheSpecifiedFrequency() {
        val section = requireNotNull(quarterWaveSection(14.2, 112.5, 50.0, .66))
        assertEquals(75.0, section.impedanceOhms, 1e-9)
        assertEquals(3.483504, section.lengthMeters, 1e-6)
        assertNull(quarterWaveSection(14.2, 112.5, 50.0, 1.01))
    }
}
