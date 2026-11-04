package local.antenna.calculator.core.trimming

import kotlin.math.abs
import local.antenna.calculator.core.format.LengthFormatter
import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.sizing.calculateSizing
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimmingTest {
    private val halfMeter = PhysicalLength.ofNanometers(500_000_000)
    private val lower = Frequency.ofHertz(145_000_000)
    private val upper = Frequency.ofHertz(155_000_000)

    @Test
    fun `higher target frequency shortens a measured half meter radiator`() {
        val input = TrimmingInput(halfMeter, lower, upper)
        val result = calculateTrimming(input)
        assertEquals(input, result.input)
        assertEquals(467.741935483871, result.newLengthMeters * 1000, 1e-6)
        assertEquals(-32.258064516129, result.deltaMeters * 1000, 1e-6)
        assertEquals(AdjustmentDirection.SHORTEN, result.direction)
        assertEquals("467,7", LengthFormatter.format(result.newLengthMeters, LengthUnit.MILLIMETERS).number)
        assertEquals("32,3", LengthFormatter.format(abs(result.deltaMeters), LengthUnit.MILLIMETERS).number)
    }

    @Test
    fun `lower target frequency lengthens a measured half meter radiator`() {
        val result = calculateTrimming(TrimmingInput(halfMeter, upper, lower))
        assertEquals(534.482758620690, result.newLengthMeters * 1000, 1e-6)
        assertEquals(34.482758620690, result.deltaMeters * 1000, 1e-6)
        assertEquals(AdjustmentDirection.LENGTHEN, result.direction)
        assertEquals("34,5", LengthFormatter.format(result.deltaMeters, LengthUnit.MILLIMETERS).number)
    }

    @Test
    fun `identical frequencies preserve the actual length with an explicit zero change`() {
        val result = calculateTrimming(TrimmingInput(halfMeter, lower, lower))
        assertEquals(0.5, result.newLengthMeters, 0.0)
        assertEquals(0.0, result.deltaMeters, 0.0)
        assertEquals(AdjustmentDirection.UNCHANGED, result.direction)
    }

    @Test
    fun `a one hertz change retains direction even when displayed length stays the same`() {
        val result = calculateTrimming(TrimmingInput(halfMeter, lower, Frequency.ofHertz(145_000_001)))
        assertEquals(AdjustmentDirection.SHORTEN, result.direction)
        assertTrue(result.deltaMeters < 0.0)
        assertTrue(LengthFormatter.format(abs(result.deltaMeters), LengthUnit.MILLIMETERS).isBelowResolution)
        assertEquals("500,0", LengthFormatter.format(result.newLengthMeters, LengthUnit.MILLIMETERS).number)
    }

    @Test
    fun `minimum actual length and high frequencies retain the smallest possible signed change`() {
        val input = TrimmingInput(PhysicalLength.ofNanometers(1_000_000), Frequency.ofHertz(5_999_999_999), Frequency.ofHertz(6_000_000_000))
        val result = calculateTrimming(input)
        assertEquals(-1.666666666666667e-13, result.deltaMeters, 1e-25)
        assertTrue(result.newLengthMeters > 0 && result.newLengthMeters < 0.001)
        assertEquals(AdjustmentDirection.SHORTEN, result.direction)
    }

    @Test
    fun `other calculations and shortening factors do not affect practical tuning`() {
        val input = TrimmingInput(halfMeter, lower, upper)
        val expected = calculateTrimming(input)
        for (factor in listOf(1, 9300, 9500, 9700, 10000)) {
            calculateSizing(SizingInput(lower, ShorteningFactor.ofTenThousandths(factor)))
            assertEquals(expected, calculateTrimming(input))
        }
    }

    @Test
    fun `proportional adjustment scales with the measured length`() {
        val first = calculateTrimming(TrimmingInput(halfMeter, lower, upper))
        val second = calculateTrimming(TrimmingInput(PhysicalLength.ofNanometers(1_000_000_000), lower, upper))
        assertEquals(first.newLengthMeters * 2, second.newLengthMeters, 1e-12)
        assertEquals(first.deltaMeters * 2, second.deltaMeters, 1e-12)
    }
}
