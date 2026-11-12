package local.antenna.calculator.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.state.FullWaveLoopDraft
import local.antenna.calculator.state.FullWaveLoopDraftCodec

internal object FullWaveLoopPreferences {
    private const val PREFIX = "fullWaveLoop."
    private val schema = intPreferencesKey(PREFIX + "schema")
    private val values = stringPreferencesKey(PREFIX + "inputs")

    fun write(target: MutablePreferences, draft: FullWaveLoopDraft) {
        target[schema] = 1
        target[values] = FullWaveLoopDraftCodec.encode(draft).joinToString("|")
    }

    fun read(source: Preferences): FullWaveLoopDraft {
        val version = source[schema]
        if (version == null) {
            require(source.asMap().keys.none { it.name.startsWith(PREFIX) })
            return FullWaveLoopDraft()
        }
        if (version != 1) throw UnsupportedSchemaException(version)
        val encoded = requireNotNull(source[values])
        require(encoded.length <= 460)
        return requireNotNull(FullWaveLoopDraftCodec.decode(encoded.split('|'))).also {
            require(it == it.canonical(FullWaveLoopDraft()))
        }
    }
}
