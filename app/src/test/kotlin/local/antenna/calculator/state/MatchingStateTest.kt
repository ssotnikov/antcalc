package local.antenna.calculator.state

import androidx.datastore.preferences.core.*
import local.antenna.calculator.core.matching.*
import local.antenna.calculator.data.*
import org.junit.Assert.*
import org.junit.Test

class MatchingStateTest {
    @Test fun modesHaveIndependentDraftsAndInvalidInputCannotCopy() {
        val first = MatchingDraft().edit(MatchingField.SWR, "3,")
        val other = first.copy(mode = MatchingMode.IMPEDANCE)
        assertNotNull(other.result())
        assertNull(other.copy(mode = MatchingMode.SWR).result())
        assertEquals("3,", other.fields[MatchingField.SWR])
    }
    @Test fun canonicalStateKeepsLastValidInputAndWireKeepsIncompleteInput() {
        val previous = MatchingDraft().edit(MatchingField.SWR, "3")
        val draft = previous.edit(MatchingField.SWR, "3,").edit(MatchingField.REACTANCE, "-25")
        val persisted = draft.canonical(previous)
        assertEquals("3", persisted.fields[MatchingField.SWR])
        assertEquals("-25", persisted.fields[MatchingField.REACTANCE])
        assertEquals(draft, MatchingDraftCodec.decode(MatchingDraftCodec.encode(draft)))
        val oversized = draft.edit(MatchingField.SWR, "9".repeat(100000))
        assertEquals(33, oversized.fields.getValue(MatchingField.SWR).length)
        assertNull(oversized.result())
        val hiddenSuffix = draft.edit(MatchingField.SWR, "2" + " ".repeat(100000) + "9")
        assertNull(hiddenSuffix.result())
        assertNull(MatchingDraftCodec.decode(MatchingDraftCodec.encode(hiddenSuffix))!!.result())
    }
    @Test fun oldPreferencesMigrateWithoutChangingCalculatorValues() {
        val base = PersistedCalculatorState.defaults()
        val wire = PreferencesStateCodec.encode(base).toMutablePreferences()
        wire.asMap().keys.filter { it.name.startsWith("matching.") }.forEach { wire.remove(it) }
        val decoded = PreferencesStateCodec.decode(wire)
        assertEquals(base.values, decoded.values)
        assertEquals(MatchingDraft(), decoded.matching)
        val updated = decoded.copy(matching = MatchingDraft(mode = MatchingMode.IMPEDANCE).edit(MatchingField.REACTANCE, "-20"))
        assertEquals(updated, PreferencesStateCodec.decode(PreferencesStateCodec.encode(updated, wire)))
    }
    @Test fun unknownAndCorruptMatchingDomainsAreNeverOverwritten() {
        val base = PersistedCalculatorState.defaults()
        val wire = PreferencesStateCodec.encode(base).toMutablePreferences()
        wire[intPreferencesKey("matching.schema")] = 2
        try { PreferencesStateCodec.encode(base, wire); fail("Unknown schema must survive") }
        catch (_: UnsupportedSchemaException) { }
        assertEquals(2, wire[intPreferencesKey("matching.schema")])
        wire[intPreferencesKey("matching.schema")] = 1
        wire[stringPreferencesKey("matching.REACTANCE")] = "NaN"
        try { PreferencesStateCodec.encode(base, wire); fail("Corruption must survive") }
        catch (_: InvalidSettingsException) { }
        assertEquals("NaN", wire[stringPreferencesKey("matching.REACTANCE")])
    }
}
