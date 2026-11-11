package local.antenna.calculator.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import local.antenna.calculator.state.FeedlineDraft
import local.antenna.calculator.state.FeedlineDraftCodec

internal object FeedlinePreferences {
    private val schema = intPreferencesKey("feedline.schema")
    private val fields = listOf("profile", "frequency", "frequencyUnit", "length", "power", "attenuation",
        "attenuationUnit", "enteredAttenuationUnit", "attenuationHertz").map { stringPreferencesKey("feedline.$it") }

    fun read(source: Preferences): FeedlineDraft = when (val version = source[schema]) {
        null -> {
            require(source.asMap().keys.none { it.name.startsWith("feedline.") })
            FeedlineDraft()
        }
        1 -> requireNotNull(FeedlineDraftCodec.decode(fields.map { requireNotNull(source[it]) }))
        else -> throw UnsupportedSchemaException(version)
    }

    fun write(target: MutablePreferences, draft: FeedlineDraft) {
        target[schema] = 1
        fields.zip(FeedlineDraftCodec.encode(draft)).forEach { (key, value) -> target[key] = value }
    }
}
