package local.antenna.calculator.core.compatibility

import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.InputError
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.ShorteningFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LegacyInputTest {
    @Test
    fun `whole kilohertz bounds are accepted exactly`() {
        assertEquals(100L, LegacyFrequency.ofKilohertz(100).kilohertz)
        assertEquals(999_999L, LegacyFrequency.ofKilohertz(999_999).kilohertz)
        for (value in listOf(Long.MIN_VALUE, 0L, 99L, 1_000_000L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LegacyFrequency.ofKilohertz(value) }
        }
    }

    @Test
    fun `modern frequency conversion rejects extra precision and excessive range`() {
        assertEquals(145_000L, LegacyFrequency.fromFrequency(Frequency.ofHertz(145_000_000)).kilohertz)
        for (value in listOf(145_000_001L, 999_999_500L, 1_000_000_000L, 6_000_000_000L)) {
            assertThrows(IllegalArgumentException::class.java) { LegacyFrequency.fromFrequency(Frequency.ofHertz(value)) }
        }
    }

    @Test
    fun `all frequency units represent the same original whole kilohertz`() {
        for ((text, unit) in listOf("145000" to FrequencyUnit.KILOHERTZ, "145,000" to FrequencyUnit.MEGAHERTZ, "0.145" to FrequencyUnit.GIGAHERTZ)) {
            assertEquals(145_000L, (LegacyInput.frequency(text, unit) as FieldState.Valid).value.kilohertz)
        }
    }

    @Test
    fun `decimal input preserves errors and original range without clamping`() {
        val unit = FrequencyUnit.MEGAHERTZ
        assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), LegacyInput.frequency("145.0005", unit))
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), LegacyInput.frequency("1000", unit))
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), LegacyInput.frequency("0.099", unit))
        assertEquals(FieldState.Invalid(InputError.INVALID_NUMBER), LegacyInput.frequency("NaN", unit))
        assertEquals(FieldState.Empty, LegacyInput.frequency("", unit))
        assertEquals(FieldState.Incomplete, LegacyInput.frequency("145,", unit))
    }

    @Test
    fun `only the original coefficient table is selectable`() {
        assertEquals(listOf(100, 97, 95, 93), LegacyCoefficient.entries.map { it.percent })
        assertEquals(LegacyCoefficient.PERCENT_95, LegacyCoefficient.DEFAULT)
        for (value in listOf(-1, 0, 94, 96, 98, 101, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LegacyCoefficient.ofPercent(value) }
        }
    }

    @Test
    fun `modern coefficients convert only on an exact table match`() {
        for (coefficient in LegacyCoefficient.entries) {
            assertEquals(coefficient, LegacyCoefficient.fromShorteningFactor(ShorteningFactor.ofTenThousandths(coefficient.percent * 100)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyCoefficient.fromShorteningFactor(ShorteningFactor.ofTenThousandths(9539))
        }
    }
}
