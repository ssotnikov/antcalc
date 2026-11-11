package local.antenna.calculator.state

import java.math.BigDecimal
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.layout.InvertedVResult
import local.antenna.calculator.core.layout.invertedVLayout
import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor

enum class InvertedVMode { MEASURED, ESTIMATED }

enum class InvertedVField(val initial: String) {
    ARM("5"), FREQUENCY("14,2"), FACTOR("0,95"), HEIGHT("8"), ANGLE("120"),
}

data class InvertedVDraft(
    val mode: InvertedVMode = InvertedVMode.MEASURED,
    val fields: Map<InvertedVField, String> = InvertedVField.entries.associateWith { it.initial },
    val lengthUnit: LengthUnit = LengthUnit.METERS,
    val frequencyUnit: FrequencyUnit = FrequencyUnit.MEGAHERTZ,
) {
    fun number(field: InvertedVField): FieldState<Long> = when (field) {
        InvertedVField.ARM -> QuantityInput.physicalLength(fields.getValue(field), lengthUnit).mapValue { it.nanometers }
        InvertedVField.FREQUENCY -> QuantityInput.frequency(fields.getValue(field), frequencyUnit).mapValue { it.hertz }
        InvertedVField.FACTOR -> QuantityInput.shorteningFactor(fields.getValue(field)).mapValue { it.tenThousandths.toLong() }
        InvertedVField.HEIGHT -> QuantityInput.supportHeightNanometers(fields.getValue(field), lengthUnit)
        InvertedVField.ANGLE -> QuantityInput.includedAngleTenths(fields.getValue(field))
    }

    fun value(field: InvertedVField): Long? = (number(field) as? FieldState.Valid)?.value

    fun edit(field: InvertedVField, text: String): InvertedVDraft = copy(fields = fields + (field to bounded(text)))

    fun changeLengthUnit(unit: LengthUnit): InvertedVDraft = convertUnits(length = unit)
    fun changeFrequencyUnit(unit: FrequencyUnit): InvertedVDraft = convertUnits(frequency = unit)

    private fun convertUnits(length: LengthUnit = lengthUnit, frequency: FrequencyUnit = frequencyUnit): InvertedVDraft {
        val updated = copy(lengthUnit = length, frequencyUnit = frequency)
        return updated.copy(fields = fields.mapValues { (field, raw) ->
            val multiplier = multiplier(field)
            if (multiplier == updated.multiplier(field)) raw
            else value(field)?.let { updated.displayInput(field, it) }.orEmpty()
        })
    }

    fun canonical(previous: InvertedVDraft): InvertedVDraft = copy(
        fields = InvertedVField.entries.associateWith { field ->
            displayInput(field, value(field) ?: checkNotNull(previous.value(field)))
        },
    )

    fun estimateInput(): SizingInput? {
        val hertz = value(InvertedVField.FREQUENCY) ?: return null
        val factor = value(InvertedVField.FACTOR) ?: return null
        return SizingInput(Frequency.ofHertz(hertz), ShorteningFactor.ofTenThousandths(factor.toInt()))
    }

    fun result(): InvertedVResult? {
        val apex = value(InvertedVField.HEIGHT) ?: return null
        val angle = value(InvertedVField.ANGLE)?.toInt() ?: return null
        return when (mode) {
            InvertedVMode.MEASURED -> value(InvertedVField.ARM)?.let { invertedVLayout(PhysicalLength.ofNanometers(it), apex, angle) }
            InvertedVMode.ESTIMATED -> estimateInput()?.let { invertedVLayout(it, apex, angle) }
        }
    }

    private fun multiplier(field: InvertedVField): Long = when (field) {
        InvertedVField.ARM, InvertedVField.HEIGHT -> lengthUnit.nanometersPerUnit
        InvertedVField.FREQUENCY -> frequencyUnit.hertzPerUnit
        InvertedVField.FACTOR -> 10_000
        InvertedVField.ANGLE -> 10
    }

    private fun displayInput(field: InvertedVField, value: Long): String =
        BigDecimal.valueOf(value).divide(BigDecimal.valueOf(multiplier(field))).inputText()
}

internal object InvertedVDraftCodec {
    const val KEY = "inverted-v.draft.v1"

    fun encode(draft: InvertedVDraft): ArrayList<String> =
        arrayListOf(draft.mode.name, draft.lengthUnit.name, draft.frequencyUnit.name).apply {
            InvertedVField.entries.forEach { add(bounded(draft.fields.getValue(it))) }
        }

    fun decode(values: List<String>?): InvertedVDraft? {
        if (values == null || values.size != 3 + InvertedVField.entries.size) return null
        if (values.any { it.length > PositiveDecimalParser.MAX_CHARACTERS + 1 }) return null
        return try {
            InvertedVDraft(
                InvertedVMode.valueOf(values[0]),
                InvertedVField.entries.associateWith { values[3 + it.ordinal] },
                LengthUnit.valueOf(values[1]),
                FrequencyUnit.valueOf(values[2]),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private fun bounded(text: String): String {
    val trimmed = text.trim()
    val limit = PositiveDecimalParser.MAX_CHARACTERS
    return if (trimmed.length > limit) trimmed.take(limit) + "!" else trimmed
}

internal inline fun <T> FieldState<T>.mapValue(transform: (T) -> Long): FieldState<Long> = when (this) {
    is FieldState.Valid -> FieldState.Valid(transform(value))
    is FieldState.Invalid -> this
    FieldState.Empty -> FieldState.Empty
    FieldState.Incomplete -> FieldState.Incomplete
}
