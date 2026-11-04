package local.antenna.calculator.core.compatibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LegacyRoundingTest {
    @Test
    fun `five eighths keeps the intermediate truncation rather than dividing the combined fraction`() {
        val frequency = LegacyFrequency.ofKilohertz(145_000)
        val unity = calculateLegacyLengths(LegacyLengthsInput(frequency, LegacyCoefficient.PERCENT_100))
        val shortened = calculateLegacyLengths(LegacyLengthsInput(frequency, LegacyCoefficient.PERCENT_97))
        // Independent integer references: direct combined division would yield 1293 and 1254.
        assertEquals(2068L, unity.correctedFullLengthMillimeters)
        assertEquals(1292L, unity.fiveEighthsLengthMillimeters)
        assertNotEquals(1293L, unity.fiveEighthsLengthMillimeters)
        assertEquals(2006L, shortened.correctedFullLengthMillimeters)
        assertEquals(1253L, shortened.fiveEighthsLengthMillimeters)
        assertNotEquals(1254L, shortened.fiveEighthsLengthMillimeters)
    }

    @Test
    fun `half length is not normalized to twice the truncated quarter length`() {
        val result = calculateLegacyLengths(LegacyLengthsInput(LegacyFrequency.ofKilohertz(145_000), LegacyCoefficient.PERCENT_97))
        assertEquals(1003L, result.halfLengthMillimeters)
        assertEquals(501L, result.quarterLengthMillimeters)
        assertEquals(1002L, result.quarterLengthMillimeters * 2)
        assertNotEquals(result.halfLengthMillimeters, result.quarterLengthMillimeters * 2)
    }

    @Test
    fun `quarter length uses floor rather than rounding to the nearest millimeter`() {
        val result = calculateLegacyLengths(LegacyLengthsInput(LegacyFrequency.ofKilohertz(145_000), LegacyCoefficient.PERCENT_97))
        assertEquals(501L, result.quarterLengthMillimeters)
        assertNotEquals(502L, result.quarterLengthMillimeters)
    }

    @Test
    fun `different frequencies with equal truncated lengths still produce zero ADD`() {
        val result = calculateLegacyTrim(LegacyTrimInput(LegacyFrequency.ofKilohertz(145_000), LegacyFrequency.ofKilohertz(145_001)))
        assertEquals(491L, result.currentLengthMillimeters)
        assertEquals(491L, result.targetLengthMillimeters)
        assertEquals(0L, result.amountMillimeters)
        assertEquals(LegacyTrimCommand.ADD, result.command)
    }

    @Test
    fun `extreme frequency endpoints preserve signed whole millimeter trim differences`() {
        val lower = LegacyFrequency.ofKilohertz(100)
        val upper = LegacyFrequency.ofKilohertz(999_999)
        val cut = calculateLegacyTrim(LegacyTrimInput(lower, upper, LegacyCoefficient.PERCENT_100))
        val add = calculateLegacyTrim(LegacyTrimInput(upper, lower, LegacyCoefficient.PERCENT_100))
        assertEquals(750_000L, cut.currentLengthMillimeters)
        assertEquals(75L, cut.targetLengthMillimeters)
        assertEquals(-749_925L, cut.deltaMillimeters)
        assertEquals(749_925L, add.deltaMillimeters)
        assertEquals(LegacyTrimCommand.CUT, cut.command)
        assertEquals(LegacyTrimCommand.ADD, add.command)
    }
}
