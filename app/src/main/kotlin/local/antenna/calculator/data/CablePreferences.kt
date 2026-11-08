package local.antenna.calculator.data

import androidx.datastore.preferences.core.*
import local.antenna.calculator.core.feedline.*
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*

/** Independently versioned cable inputs; do not migrate or rewrite matching/calculator data. */
internal object CablePreferences {
    fun write(target: MutablePreferences, draft: CableDraft) {
        require(draft.frequencyValidationUnit == draft.frequencyUnit && draft.lengthValidationUnit == draft.lengthUnit)
        require(CableField.entries.all { draft.value(it) != null })
        target[intPreferencesKey("cable.schema")] = 1
        target[stringPreferencesKey("cable.mode")] = draft.mode.name
        target[stringPreferencesKey("cable.frequencyUnit")] = draft.frequencyUnit.name
        target[stringPreferencesKey("cable.lengthUnit")] = draft.lengthUnit.name
        CableDraftCodec.fields.forEach { target[stringPreferencesKey("cable.${it.name}")] = draft.fields.getValue(it) }
    }
    fun read(source: Preferences): CableDraft {
        val version = source[intPreferencesKey("cable.schema")]
        if (version == null) {
            require(source.asMap().keys.none { it.name.startsWith("cable.") })
            return CableDraft()
        }
        if (version != 1) throw UnsupportedSchemaException(version)
        fun value(key: String) = requireNotNull(source[stringPreferencesKey("cable.$key")])
        val draft = CableDraft(CableMode.valueOf(value("mode")), CableDraftCodec.fields.associateWith { value(it.name) },
            FrequencyUnit.valueOf(value("frequencyUnit")), LengthUnit.valueOf(value("lengthUnit")))
        require(draft.fields.values.all { it.length <= 32 } && CableField.entries.all { draft.value(it) != null })
        return draft
    }
}
