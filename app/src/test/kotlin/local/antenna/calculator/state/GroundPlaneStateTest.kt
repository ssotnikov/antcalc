package local.antenna.calculator.state

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.data.InvalidSettingsException
import local.antenna.calculator.data.PersistedCalculatorState
import local.antenna.calculator.data.PreferencesStateCodec
import local.antenna.calculator.data.UnsupportedSchemaException
import local.antenna.calculator.journal.groundPlaneSnapshot
import org.junit.Assert.*
import org.junit.Test

class GroundPlaneStateTest {
    @Test fun incompleteDraftSurvivesRecreationWithoutPersistingInvalidInput() {
        val saved = PersistedCalculatorState.defaults()
        val valid = GroundPlaneDraft(mode = GroundPlaneMode.MANUAL).edit(GroundPlaneField.WHIP, "0,61")
        val draft = valid.edit(GroundPlaneField.WHIP, "0,")
        assertNull(draft.result())
        assertEquals(draft, GroundPlaneDraftCodec.decode(GroundPlaneDraftCodec.encode(draft)))
        val updated = saved.advance(CalculatorDraft.defaults().copy(groundPlane = valid))
            .advance(CalculatorDraft.defaults().copy(groundPlane = draft))
        val restored = PreferencesStateCodec.decode(PreferencesStateCodec.encode(updated)).toDraft().groundPlane
        assertEquals(valid, restored)
    }

    @Test fun layoutSnapshotUsesCanonicalValuesAndRejectsImpossibleResults() {
        val draft = GroundPlaneDraft(mode = GroundPlaneMode.MANUAL).changeUnits(length = LengthUnit.CENTIMETERS)
        assertEquals("50", draft.fields[GroundPlaneField.WHIP])
        val snapshot = requireNotNull(draft.groundPlaneSnapshot())
        assertEquals("ground_plane", snapshot.model)
        assertEquals(0.5, snapshot.outputs.first { it.key == "gp_whip" }.value, 0.0)
        assertNull(draft.edit(GroundPlaneField.ANGLE, "90").groundPlaneSnapshot())
        assertEquals(GroundPlaneResult.RadialsBelowGround, draft.edit(GroundPlaneField.HEIGHT, "0").result())
    }

    @Test fun preferencesRejectUnknownOrCorruptToolData() {
        val valid = PreferencesStateCodec.encode(PersistedCalculatorState.defaults())
        val unknown = valid.toMutablePreferences().apply { this[intPreferencesKey("groundPlane.schema")] = 2 }
        val corrupt = valid.toMutablePreferences().apply { this[stringPreferencesKey("groundPlane.inputs")] = "invalid" }
        assertThrows(UnsupportedSchemaException::class.java) { PreferencesStateCodec.decode(unknown) }
        assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.decode(corrupt) }
        assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.encode(PersistedCalculatorState.defaults(), corrupt) }
    }
}
