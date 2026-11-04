package local.antenna.calculator.core.trimming

import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimmingWarningsTest {
    private fun tune(nanometers: Long, measured: Long, target: Long, type: AntennaType = AntennaType.MONOPOLE) =
        calculateTrimming(TrimmingInput(PhysicalLength.ofNanometers(nanometers), Frequency.ofHertz(measured), Frequency.ofHertz(target), type))

    @Test
    fun `exact ratio boundaries do not trigger the large change notice`() {
        for (measured in listOf(400_000_000L, 600_000_000L)) {
            assertTrue(tune(500_000_000, measured, 500_000_000).warnings.isEmpty())
        }
    }

    @Test
    fun `one hertz on either side of ratio thresholds is classified exactly`() {
        for ((measured, expected) in listOf(399_999_999L to true, 400_000_001L to false, 599_999_999L to false, 600_000_001L to true)) {
            assertEquals("measured=$measured", expected, TrimmingWarning.LARGE_FREQUENCY_CHANGE in tune(500_000_000, measured, 500_000_000).warnings)
        }
    }

    @Test
    fun `maximum length and frequency ratio return six hundred million meters without clipping`() {
        val result = tune(10_000_000_000_000, 6_000_000_000, 100_000)
        assertEquals(600_000_000.0, result.newLengthMeters, 1e-6)
        assertEquals(599_990_000.0, result.deltaMeters, 1e-6)
        assertEquals(AdjustmentDirection.LENGTHEN, result.direction)
        assertEquals(setOf(TrimmingWarning.LARGE_FREQUENCY_CHANGE, TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE), result.warnings)
    }

    @Test
    fun `minimum length and inverse extreme ratio remain a positive result below the input range`() {
        val result = tune(1_000_000, 100_000, 6_000_000_000)
        assertEquals(1.666666666666667e-8, result.newLengthMeters, 1e-20)
        assertTrue(result.deltaMeters < 0)
        assertEquals(setOf(TrimmingWarning.LARGE_FREQUENCY_CHANGE, TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE), result.warnings)
    }

    @Test
    fun `exact one millimeter result remains in range`() {
        val result = tune(1_250_000, 400_000_000, 500_000_000)
        assertEquals(0.001, result.newLengthMeters, 1e-15)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `one hertz perturbations of the lower output boundary are classified exactly`() {
        assertTrue(TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE in tune(1_250_000, 399_999_999, 500_000_000).warnings)
        assertFalse(TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE in tune(1_250_000, 400_000_001, 500_000_000).warnings)
    }

    @Test
    fun `ten thousand meter result and its neighbors are classified without Long overflow`() {
        val exact = tune(8_000_000_000_000, 500_000_000, 400_000_000)
        assertEquals(10_000.0, exact.newLengthMeters, 1e-9)
        assertFalse(TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE in exact.warnings)
        assertTrue(TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE in tune(8_000_000_000_000, 500_000_001, 400_000_000).warnings)
        assertFalse(TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE in tune(8_000_000_000_000, 499_999_999, 400_000_000).warnings)
    }

    @Test
    fun `equal frequencies preserve exact input boundaries for either antenna type`() {
        for (length in listOf(PhysicalLength.MIN_NANOMETERS, PhysicalLength.MAX_NANOMETERS)) {
            for (type in AntennaType.entries) {
                val result = tune(length, 6_000_000_000, 6_000_000_000, type)
                assertTrue(result.warnings.isEmpty())
                assertEquals(AdjustmentDirection.UNCHANGED, result.direction)
            }
        }
    }

    @Test
    fun `dipole notices apply to total length rather than either individual arm`() {
        val result = tune(1_000_000, 145_000_000, 145_000_000, AntennaType.SYMMETRIC_DIPOLE)
        assertEquals(0.0005, requireNotNull(result.dipoleArmAdjustment).newLengthMeters, 0.0)
        assertTrue(result.warnings.isEmpty())
    }
}
