package local.antenna.calculator.core.format

import java.util.Locale
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LengthFormatterTest {
    @Test
    fun `the reference conductor uses the specified decimal scales`() {
        val meters = 0.491039370862069
        for ((unit, expected) in listOf(
            LengthUnit.MILLIMETERS to "491",
            LengthUnit.CENTIMETERS to "49,10",
            LengthUnit.METERS to "0,49",
        )) {
            assertEquals(LengthDisplay(expected, unit, false), LengthFormatter.format(meters, unit))
        }
    }

    @Test
    fun `decimal ties round half up in every unit`() {
        assertEquals("2", LengthFormatter.format(0.0015, LengthUnit.MILLIMETERS).number)
        assertEquals("1,24", LengthFormatter.format(0.01235, LengthUnit.CENTIMETERS).number)
        assertEquals("1,24", LengthFormatter.format(1.235, LengthUnit.METERS).number)
    }

    @Test
    fun `decimal values on both sides of a tie are not rounded prematurely`() {
        assertEquals("1", LengthFormatter.format(0.00149999, LengthUnit.MILLIMETERS).number)
        assertEquals("2", LengthFormatter.format(0.00150001, LengthUnit.MILLIMETERS).number)
    }

    @Test
    fun `small positive values return the display threshold instead of zero`() {
        for ((unit, threshold) in listOf(
            LengthUnit.MILLIMETERS to "1",
            LengthUnit.CENTIMETERS to "0,01",
            LengthUnit.METERS to "0,01",
        )) {
            for (meters in listOf(0.00009, 0.000000001, Double.MIN_VALUE)) {
                assertEquals(LengthDisplay(threshold, unit, true), LengthFormatter.format(meters, unit))
            }
        }
    }

    @Test
    fun `an exact display step is a regular numeric result`() {
        for (unit in LengthUnit.entries) {
            assertFalse(LengthFormatter.format(unit.nanometersPerUnit / 1e9 / Math.pow(10.0, unit.displayScale.toDouble()), unit).isBelowResolution)
        }
    }

    @Test
    fun `zero including negative zero has no less than marker or minus sign`() {
        for (zero in listOf(0.0, -0.0)) {
            for (unit in LengthUnit.entries) {
                val result = LengthFormatter.format(zero, unit)
                assertFalse(result.isBelowResolution)
                assertFalse(result.number.contains('-'))
                assertEquals(if (unit.displayScale == 0) "0" else "0,00", result.number)
            }
        }
    }

    @Test
    fun `nonfinite or negative results are rejected`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.001)) {
            assertThrows(IllegalArgumentException::class.java) {
                LengthFormatter.format(value, LengthUnit.MILLIMETERS)
            }
        }
    }

    @Test
    fun `large calculated results remain finite decimal strings without exponents`() {
        assertEquals("600000000,00", LengthFormatter.format(600_000_000.0, LengthUnit.METERS).number)
        val largest = LengthFormatter.format(Double.MAX_VALUE, LengthUnit.MILLIMETERS)
        assertTrue(largest.number.length > 300)
        assertFalse(largest.number.contains(','))
        assertFalse(largest.number.contains('E'))
    }

    @Test
    fun `the numeric result is independent of system locale`() {
        val original = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ru-RU"))) {
                Locale.setDefault(locale)
                assertEquals("491", LengthFormatter.format(0.491039370862069, LengthUnit.MILLIMETERS).number)
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `formatting physical input never changes its nanometer value`() {
        val length = PhysicalLength.ofNanometers(491_050_000)
        assertEquals("491", LengthFormatter.format(length, LengthUnit.MILLIMETERS).number)
        assertEquals(491_050_000L, length.nanometers)
    }
}
