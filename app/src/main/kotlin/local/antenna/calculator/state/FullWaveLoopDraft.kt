package local.antenna.calculator.state

import java.math.BigDecimal
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.InputError
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.layout.*
import local.antenna.calculator.core.units.LengthUnit

enum class LoopMode { FREQUENCY, PERIMETER }
enum class LoopField { FREQUENCY, PERIMETER, FACTOR, MATCH_FREQUENCY, MATCH_LOAD, MATCH_LINE, MATCH_VF }

data class FullWaveLoopDraft(
    val shape: LoopShape = LoopShape.SQUARE,
    val mode: LoopMode = LoopMode.FREQUENCY,
    val model: LoopModel = LoopModel.IDEAL,
    val feed: LoopFeed = LoopFeed.BASE,
    val inputUnit: LengthUnit = LengthUnit.METERS,
    val outputUnit: LengthUnit = LengthUnit.METERS,
    val fields: Map<LoopField, String> = mapOf(
        LoopField.FREQUENCY to "14,2", LoopField.PERIMETER to "12", LoopField.FACTOR to "1",
        LoopField.MATCH_FREQUENCY to "", LoopField.MATCH_LOAD to "", LoopField.MATCH_LINE to "50",
        LoopField.MATCH_VF to "",
    ),
) {
    val activeField get() = if (mode == LoopMode.FREQUENCY) LoopField.FREQUENCY else LoopField.PERIMETER

    fun edit(field: LoopField, raw: String) = copy(fields = fields + (field to raw.boundedNumericText()))

    fun chooseShape(value: LoopShape) = copy(shape = value, feed = feed.takeIf { it.fits(value) } ?: LoopFeed.BASE)

    fun changeInputUnit(unit: LengthUnit): FullWaveLoopDraft {
        val value = number(LoopField.PERIMETER).validValue() ?: return copy(inputUnit = unit)
        val converted = value.multiply(BigDecimal.valueOf(inputUnit.nanometersPerUnit))
            .divide(BigDecimal.valueOf(unit.nanometersPerUnit)).inputText()
        val candidate = copy(inputUnit = unit).edit(LoopField.PERIMETER, converted)
        return if (candidate.number(LoopField.PERIMETER).validValue() != null) candidate else this
    }

    fun number(field: LoopField): FieldState<BigDecimal> {
        val parsed = PositiveDecimalParser.parse(fields.getValue(field))
        val value = parsed.validValue() ?: return parsed
        val invalid = when (field) {
            LoopField.FREQUENCY, LoopField.MATCH_FREQUENCY -> InputError.TOO_PRECISE.takeIf { value.scale() > 6 }
            LoopField.FACTOR -> when {
                value < BigDecimal("0.5") || value > BigDecimal("1.5") -> InputError.OUT_OF_RANGE
                value.scale() > 4 -> InputError.TOO_PRECISE
                else -> null
            }
            LoopField.MATCH_VF -> InputError.OUT_OF_RANGE.takeIf { value > BigDecimal.ONE }
            LoopField.PERIMETER -> {
                val meters = value.multiply(BigDecimal.valueOf(inputUnit.nanometersPerUnit, 9))
                InputError.OUT_OF_RANGE.takeIf { meters < BigDecimal("0.004") || meters > BigDecimal("10000") }
            }
            else -> null
        }
        return invalid?.let { FieldState.Invalid(it) } ?: parsed
    }

    fun layout(): LoopLayout? {
        val value = number(activeField).validValue() ?: return null
        val factor = number(LoopField.FACTOR).validValue()?.toDouble()
        return when (mode) {
            LoopMode.FREQUENCY -> fullWaveLoop(shape, model, factor, frequencyMhz = value.toDouble())
            LoopMode.PERIMETER -> fullWaveLoop(shape, model, factor, perimeterMeters = value.multiply(BigDecimal.valueOf(inputUnit.nanometersPerUnit, 9)).toDouble())
        }
    }

    fun matching(): QuarterWaveSection? {
        val frequency = number(LoopField.MATCH_FREQUENCY).validValue()?.toDouble() ?: return null
        val load = number(LoopField.MATCH_LOAD).validValue()?.toDouble() ?: return null
        val line = number(LoopField.MATCH_LINE).validValue()?.toDouble() ?: return null
        val vf = number(LoopField.MATCH_VF).validValue()?.toDouble() ?: return null
        return quarterWaveSection(frequency, load, line, vf)
    }

    internal fun canonical(previous: FullWaveLoopDraft): FullWaveLoopDraft {
        val fallback = previous.changeInputUnit(inputUnit)
        val acceptedUnit = if (number(LoopField.PERIMETER).validValue() != null) inputUnit else fallback.inputUnit
        val accepted = copy(inputUnit = acceptedUnit, fields = fields.mapValues { (field, text) ->
            number(field).validValue()?.inputText()
                ?: if (field.ordinal >= LoopField.MATCH_FREQUENCY.ordinal && text.isBlank()) ""
                else fallback.fields.getValue(field)
        })
        return if (accepted.layout() != null) accepted else accepted.copy(
            mode = fallback.mode, model = fallback.model, inputUnit = fallback.inputUnit,
            fields = accepted.fields + listOf(LoopField.FREQUENCY, LoopField.PERIMETER, LoopField.FACTOR)
                .associateWith { fallback.fields.getValue(it) },
        )
    }
}

internal object FullWaveLoopDraftCodec {
    const val KEY = "full-wave-loop.draft.v1"
    private const val HEADER_SIZE = 6

    fun encode(draft: FullWaveLoopDraft): ArrayList<String> = arrayListOf(
        draft.shape.name, draft.mode.name, draft.model.name, draft.feed.name, draft.inputUnit.name, draft.outputUnit.name,
    ).apply { LoopField.entries.forEach { add(draft.fields.getValue(it).boundedNumericText()) } }

    fun decode(values: List<String>?): FullWaveLoopDraft? {
        if (values == null || values.size != HEADER_SIZE + LoopField.entries.size ||
            values.any { it.length > PositiveDecimalParser.MAX_CHARACTERS + 1 }) return null
        return runCatching {
            val shape = LoopShape.valueOf(values[0])
            FullWaveLoopDraft(shape, LoopMode.valueOf(values[1]), LoopModel.valueOf(values[2]),
                LoopFeed.valueOf(values[3]).also { require(it.fits(shape)) },
                LengthUnit.valueOf(values[4]), LengthUnit.valueOf(values[5]),
                LoopField.entries.associateWith { values[HEADER_SIZE + it.ordinal] })
        }.getOrNull()
    }
}
