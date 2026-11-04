package local.antenna.calculator.core.compatibility

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyCalculationsTest {
    @Test
    fun `original lower bound uses whole millimeter integer geometry`() {
        val result = calculateLegacyLengths(LegacyLengthsInput(LegacyFrequency.ofKilohertz(100), LegacyCoefficient.PERCENT_100))
        assertEquals(3_000_000L, result.correctedFullLengthMillimeters)
        assertEquals(1_500_000L, result.halfLengthMillimeters)
        assertEquals(750_000L, result.quarterLengthMillimeters)
        assertEquals(1_875_000L, result.fiveEighthsLengthMillimeters)
    }

    @Test
    fun `original upper bound retains odd truncated fractions`() {
        val result = calculateLegacyLengths(LegacyLengthsInput(LegacyFrequency.ofKilohertz(999_999), LegacyCoefficient.PERCENT_93))
        assertEquals(279L, result.correctedFullLengthMillimeters)
        assertEquals(139L, result.halfLengthMillimeters)
        assertEquals(69L, result.quarterLengthMillimeters)
        assertEquals(174L, result.fiveEighthsLengthMillimeters)
    }

    @Test
    fun `default coefficient returns the original 145 MHz lengths`() {
        val input = LegacyLengthsInput(LegacyFrequency.ofKilohertz(145_000))
        val result = calculateLegacyLengths(input)
        assertEquals(input, result.input)
        assertEquals(1965L, result.correctedFullLengthMillimeters)
        assertEquals(982L, result.halfLengthMillimeters)
        assertEquals(491L, result.quarterLengthMillimeters)
        assertEquals(1228L, result.fiveEighthsLengthMillimeters)
    }

    @Test
    fun `trim reproduces cut add and zero add commands`() {
        val lower = LegacyFrequency.ofKilohertz(145_000)
        val upper = LegacyFrequency.ofKilohertz(155_000)
        val cut = calculateLegacyTrim(LegacyTrimInput(lower, upper))
        val add = calculateLegacyTrim(LegacyTrimInput(upper, lower))
        val zero = calculateLegacyTrim(LegacyTrimInput(lower, lower))
        assertEquals(491L, cut.currentLengthMillimeters)
        assertEquals(459L, cut.targetLengthMillimeters)
        assertEquals(-32L, cut.deltaMillimeters)
        assertEquals(32L, cut.amountMillimeters)
        assertEquals(LegacyTrimCommand.CUT, cut.command)
        assertEquals(32L, add.deltaMillimeters)
        assertEquals(LegacyTrimCommand.ADD, add.command)
        assertEquals(0L, zero.amountMillimeters)
        assertEquals(LegacyTrimCommand.ADD, zero.command)
    }
}
