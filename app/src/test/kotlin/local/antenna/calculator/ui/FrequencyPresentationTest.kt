package local.antenna.calculator.ui

import java.util.Locale
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.state.FullWaveLoopDraft
import local.antenna.calculator.state.LoopField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyPresentationTest {
    @Test
    fun megahertzUsesThreeDigitsAndKeepsHertzPrecision() {
        val ru = FrequencyNumberFormat(Locale.forLanguageTag("ru"))
        val en = FrequencyNumberFormat(Locale.US)
        for ((hertz, expected) in listOf(
            7_100_000L to "7,100",
            14_074_000L to "14,074",
            145_625_000L to "145,625",
            145_612_500L to "145,6125",
            145_612_501L to "145,612501",
        )) {
            assertEquals(expected, ru.megahertz(hertz))
            assertEquals(expected.replace(',', '.'), en.megahertz(hertz.toDouble()))
        }
        assertEquals("147,253", ru.estimatedMegahertz(147_253_249.904))
    }

    @Test
    fun inactiveInputPadsWithoutRoundingAndKeepsIncompleteInput() {
        assertEquals("145,000", displayMegahertzInput("145", ','))
        assertEquals("145.500", displayMegahertzInput("145,5", '.'))
        assertEquals("145,612501", displayMegahertzInput("145.612501", ','))
        assertEquals("145.", displayMegahertzInput("145.", ','))
        assertEquals("", displayMegahertzInput("", ','))
    }

    @Test
    fun loopFrequencyInputsHaveTheSameResolutionAsTheMainCalculator() {
        val draft = FullWaveLoopDraft()
        for (field in listOf(LoopField.FREQUENCY, LoopField.MATCH_FREQUENCY)) {
            assertTrue(draft.edit(field, "145,612501").number(field) is FieldState.Valid)
            assertTrue(draft.edit(field, "145,6125011").number(field) is FieldState.Invalid)
        }
        assertEquals(145_612_501L,
            (QuantityInput.frequency("145.612501", FrequencyUnit.MEGAHERTZ) as FieldState.Valid).value.hertz)
    }
}
