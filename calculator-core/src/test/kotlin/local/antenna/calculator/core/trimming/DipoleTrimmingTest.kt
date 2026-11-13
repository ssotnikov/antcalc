package local.antenna.calculator.core.trimming

import kotlin.math.abs
import local.antenna.calculator.core.format.LengthFormatter
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DipoleTrimmingTest {
    private val input = TrimmingInput(
        actualLength = PhysicalLength.ofNanometers(1_000_000_000),
        measuredFrequency = Frequency.ofHertz(145_000_000),
        targetFrequency = Frequency.ofHertz(155_000_000),
        antennaType = AntennaType.SYMMETRIC_DIPOLE,
    )

    @Test
    fun `one meter dipole has the specified total and per arm adjustment`() {
        val result = calculateTrimming(input)
        val arm = requireNotNull(result.dipoleArmAdjustment)
        assertEquals(935.483870967742, result.newLengthMeters * 1000, 1e-6)
        assertEquals(-64.516129032258, result.deltaMeters * 1000, 1e-6)
        assertEquals(500.0, arm.currentLengthMeters * 1000, 0.0)
        assertEquals(467.741935483871, arm.newLengthMeters * 1000, 1e-6)
        assertEquals(-32.258064516129, arm.deltaMeters * 1000, 1e-6)
        assertEquals(AdjustmentDirection.SHORTEN, result.direction)
    }

    @Test
    fun `each arm is derived before formatting the total`() {
        val result = calculateTrimming(input)
        val arm = requireNotNull(result.dipoleArmAdjustment)
        assertEquals("65", LengthFormatter.format(abs(result.deltaMeters), LengthUnit.MILLIMETERS).number)
        assertEquals("32", LengthFormatter.format(abs(arm.deltaMeters), LengthUnit.MILLIMETERS).number)
        assertEquals("468", LengthFormatter.format(arm.newLengthMeters, LengthUnit.MILLIMETERS).number)
        assertEquals(result.deltaMeters, arm.deltaMeters * 2, 0.0)
    }

    @Test
    fun `dipole extension and equal frequencies preserve arm symmetry`() {
        for (target in listOf(135_000_000L, 145_000_000L, 155_000_000L)) {
            val result = calculateTrimming(input.copy(targetFrequency = Frequency.ofHertz(target)))
            val arm = requireNotNull(result.dipoleArmAdjustment)
            assertEquals(result.newLengthMeters, arm.newLengthMeters * 2, 0.0)
            assertEquals(result.deltaMeters, arm.deltaMeters * 2, 0.0)
            if (target == input.measuredFrequency.hertz) {
                assertEquals(AdjustmentDirection.UNCHANGED, result.direction)
                assertEquals(0.0, arm.deltaMeters, 0.0)
            }
        }
    }

    @Test
    fun `monopole output cannot be mistaken for per arm dipole data`() {
        assertNull(calculateTrimming(input.copy(antennaType = AntennaType.MONOPOLE)).dipoleArmAdjustment)
        assertNotNull(calculateTrimming(input).dipoleArmAdjustment)
    }
}
