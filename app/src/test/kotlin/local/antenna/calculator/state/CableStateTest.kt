package local.antenna.calculator.state

import androidx.datastore.preferences.core.*
import local.antenna.calculator.core.feedline.*
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.units.*
import local.antenna.calculator.data.*
import org.junit.Assert.*
import org.junit.Test

class CableStateTest {
    @Test fun directionsRetainIndependentLengthAndAngle() {
        val angle = CableDraft().edit(CableField.ANGLE, "450")
        val length = angle.copy(mode = CableMode.LENGTH_TO_ANGLE).edit(CableField.LENGTH, "1000")
        assertEquals(1.0, length.result()!!.lengthMeters.toDouble(), 0.0)
        assertEquals("450", length.copy(mode = CableMode.ANGLE_TO_LENGTH).fields[CableField.ANGLE])
        assertNull(length.edit(CableField.LENGTH, "1,").result())
        assertNotNull(length.edit(CableField.LENGTH, "1,").copy(mode = CableMode.ANGLE_TO_LENGTH).result())
    }
    @Test fun unitsPreserveExactValuesAndInvalidInterpretation() {
        val original = CableDraft().edit(CableField.FREQUENCY, "145,000001").edit(CableField.LENGTH, "1000,000001")
        val changed = original.changeFrequencyUnit(FrequencyUnit.GIGAHERTZ).changeLengthUnit(LengthUnit.METERS)
        assertEquals(145_000_001L, changed.value(CableField.FREQUENCY))
        assertEquals(1_000_000_001L, changed.value(CableField.LENGTH))
        val incomplete = changed.edit(CableField.LENGTH, "1,").changeLengthUnit(LengthUnit.CENTIMETERS)
        assertEquals("1,", incomplete.fields[CableField.LENGTH])
        assertEquals(LengthUnit.METERS, incomplete.lengthValidationUnit)
        assertEquals(FieldState.Incomplete, incomplete.number(CableField.LENGTH))
        assertEquals(original.value(CableField.LENGTH), incomplete.canonical(original).value(CableField.LENGTH))
    }
    @Test fun oversizedTextCannotBecomeValidAndWireIsBounded() {
        val draft = CableDraft().edit(CableField.ANGLE, "90" + " ".repeat(100000) + "1")
        assertNull(draft.result()); assertEquals(33, draft.fields.getValue(CableField.ANGLE).length)
        val wire = CableDraftCodec.encode(draft)
        assertEquals(draft, CableDraftCodec.decode(wire))
        assertTrue(wire.sumOf { it.length } < 512)
    }
    @Test fun migrationKeepsCalculatorAndMatchingData() {
        val base = PersistedCalculatorState.defaults()
        val old = PreferencesStateCodec.encode(base).toMutablePreferences()
        old.asMap().keys.filter { it.name.startsWith("cable.") }.forEach { old.remove(it) }
        val decoded = PreferencesStateCodec.decode(old)
        assertEquals(base.values, decoded.values); assertEquals(base.matching, decoded.matching)
        assertEquals(CableDraft(), decoded.cable)
        val state = decoded.copy(cable = CableDraft().edit(CableField.VELOCITY, "0,84").changeLengthUnit(LengthUnit.METERS))
        assertEquals(state, PreferencesStateCodec.decode(PreferencesStateCodec.encode(state, old)))
    }
    @Test fun futureAndCorruptCableDomainsCannotBeOverwritten() {
        val state = PersistedCalculatorState.defaults()
        val wire = PreferencesStateCodec.encode(state).toMutablePreferences()
        wire[intPreferencesKey("cable.schema")] = 2
        try { PreferencesStateCodec.encode(state, wire); fail("Future schema") } catch (_: UnsupportedSchemaException) { }
        assertEquals(2, wire[intPreferencesKey("cable.schema")])
        wire[intPreferencesKey("cable.schema")] = 1
        wire[stringPreferencesKey("cable.VELOCITY")] = "0"
        try { PreferencesStateCodec.encode(state, wire); fail("Corrupt input") } catch (_: InvalidSettingsException) { }
        assertEquals("0", wire[stringPreferencesKey("cable.VELOCITY")])
    }
    @Test fun antennaCoefficientCannotChangeCableVelocity() {
        val original = CalculatorDraft.defaults()
        val changed = original.update(FieldKey.FACTOR, original[FieldKey.FACTOR].edit("0,93"))
        assertEquals(original.cable, changed.cable)
        assertEquals(original.cable.result(), changed.cable.result())
    }
}
