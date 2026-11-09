package local.antenna.calculator.data

import androidx.datastore.preferences.core.*
import local.antenna.calculator.core.matching.*
import local.antenna.calculator.state.*

/** Additive versioned domain; old calculator schema and numerical wire fields remain intact. */
internal object MatchingPreferences {
    fun write(target: MutablePreferences, draft: MatchingDraft) {
        target[intPreferencesKey("matching.schema")] = 1
        target[stringPreferencesKey("matching.mode")] = draft.mode.name
        MatchingField.entries.forEach { target[stringPreferencesKey("matching.${it.name}")] = draft.fields.getValue(it) }
    }
    fun read(source: Preferences): MatchingDraft {
        val version = source[intPreferencesKey("matching.schema")]
        if (version == null) {
            require(source.asMap().keys.none { it.name.startsWith("matching.") })
            return MatchingDraft()
        }
        if (version != 1) throw UnsupportedSchemaException(version)
        val mode = MatchingMode.valueOf(requireNotNull(source[stringPreferencesKey("matching.mode")]))
        val values = MatchingField.entries.associateWith { key ->
            val text = requireNotNull(source[stringPreferencesKey("matching.${key.name}")])
            require(text.length <= 32 && parseMatchingNumber(text, key).value != null)
            text
        }
        return MatchingDraft(mode, values)
    }
}
