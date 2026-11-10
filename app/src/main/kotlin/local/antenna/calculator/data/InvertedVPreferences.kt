package local.antenna.calculator.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.state.InvertedVDraft
import local.antenna.calculator.state.InvertedVDraftCodec
import local.antenna.calculator.state.InvertedVField

internal object InvertedVPreferences {
    private const val PREFIX = "invertedV."
    private val schema = intPreferencesKey(PREFIX + "schema")
    private val values = stringPreferencesKey(PREFIX + "inputs")

    fun write(target: MutablePreferences, draft: InvertedVDraft) {
        require(InvertedVField.entries.all { draft.value(it) != null })
        target[schema] = 1
        target[values] = InvertedVDraftCodec.encode(draft).joinToString("|")
    }

    fun read(source: Preferences): InvertedVDraft {
        val version = source[schema]
        if (version == null) {
            require(source.asMap().keys.none { it.name.startsWith(PREFIX) })
            return InvertedVDraft()
        }
        if (version != 1) throw UnsupportedSchemaException(version)
        val encoded = requireNotNull(source[values])
        require(encoded.length <= 320)
        val draft = requireNotNull(InvertedVDraftCodec.decode(encoded.split('|')))
        require(InvertedVField.entries.all { draft.value(it) != null })
        return draft
    }
}
