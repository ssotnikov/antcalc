package local.antenna.calculator.core.sizing

import kotlin.random.Random
import local.antenna.calculator.core.format.LengthFormatter
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.ShorteningFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizingPropertiesTest {
    @Test
    fun `433 MHz matches the independent reference`() {
        val result = calculateSizing(SizingInput(Frequency.ofHertz(433_000_000)))
        assertEquals(0.164435817032333, result.monopoleMeters, 1e-9)
        assertEquals(0.328871634064665, result.dipoleTotalMeters, 1e-9)
    }

    @Test
    fun `changing factor never changes ideal geometry`() {
        val frequency = Frequency.ofHertz(145_000_000)
        val expected = calculateIdealSizing(frequency)
        for (coefficient in listOf(1, 9300, 9500, 9539, 9700, 10000)) {
            val input = SizingInput(frequency, ShorteningFactor.ofTenThousandths(coefficient))
            val result = calculateSizing(input)
            assertEquals(input, result.input)
            assertEquals(expected, result.ideal)
        }
    }

    @Test
    fun `unity gives the ideal physical fractions`() {
        val result = calculateSizing(SizingInput(Frequency.ofHertz(145_000_000), ShorteningFactor.ofTenThousandths(10000)))
        assertEquals(result.ideal.monopoleMeters, result.monopoleMeters, 0.0)
        assertEquals(result.ideal.dipoleTotalMeters, result.dipoleTotalMeters, 0.0)
        assertEquals(result.ideal.dipoleArmMeters, result.dipoleArmMeters, 0.0)
    }

    @Test
    fun `physical results preserve precision until formatted`() {
        val result = calculateSizing(SizingInput(Frequency.ofHertz(145_000_000)))
        assertEquals("491", LengthFormatter.format(result.monopoleMeters, LengthUnit.MILLIMETERS).number)
        assertEquals("982", LengthFormatter.format(result.dipoleTotalMeters, LengthUnit.MILLIMETERS).number)
        assertEquals(0.982078741724138, result.dipoleTotalMeters, 1e-12)
    }

    @Test
    fun `deterministic samples preserve symmetry frequency scaling and coefficient linearity`() {
        val random = Random(145433)
        repeat(200) {
            val frequency = Frequency.ofHertz(random.nextLong(Frequency.MIN_HERTZ, Frequency.MAX_HERTZ / 2 + 1))
            val halfCoefficient = random.nextInt(1, 5001)
            val input = SizingInput(frequency, ShorteningFactor.ofTenThousandths(halfCoefficient))
            val result = calculateSizing(input)
            val doubledFrequency = calculateSizing(input.copy(frequency = Frequency.ofHertz(frequency.hertz * 2)))
            val doubledFactor = calculateSizing(input.copy(factor = ShorteningFactor.ofTenThousandths(halfCoefficient * 2)))
            assertEquals(2.0, result.dipoleTotalMeters / result.dipoleArmMeters, 2e-12)
            assertEquals(result.monopoleMeters, result.dipoleArmMeters, 0.0)
            assertEquals(result.dipoleTotalMeters / 2, doubledFrequency.dipoleTotalMeters, result.dipoleTotalMeters * 1e-10)
            assertEquals(result.dipoleTotalMeters * 2, doubledFactor.dipoleTotalMeters, result.dipoleTotalMeters * 1e-10)
            assertTrue(result.dipoleArmMeters.isFinite() && result.dipoleArmMeters > 0)
        }
    }
}
