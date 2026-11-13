package local.antenna.calculator.data

import androidx.datastore.preferences.core.*
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*

class UnsupportedSchemaException(val version: Int) : Exception("Unsupported storage schema: $version")
class InvalidSettingsException(cause: Throwable) : Exception("Invalid stored settings", cause)

/** Owns schema 1, including opaque retired fields; they never enter the current UI model. */
object PreferencesStateCodec {
    const val SCHEMA = 1

    fun encode(state: PersistedCalculatorState, existing: Preferences = emptyPreferences()): Preferences {
        if (existing.asMap().isNotEmpty()) decode(existing) // Preserve corrupt/newer data, even on writes.
        return (if (existing.asMap().isEmpty()) RetiredPreferencesV1.defaults() else existing.toMutablePreferences()).apply {
            this[intPreferencesKey("schema")] = SCHEMA
            for (key in FieldKey.entries) {
                val valueKey = longPreferencesKey("value.${key.name}")
                state.values[key]?.let { this[valueKey] = it } ?: remove(valueKey)
                val unitKey = stringPreferencesKey("unit.${key.name}")
                val unit = state.units.getValue(key)
                if (key == FieldKey.OTHER_ANTENNA_LENGTH && state.values[key] == null && unit == LengthUnit.MILLIMETERS.name)
                    remove(unitKey)
                else this[unitKey] = unit
            }
            val s = state.settings
            this[stringPreferencesKey("section")] = s.section.name
            this[stringPreferencesKey("theme")] = s.theme.name
            this[stringPreferencesKey("language")] = s.language.name
            this[booleanPreferencesKey("compatibility")] = false
            this[stringPreferencesKey("antenna")] = s.antennaType.name
            this[stringPreferencesKey("sizingLengthUnit")] = s.sizingLengthUnit.name
            this[stringPreferencesKey("trimmingLengthUnit")] = s.trimmingLengthUnit.name
            MatchingPreferences.write(this, state.matching)
            CablePreferences.write(this, state.cable)
            InvertedVPreferences.write(this, state.invertedV)
            GroundPlanePreferences.write(this, state.groundPlane)
            FeedlinePreferences.write(this, state.feedline)
            FullWaveLoopPreferences.write(this, state.fullWaveLoop)
        }.toPreferences()
    }

    fun decode(preferences: Preferences): PersistedCalculatorState {
        if (preferences.asMap().isEmpty()) return PersistedCalculatorState.defaults()
        try {
            val version = requireNotNull(preferences[intPreferencesKey("schema")])
            if (version != SCHEMA) throw UnsupportedSchemaException(version)
            RetiredPreferencesV1.validate(preferences)
            fun string(key: String) = requireNotNull(preferences[stringPreferencesKey(key)])
            val values = FieldKey.entries.mapNotNull { key ->
                val value = preferences[longPreferencesKey("value.${key.name}")]
                require(value != null || key.isLength)
                value?.let { key to it }
            }.toMap()
            val units = FieldKey.entries.associateWith { key ->
                val unit = preferences[stringPreferencesKey("unit.${key.name}")]
                if (key == FieldKey.OTHER_ANTENNA_LENGTH && key !in values) unit ?: LengthUnit.MILLIMETERS.name
                else requireNotNull(unit)
            }
            val settings = CalculatorSettings(
                section = Section.valueOf(string("section")), theme = AppTheme.valueOf(string("theme")),
                antennaType = AntennaType.valueOf(string("antenna")),
                sizingLengthUnit = LengthUnit.valueOf(string("sizingLengthUnit")),
                trimmingLengthUnit = LengthUnit.valueOf(string("trimmingLengthUnit")),
                language = preferences[stringPreferencesKey("language")]?.let(AppLanguage::valueOf) ?: AppLanguage.initial())
            val state = PersistedCalculatorState(
                values, units, settings, MatchingPreferences.read(preferences), CablePreferences.read(preferences),
                InvertedVPreferences.read(preferences), GroundPlanePreferences.read(preferences),
                FeedlinePreferences.read(preferences), FullWaveLoopPreferences.read(preferences),
            )
            val draft = state.toDraft()
            for (key in FieldKey.entries) {
                if (key == FieldKey.FACTOR) require(units[key] == "FACTOR")
                require(draft[key].state(key) is FieldState.Valid || key.isLength && key !in values)
            }
            return state
        } catch (error: UnsupportedSchemaException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw InvalidSettingsException(error)
        } catch (error: ClassCastException) {
            throw InvalidSettingsException(error)
        }
    }
}

/** Wire-only data from the retired screen. Preserve existing values without exposing calculations. */
private object RetiredPreferencesV1 {
    private val fields = listOf("LEN_FREQUENCY", "LEGACY_CURRENT", "LEGACY_TARGET")
    private val coefficients = listOf("lenCoefficient", "trimCoefficient")
    fun defaults(): MutablePreferences = mutablePreferencesOf().apply {
        for (name in fields) {
            this[longPreferencesKey("value.$name")] = if (name == "LEGACY_TARGET") 155_000_000L else 145_000_000L
            this[stringPreferencesKey("unit.$name")] = FrequencyUnit.MEGAHERTZ.name
        }
        for (name in coefficients) this[intPreferencesKey(name)] = 95
        this[booleanPreferencesKey("compatibility")] = false
    }
    fun validate(preferences: Preferences) {
        requireNotNull(preferences[booleanPreferencesKey("compatibility")])
        for (name in fields) {
            val value = requireNotNull(preferences[longPreferencesKey("value.$name")])
            require(value in 100_000L..999_999_000L && value % 1000L == 0L)
            FrequencyUnit.valueOf(requireNotNull(preferences[stringPreferencesKey("unit.$name")]))
        }
        for (name in coefficients) require(requireNotNull(preferences[intPreferencesKey(name)]) in setOf(100, 97, 95, 93))
    }
}
