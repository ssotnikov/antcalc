package local.antenna.calculator.state

import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.units.*
import org.junit.Assert.*
import org.junit.Test

class NumericDraftTest {
    @Test fun frequencyConversionUsesExactInputRatherThanRoundedResults() {
        val draft = NumericDraft("145", FrequencyUnit.MEGAHERTZ.name)
        assertEquals("145000", draft.changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.KILOHERTZ.name).text)
        assertEquals("0,145", draft.changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.GIGAHERTZ.name).text)
    }

    @Test fun repeatedFrequencyConversionsPreserveOneHertz() {
        var draft = NumericDraft("145.000001", FrequencyUnit.MEGAHERTZ.name)
        repeat(100) {
            for (unit in FrequencyUnit.entries) {
                draft = draft.changeUnit(FieldKey.SIZING_FREQUENCY, unit.name)
                assertEquals(145_000_001L, draft.frequency().validValue()!!.hertz)
                assertEquals(draft.frequency(), QuantityInput.frequency(draft.text, unit))
            }
        }
    }

    @Test fun repeatedLengthConversionsPreserveOneNanometer() {
        var draft = NumericDraft("1.000001", LengthUnit.MILLIMETERS.name)
        repeat(100) {
            for (unit in LengthUnit.entries) {
                draft = draft.changeUnit(FieldKey.ACTUAL_LENGTH, unit.name)
                assertEquals(1_000_001L, draft.length().validValue()!!.nanometers)
                assertEquals(draft.length(), QuantityInput.physicalLength(draft.text, unit))
            }
        }
        assertEquals("0,001000001", draft.text)
    }

    @Test fun incompleteAndMalformedTextSurvivesUnitChangesAndSavedRestoration() {
        for (text in listOf("", "145,", "NaN", "1,2.3")) {
            val initial = NumericDraft(text, FrequencyUnit.MEGAHERTZ.name)
            val changed = initial.changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.GIGAHERTZ.name)
            assertEquals(text, changed.text)
            assertEquals(initial.frequency(), changed.frequency())
            val draft = CalculatorDraft.defaults().update(FieldKey.SIZING_FREQUENCY, changed)
            assertEquals(changed, DraftCodec.decode(DraftCodec.encode(draft))!![FieldKey.SIZING_FREQUENCY])
        }
    }

    @Test fun invalidFrequencyIsNotReinterpretedUntilEdited() {
        val changed = NumericDraft("0.05", FrequencyUnit.MEGAHERTZ.name)
            .changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.GIGAHERTZ.name)
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), changed.frequency())
        assertEquals("0.05", changed.text)
        assertEquals(50_000_000L, changed.edit("0.05").frequency().validValue()!!.hertz)
    }

    @Test fun invalidLengthIsNotReinterpretedUntilEdited() {
        val changed = NumericDraft("0.5", LengthUnit.MILLIMETERS.name)
            .changeUnit(FieldKey.ACTUAL_LENGTH, LengthUnit.METERS.name)
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), changed.length())
        assertEquals("0.5", changed.text)
        assertEquals(500_000_000L, changed.edit("0.5").length().validValue()!!.nanometers)
    }

    @Test fun fieldsDoNotShareEditsOrUnitChanges() {
        val first = NumericDraft("145", FrequencyUnit.MEGAHERTZ.name)
        val second = NumericDraft("155", FrequencyUnit.MEGAHERTZ.name)
        first.edit("433").changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.KILOHERTZ.name)
        assertEquals("145", first.text)
        assertEquals("155", second.text)
    }
}
