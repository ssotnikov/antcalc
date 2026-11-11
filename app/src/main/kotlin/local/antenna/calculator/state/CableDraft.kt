package local.antenna.calculator.state

import java.math.BigDecimal
import local.antenna.calculator.core.feedline.*
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.units.*

private fun boundedCableText(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.length > 32) trimmed.take(32) + "!" else trimmed
}

data class CableDraft(
    val mode: CableMode = CableMode.ANGLE_TO_LENGTH,
    val fields: Map<CableField, String> = mapOf(CableField.FREQUENCY to "145", CableField.VELOCITY to "0,66", CableField.LENGTH to "0", CableField.ANGLE to "90"),
    val frequencyUnit: FrequencyUnit = FrequencyUnit.MEGAHERTZ,
    val lengthUnit: LengthUnit = LengthUnit.MILLIMETERS,
    val frequencyValidationUnit: FrequencyUnit = frequencyUnit,
    val lengthValidationUnit: LengthUnit = lengthUnit,
    val checked: Set<CableField> = emptySet(),
    val profileId: String? = null,
) {
    val active: List<CableField> get() = listOf(CableField.FREQUENCY, CableField.VELOCITY,
        if (mode == CableMode.ANGLE_TO_LENGTH) CableField.ANGLE else CableField.LENGTH)
    fun number(field: CableField) = parseCableNumber(fields.getValue(field), field, frequencyValidationUnit, lengthValidationUnit)
    fun edit(field: CableField, text: String) = copy(fields = fields + (field to boundedCableText(text)),
        frequencyValidationUnit = if (field == CableField.FREQUENCY) frequencyUnit else frequencyValidationUnit,
        lengthValidationUnit = if (field == CableField.LENGTH) lengthUnit else lengthValidationUnit)
    fun value(field: CableField): Long? = (number(field) as? FieldState.Valid)?.value
    private fun text(field: CableField, value: Long, f: FrequencyUnit = frequencyUnit, l: LengthUnit = lengthUnit): String =
        BigDecimal.valueOf(value).divide(BigDecimal.valueOf(cableMultiplier(field, f, l))).inputText()
    fun changeFrequencyUnit(unit: FrequencyUnit): CableDraft {
        val value = value(CableField.FREQUENCY)
        return copy(frequencyUnit = unit, frequencyValidationUnit = if (value == null) frequencyValidationUnit else unit,
            fields = fields + (CableField.FREQUENCY to (value?.let { text(CableField.FREQUENCY, it, f = unit) } ?: fields.getValue(CableField.FREQUENCY))))
    }
    fun changeLengthUnit(unit: LengthUnit): CableDraft {
        val value = value(CableField.LENGTH)
        return copy(lengthUnit = unit, lengthValidationUnit = if (value == null) lengthValidationUnit else unit,
            fields = fields + (CableField.LENGTH to (value?.let { text(CableField.LENGTH, it, l = unit) } ?: fields.getValue(CableField.LENGTH))))
    }
    fun canonical(previous: CableDraft): CableDraft = copy(
        fields = CableField.entries.associateWith { field -> text(field, value(field) ?: previous.value(field)!!) },
        frequencyValidationUnit = frequencyUnit, lengthValidationUnit = lengthUnit, checked = emptySet(),
    )
    fun result(profileVelocity: VelocityFactor? = null): CableResult? {
        val frequency = Frequency.ofHertz(value(CableField.FREQUENCY) ?: return null)
        val vf = if (profileId == null) VelocityFactor.ofTenThousandths((value(CableField.VELOCITY) ?: return null).toInt())
            else profileVelocity ?: return null
        return when (mode) {
            CableMode.ANGLE_TO_LENGTH -> cableFromAngle(frequency, vf, value(CableField.ANGLE) ?: return null)
            CableMode.LENGTH_TO_ANGLE -> {
                val length = value(CableField.LENGTH) ?: return null
                cableFromLength(frequency, vf, CableLength.ofNanometers(length))
            }
        }
    }
}

object CableDraftCodec {
    const val KEY = "cable.draft.v1"
    private val fields = listOf(CableField.FREQUENCY, CableField.VELOCITY, CableField.LENGTH, CableField.ANGLE)
    fun encode(draft: CableDraft): ArrayList<String> = arrayListOf(draft.mode.name, draft.frequencyUnit.name, draft.lengthUnit.name,
        draft.frequencyValidationUnit.name, draft.lengthValidationUnit.name).apply {
        fields.forEach { add(boundedCableText(draft.fields.getValue(it))); add((it in draft.checked).toString()) }
        add(draft.profileId.orEmpty())
    }
    fun decode(values: ArrayList<String>?): CableDraft? = try {
        if (values == null || values.size !in 13..14) null else CableDraft(
            CableMode.valueOf(values[0]), fields.mapIndexed { i, f -> f to boundedCableText(values[5 + i * 2]) }.toMap(),
            FrequencyUnit.valueOf(values[1]), LengthUnit.valueOf(values[2]), FrequencyUnit.valueOf(values[3]), LengthUnit.valueOf(values[4]),
            fields.filterIndexed { i, _ -> values[6 + i * 2].toBooleanStrict() }.toSet(),
            values.getOrNull(13)?.ifEmpty { null }?.also { require(it.matches(Regex("[a-z0-9-]{1,48}"))) },
        )
    } catch (_: IllegalArgumentException) { null }
}
