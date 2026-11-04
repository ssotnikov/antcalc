package local.antenna.calculator.state

import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit

/** Frozen v1 wire layout; runtime enum order must never determine saved field positions. */
object DraftCodec {
    const val KEY = "calculator.draft.v1"
    private val fieldsV1 = listOf("SIZING_FREQUENCY", "FACTOR", "ACTUAL_LENGTH", "MEASURED", "TARGET",
        "LEN_FREQUENCY", "LEGACY_CURRENT", "LEGACY_TARGET")
    private val activeFields = FieldKey.entries.associateBy { it.name }
    private val retiredCoefficients = setOf(100, 97, 95, 93)

    fun encode(draft: CalculatorDraft): ArrayList<String> = arrayListOf<String>().apply {
        for (name in fieldsV1) {
            // Retired slots remain on the wire, not in current state or calculations.
            val field = activeFields[name]?.let { draft[it] }
                ?: NumericDraft(if (name == "LEGACY_TARGET") "155" else "145", FrequencyUnit.MEGAHERTZ.name)
            addAll(listOf(field.text.boundedNumericText(), field.unit, field.validationUnit,
                field.checked.toString(), field.focused.toString()))
        }
        val s = draft.settings
        addAll(listOf(s.section.name, s.theme.name, "false", s.antennaType.name,
            s.sizingLengthUnit.name, s.trimmingLengthUnit.name, "95", "95", s.language.name))
    }

    fun decode(values: ArrayList<String>?): CalculatorDraft? {
        if (values == null) return null
        return try {
            require(values.size in (fieldsV1.size * 5 + 8)..(fieldsV1.size * 5 + 9))
            var index = 0
            fun next() = values[index++]
            val fields = fieldsV1.associateWith { name ->
                val field = NumericDraft(next().boundedNumericText(), next(), next(),
                    next().toBooleanStrict(), next().toBooleanStrict())
                when (name) {
                    "FACTOR" -> require(field.unit == "FACTOR" && field.validationUnit == "FACTOR")
                    "ACTUAL_LENGTH" -> { LengthUnit.valueOf(field.unit); LengthUnit.valueOf(field.validationUnit) }
                    else -> { FrequencyUnit.valueOf(field.unit); FrequencyUnit.valueOf(field.validationUnit) }
                }
                field
            }
            val section = Section.valueOf(next())
            val theme = AppTheme.valueOf(next())
            next().toBooleanStrict() // Validate, then discard the retired mode flag.
            val antenna = AntennaType.valueOf(next())
            val sizingUnit = LengthUnit.valueOf(next())
            val trimmingUnit = LengthUnit.valueOf(next())
            repeat(2) { require(next().toInt() in retiredCoefficients) }
            val language = if (index < values.size) AppLanguage.valueOf(next()) else AppLanguage.initial()
            CalculatorDraft(FieldKey.entries.associateWith { fields.getValue(it.name) },
                CalculatorSettings(section, theme, antenna, sizingUnit, trimmingUnit, language))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
