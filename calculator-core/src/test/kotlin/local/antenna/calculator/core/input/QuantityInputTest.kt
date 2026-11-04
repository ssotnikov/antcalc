package local.antenna.calculator.core.input

import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityInputTest {
    @Test
    fun `equivalent frequency units give identical canonical values`() {
        for ((text, unit) in listOf(
            "145000" to FrequencyUnit.KILOHERTZ,
            "145,000" to FrequencyUnit.MEGAHERTZ,
            "0.145" to FrequencyUnit.GIGAHERTZ,
        )) {
            val parsed = QuantityInput.frequency(text, unit) as FieldState.Valid
            assertEquals(145_000_000L, parsed.value.hertz)
        }
    }

    @Test
    fun `frequency boundaries and immediate neighbors are validated exactly`() {
        val mhz = FrequencyUnit.MEGAHERTZ
        assertEquals(100_000L, (QuantityInput.frequency("0.1", mhz) as FieldState.Valid).value.hertz)
        assertEquals(6_000_000_000L, (QuantityInput.frequency("6000", mhz) as FieldState.Valid).value.hertz)
        for (text in listOf("0.099999", "6000.000001", "9".repeat(32))) {
            assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), QuantityInput.frequency(text, mhz))
        }
    }

    @Test
    fun `one hertz is preserved but sub hertz input is rejected`() {
        val parsed = QuantityInput.frequency("145.000001", FrequencyUnit.MEGAHERTZ) as FieldState.Valid
        assertEquals(145_000_001L, parsed.value.hertz)
        for ((text, unit) in listOf(
            "145000.0001" to FrequencyUnit.KILOHERTZ,
            "145.0000001" to FrequencyUnit.MEGAHERTZ,
            "0.1450000001" to FrequencyUnit.GIGAHERTZ,
        )) {
            assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), QuantityInput.frequency(text, unit))
        }
    }

    @Test
    fun `original frequency policy keeps one kilohertz resolution and original bounds`() {
        val unit = FrequencyUnit.MEGAHERTZ
        assertEquals(999_999_000L, (QuantityInput.frequency("999.999", unit, FrequencyMode.ORIGINAL) as FieldState.Valid).value.hertz)
        assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), QuantityInput.frequency("145.0005", unit, FrequencyMode.ORIGINAL))
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), QuantityInput.frequency("1000", unit, FrequencyMode.ORIGINAL))
    }

    @Test
    fun `equivalent physical lengths give identical canonical values`() {
        for ((text, unit) in listOf(
            "500" to LengthUnit.MILLIMETERS,
            "50" to LengthUnit.CENTIMETERS,
            ",5" to LengthUnit.METERS,
        )) {
            assertEquals(500_000_000L, (QuantityInput.physicalLength(text, unit) as FieldState.Valid).value.nanometers)
        }
    }

    @Test
    fun `length accepts working boundaries and rejects one nanometer beyond them`() {
        val unit = LengthUnit.METERS
        assertEquals(1_000_000L, (QuantityInput.physicalLength("0.001", unit) as FieldState.Valid).value.nanometers)
        assertEquals(10_000_000_000_000L, (QuantityInput.physicalLength("10000", unit) as FieldState.Valid).value.nanometers)
        for (text in listOf("0.000999999", "10000.000000001", "9".repeat(32))) {
            assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), QuantityInput.physicalLength(text, unit))
        }
    }

    @Test
    fun `nanometer resolution is independent of selected length unit`() {
        for ((text, unit) in listOf(
            "1.000001" to LengthUnit.MILLIMETERS,
            "0.1000001" to LengthUnit.CENTIMETERS,
            "0.001000001" to LengthUnit.METERS,
        )) {
            assertEquals(1_000_001L, (QuantityInput.physicalLength(text, unit) as FieldState.Valid).value.nanometers)
            assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), QuantityInput.physicalLength(text + "1", unit))
        }
    }

    @Test
    fun `factor accepts presets custom values and insignificant zeros`() {
        for ((text, expected) in listOf("1" to 10000, "0.97" to 9700, "0,95000" to 9500, "0.93" to 9300, "0.9539" to 9539, "0.0001" to 1)) {
            assertEquals(expected, (QuantityInput.shorteningFactor(text) as FieldState.Valid).value.tenThousandths)
        }
    }

    @Test
    fun `factor does not round invalid values into the allowed range`() {
        assertEquals(FieldState.Invalid(InputError.NON_POSITIVE), QuantityInput.shorteningFactor("0"))
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), QuantityInput.shorteningFactor("1.01"))
        assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), QuantityInput.shorteningFactor("0.95399"))
        assertEquals(FieldState.Invalid(InputError.INVALID_NUMBER), QuantityInput.shorteningFactor("-0.95"))
    }

    @Test
    fun `quantity validation preserves incomplete and invalid lexical states`() {
        assertEquals(FieldState.Empty, QuantityInput.frequency("", FrequencyUnit.MEGAHERTZ))
        assertEquals(FieldState.Incomplete, QuantityInput.physicalLength("500,", LengthUnit.MILLIMETERS))
        assertEquals(FieldState.Invalid(InputError.INVALID_NUMBER), QuantityInput.shorteningFactor("NaN"))
    }
}
