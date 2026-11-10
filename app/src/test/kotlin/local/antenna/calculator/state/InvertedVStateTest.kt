package local.antenna.calculator.state

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.data.InvalidSettingsException
import local.antenna.calculator.data.PersistedCalculatorState
import local.antenna.calculator.data.PreferencesStateCodec
import local.antenna.calculator.data.UnsupportedSchemaException
import local.antenna.calculator.journal.invertedVSnapshot
import local.antenna.calculator.state.InvertedVField.*
import org.junit.Assert.*
import org.junit.Test

class InvertedVStateTest {
    @Test fun exactUnitsAndModeIndependence() {
        val measured = InvertedVDraft().edit(ARM, "5,000000001").edit(HEIGHT, "8.000000001")
        val estimated = measured.copy(mode = InvertedVMode.ESTIMATED).edit(FREQUENCY, "14,200001")
            .changeLengthUnit(LengthUnit.MILLIMETERS).changeFrequencyUnit(FrequencyUnit.GIGAHERTZ)
        assertEquals(14_200_001L, estimated.value(FREQUENCY))
        assertEquals(8_000_000_001L, estimated.value(HEIGHT))
        assertEquals(5_000_000_001L, estimated.copy(mode = InvertedVMode.MEASURED).value(ARM))
        assertEquals(estimated, InvertedVDraftCodec.decode(InvertedVDraftCodec.encode(estimated)))
    }

    @Test fun invalidDraftCannotExposeOldResultOrBecomeValidAfterUnitChange() {
        val draft = InvertedVDraft().edit(ARM, "0,0001")
        assertNull(draft.result())
        assertNull(draft.changeLengthUnit(LengthUnit.MILLIMETERS).result())
        assertNull(InvertedVDraft().edit(ANGLE, "120,01").result())
        assertNull(InvertedVDraft().edit(HEIGHT, "8" + " ".repeat(1000) + "0").result())
        assertEquals(FieldState.Valid(0L), InvertedVDraft().edit(HEIGHT, "0").number(HEIGHT))
    }

    @Test fun onlyGroundCompatibleResultsAreSavedWithModelInputs() {
        val draft = InvertedVDraft()
        assertNull(draft.edit(HEIGHT, "1").invertedVSnapshot())
        val touch = checkNotNull(draft.edit(HEIGHT, "2,5").invertedVSnapshot())
        assertEquals(listOf("ENDS_TOUCH_GROUND"), touch.warnings)
        assertTrue(touch.inputs.any { it.key == "dipole_arm" && it.value == 5.0 })
        val estimate = checkNotNull(draft.copy(mode = InvertedVMode.ESTIMATED).invertedVSnapshot())
        assertEquals("inverted_v", estimate.model)
        assertEquals(1, estimate.revision)
        assertTrue(estimate.inputs.any { it.key == "frequency" && it.value == 14_200_000.0 })
    }

    @Test fun previousPreferencesMigrateAndNewerOrCorruptPreferencesArePreserved() {
        val base = PersistedCalculatorState.defaults()
        val schema = intPreferencesKey("invertedV.schema")
        val inputs = stringPreferencesKey("invertedV.inputs")
        val previous = PreferencesStateCodec.encode(base).toMutablePreferences().apply {
            remove(schema)
            remove(inputs)
        }
        val migrated = PreferencesStateCodec.decode(previous)
        assertEquals(base, migrated)
        val draft = migrated.toDraft().copy(invertedV = InvertedVDraft().edit(HEIGHT, "0"))
        val updated = migrated.advance(draft)
        val current = PreferencesStateCodec.encode(updated, previous).toMutablePreferences()
        assertEquals(updated, PreferencesStateCodec.decode(current))
        current[schema] = 2
        assertThrows(UnsupportedSchemaException::class.java) { PreferencesStateCodec.encode(updated, current) }
        assertEquals(2, current[schema])
        current[schema] = 1
        current[inputs] = "broken"
        assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.encode(updated, current) }
        assertEquals("broken", current[inputs])
    }
}
