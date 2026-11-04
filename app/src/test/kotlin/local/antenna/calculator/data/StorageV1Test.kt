package local.antenna.calculator.data

import androidx.datastore.preferences.core.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

/** Independent wire fixtures transcribed from the pre-refactoring v1 layout. */
internal fun storageV1Preferences(): Preferences = mutablePreferencesOf().apply {
    val properties = Properties().apply {
        StorageV1Test::class.java.getResourceAsStream("/storage/preferences-v1.properties")!!.use { load(it) }
    }
    for ((rawKey, rawValue) in properties) {
        val key = rawKey.toString()
        val (type, value) = rawValue.toString().split('|', limit = 2)
        when (type) {
            "i" -> this[intPreferencesKey(key)] = value.toInt()
            "l" -> this[longPreferencesKey(key)] = value.toLong()
            "b" -> this[booleanPreferencesKey(key)] = value.toBooleanStrict()
            "s" -> this[stringPreferencesKey(key)] = value
            else -> error("Invalid fixture type")
        }
    }
}.toPreferences()

internal fun savedDraftV1(): ArrayList<String> = ArrayList(
    StorageV1Test::class.java.getResourceAsStream("/storage/saved-draft-v1.txt")!!.bufferedReader().use { it.readLines() })

class StorageV1Test {
    @Test fun preferencesV1LoadsOnlyCurrentFieldsAndPreservesOpaqueRetiredValuesOnWrite() {
        val old = storageV1Preferences()
        val restored = PreferencesStateCodec.decode(old)
        assertEquals(5, restored.values.size)
        assertEquals(433_000_001L, restored.values[FieldKey.SIZING_FREQUENCY])
        assertEquals(1_000_000_001L, restored.values[FieldKey.ACTUAL_LENGTH])
        assertEquals(9539L, restored.values[FieldKey.FACTOR])
        assertEquals(AppTheme.DARK, restored.settings.theme)
        val written = PreferencesStateCodec.encode(restored, old)
        for (name in listOf("LEN_FREQUENCY", "LEGACY_CURRENT", "LEGACY_TARGET")) {
            assertEquals(old[longPreferencesKey("value.$name")], written[longPreferencesKey("value.$name")])
            assertEquals(old[stringPreferencesKey("unit.$name")], written[stringPreferencesKey("unit.$name")])
        }
        assertEquals(97, written[intPreferencesKey("lenCoefficient")])
        assertEquals(93, written[intPreferencesKey("trimCoefficient")])
        assertEquals(false, written[booleanPreferencesKey("compatibility")])
    }

    @Test fun savedDraftV1LoadsIncompleteTextFlagsAndSettingsWithoutRuntimeRetiredFields() {
        val wire = savedDraftV1()
        assertEquals(49, wire.size)
        val draft = requireNotNull(DraftCodec.decode(wire))
        assertEquals(5, draft.fields.size)
        assertEquals("145,", draft[FieldKey.SIZING_FREQUENCY].text)
        assertTrue(draft[FieldKey.SIZING_FREQUENCY].focused)
        assertTrue(draft[FieldKey.SIZING_FREQUENCY].checked)
        assertEquals("1,000000001", draft[FieldKey.ACTUAL_LENGTH].text)
        assertEquals(AppLanguage.RUSSIAN, draft.settings.language)
        assertEquals(Section.TRIMMING, draft.settings.section)
        val rewritten = DraftCodec.encode(draft)
        assertEquals(49, rewritten.size)
        assertEquals("false", rewritten[42])
        assertEquals(draft, DraftCodec.decode(rewritten))
        assertEquals(draft.fields, DraftCodec.decode(ArrayList(wire.dropLast(1)))!!.fields)
    }

    @Test fun malformedRetiredWireDataAndFutureSchemasAreStillRejected() {
        val invalid = storageV1Preferences().toMutablePreferences().apply {
            this[longPreferencesKey("value.LEGACY_CURRENT")] = 145_000_001L
        }
        assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.decode(invalid) }
        assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.encode(PersistedCalculatorState.defaults(), invalid) }
        val future = storageV1Preferences().toMutablePreferences().apply { this[intPreferencesKey("schema")] = 99 }
        assertThrows(UnsupportedSchemaException::class.java) { PreferencesStateCodec.encode(PersistedCalculatorState.defaults(), future) }
        val malformed = savedDraftV1().apply { this[46] = "96" }
        assertNull(DraftCodec.decode(malformed))
    }
}
