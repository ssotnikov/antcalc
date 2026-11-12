package local.antenna.calculator.state

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.core.layout.LoopFeed
import local.antenna.calculator.core.layout.LoopShape
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.data.InvalidSettingsException
import local.antenna.calculator.data.PersistedCalculatorState
import local.antenna.calculator.data.PreferencesStateCodec
import local.antenna.calculator.data.UnsupportedSchemaException
import local.antenna.calculator.journal.fullWaveLoopSnapshot
import org.junit.Assert.*
import org.junit.Test

class FullWaveLoopStateTest {
    private val triangle = FullWaveLoopDraft(mode = LoopMode.PERIMETER)
        .chooseShape(LoopShape.TRIANGLE).copy(feed = LoopFeed.QUARTER_FROM_APEX)
        .changeInputUnit(LengthUnit.CENTIMETERS)

    @Test fun roundTripsGeometryAndFeedAndKeepsLastValidInput() {
        val base = PersistedCalculatorState.defaults()
        val updated = base.advance(base.toDraft().copy(fullWaveLoop = triangle))
        val invalid = triangle.edit(LoopField.PERIMETER, "")
        assertNull(invalid.layout())
        assertEquals(updated, updated.advance(updated.toDraft().copy(fullWaveLoop = invalid)))
        assertEquals(invalid, FullWaveLoopDraftCodec.decode(FullWaveLoopDraftCodec.encode(invalid)))
        assertEquals(triangle, PreferencesStateCodec.decode(PreferencesStateCodec.encode(updated)).fullWaveLoop)
        val snapshot = requireNotNull(triangle.fullWaveLoopSnapshot())
        assertEquals(3.0, snapshot.inputs.first { it.key == "loop_shape" }.value, 0.0)
        assertEquals(12.0, snapshot.outputs.first { it.key == "loop_perimeter" }.value, 0.0)
        assertEquals(24_982_704.833333, snapshot.outputs.first { it.key == "loop_frequency" }.value, 1e-5)
    }

    @Test fun olderSettingsMigrateAndMalformedOrUnknownSettingsArePreserved() {
        val base = PersistedCalculatorState.defaults()
        val wire = PreferencesStateCodec.encode(base).toMutablePreferences()
        val schema = intPreferencesKey("fullWaveLoop.schema")
        val inputs = stringPreferencesKey("fullWaveLoop.inputs")
        val older = wire.toMutablePreferences().apply { remove(schema); remove(inputs) }
        assertEquals(FullWaveLoopDraft(), PreferencesStateCodec.decode(older).fullWaveLoop)
        val future = wire.toMutablePreferences().apply { this[schema] = 2 }
        assertThrows(UnsupportedSchemaException::class.java) { PreferencesStateCodec.encode(base, future) }
        val corrupt = wire.toMutablePreferences().apply { this[inputs] = "TRIANGLE|invalid" }
        assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.encode(base, corrupt) }
    }
}
