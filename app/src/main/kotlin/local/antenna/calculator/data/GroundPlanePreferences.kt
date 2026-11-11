package local.antenna.calculator.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.state.GroundPlaneDraft
import local.antenna.calculator.state.GroundPlaneDraftCodec
import local.antenna.calculator.state.GroundPlaneField

internal object GroundPlanePreferences {
    private const val PREFIX = "groundPlane."
    private val schema = intPreferencesKey(PREFIX + "schema")
    private val values = stringPreferencesKey(PREFIX + "inputs")

    fun write(target: MutablePreferences, draft: GroundPlaneDraft) {
        require(GroundPlaneField.entries.all { draft.value(it) != null })
        target[schema] = 1
        target[values] = GroundPlaneDraftCodec.encode(draft).joinToString("|")
    }

    fun read(source: Preferences): GroundPlaneDraft {
        val version = source[schema]
        if (version == null) {
            require(source.asMap().keys.none { it.name.startsWith(PREFIX) })
            return GroundPlaneDraft()
        }
        if (version != 1) throw UnsupportedSchemaException(version)
        val encoded = requireNotNull(source[values])
        require(encoded.length <= 420)
        val draft = requireNotNull(GroundPlaneDraftCodec.decode(encoded.split('|')))
        require(GroundPlaneField.entries.all { draft.value(it) != null })
        return draft
    }
}
