package local.antenna.calculator.ui

import android.content.Context
import local.antenna.calculator.R
import local.antenna.calculator.core.format.LengthFormatter
import local.antenna.calculator.core.trimming.*
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*
import kotlin.math.abs
import java.math.BigDecimal

data class ResultRow(val label: Int, val value: String, val tag: String)
data class ResultGroup(
    val title: Int, val rows: List<ResultRow>, val notes: List<Int> = emptyList(),
    val diagrams: Boolean = false, val warning: Int? = null, val tuningDirection: AdjustmentDirection? = null,
)
data class ResultPresentation(val groups: List<ResultGroup>, val copyText: String?)

internal fun coefficientText(percent: Int): String = BigDecimal.valueOf(percent.toLong(), 2).toPlainString().replace('.', ',')

fun FrequencyUnit.labelResource(): Int = when (this) {
    FrequencyUnit.KILOHERTZ -> R.string.khz; FrequencyUnit.MEGAHERTZ -> R.string.mhz; FrequencyUnit.GIGAHERTZ -> R.string.ghz
}
fun LengthUnit.labelResource(): Int = when (this) {
    LengthUnit.MILLIMETERS -> R.string.mm; LengthUnit.CENTIMETERS -> R.string.cm; LengthUnit.METERS -> R.string.m
}

/** Builds display and clipboard from the same current, unrounded result, with no stored output. */
fun presentResults(context: Context, state: CalculatorUiState): ResultPresentation {
    val draft = state.draft
    val settings = draft.settings
    val sizing = settings.section == Section.SIZING
    val unit = if (sizing) settings.sizingLengthUnit else settings.trimmingLengthUnit
    val groups = mutableListOf<ResultGroup>()
    val summary = mutableListOf<String>()
    fun line(label: Int, value: String) = context.getString(R.string.label_value, context.getString(label), value)
    fun input(key: FieldKey, label: Int) {
        val field = draft[key]
        val text = localizedNumber(context, field.text.trim())
        val value = when (key) {
            FieldKey.FACTOR -> text
            FieldKey.ACTUAL_LENGTH -> context.getString(R.string.value_unit, text, context.getString(LengthUnit.valueOf(field.unit).labelResource()))
            else -> field.frequency().validValue()?.let {
                context.frequencyWithUnit(it.hertz, FrequencyUnit.valueOf(field.unit))
            } ?: text
        }
        summary += line(label, value)
    }
    fun length(value: Double) = formatLength(context, value, unit)
    var complete = false
    if (sizing) {
        state.sizing.result?.let { result ->
            complete = true
            summary += context.getString(R.string.sizes)
            input(FieldKey.SIZING_FREQUENCY, R.string.frequency)
            input(FieldKey.FACTOR, R.string.factor)
            groups += ResultGroup(R.string.physical_sizes, listOf(
                ResultRow(R.string.radiator_length, length(result.monopoleMeters), "monopole-result"),
                ResultRow(R.string.dipole_total, length(result.dipoleTotalMeters), "dipole-total"),
                ResultRow(R.string.dipole_arm, length(result.dipoleArmMeters), "dipole-arm"),
            ), listOf(R.string.approximate, R.string.conductor_note, R.string.sizing_note), diagrams = true)
        }
        state.sizing.ideal?.let { ideal ->
            groups += ResultGroup(R.string.ideal_sizes, listOf(
                ResultRow(R.string.wavelength, length(ideal.wavelengthMeters), "wavelength"),
                ResultRow(R.string.ideal_quarter, length(ideal.monopoleMeters), "ideal-quarter"),
                ResultRow(R.string.ideal_half, length(ideal.dipoleTotalMeters), "ideal-half"),
                ResultRow(R.string.ideal_five_eighths, length(ideal.fiveEighthsMeters), "ideal-five-eighths"),
            ), listOf(R.string.five_eighths_note))
        }
    } else {
        state.trimming.result?.let { result ->
            complete = true
            val dipole = result.input.antennaType == AntennaType.SYMMETRIC_DIPOLE
            summary += context.getString(R.string.trimming)
            summary += context.getString(if (dipole) R.string.dipole else R.string.monopole)
            input(FieldKey.ACTUAL_LENGTH, if (dipole) R.string.dipole_actual_length else R.string.actual_length)
            input(FieldKey.MEASURED, R.string.measured_frequency)
            input(FieldKey.TARGET, R.string.target_frequency)
            val rows = mutableListOf(
                ResultRow(if (dipole) R.string.total_adjustment else R.string.adjustment,
                    formatAdjustment(context, result.direction, abs(result.deltaMeters), unit), "adjustment"),
                ResultRow(if (dipole) R.string.new_total else R.string.new_length, length(result.newLengthMeters), "new-length"),
            )
            result.dipoleArmAdjustment?.let { arm ->
                rows += ResultRow(R.string.arm_adjustment, formatAdjustment(context, result.direction, abs(arm.deltaMeters), unit), "arm-adjustment")
                rows += ResultRow(R.string.new_arm, length(arm.newLengthMeters), "new-arm")
            }
            val notes = mutableListOf(R.string.approximate, R.string.trimming_note)
            if (dipole) notes += listOf(R.string.dipole_note, R.string.conductor_note)
            if (TrimmingWarning.LARGE_FREQUENCY_CHANGE in result.warnings) notes += R.string.large_change_note
            if (TrimmingWarning.RESULT_OUTSIDE_INPUT_LENGTH_RANGE in result.warnings) notes += R.string.outside_length_note
            val warning = if (TrimmingWarning.LENGTH_DIFFERS_FROM_MODEL in result.warnings) R.string.trimming_model_warning else null
            groups += ResultGroup(R.string.trimming, rows, notes, warning = warning,
                tuningDirection = result.direction.takeUnless { it == AdjustmentDirection.UNCHANGED || warning != null })
        }
    }
    val copy = if (complete && state.editingEnabled) buildList {
        addAll(summary)
        for (group in groups) {
            add("")
            add(context.getString(group.title))
            group.warning?.let { add(context.getString(it)) }
            addAll(group.rows.map { line(it.label, it.value) })
            addAll(group.notes.map(context::getString))
        }
    }.joinToString("\n") else null
    return ResultPresentation(groups, copy)
}

fun formatLength(context: Context, meters: Double, unit: LengthUnit): String {
    val value = LengthFormatter.format(meters, unit)
    val text = context.getString(R.string.value_unit, localizedNumber(context, value.number), context.getString(unit.labelResource()))
    return if (value.isBelowResolution) context.getString(R.string.less_than, text) else text
}

private fun formatAdjustment(context: Context, direction: AdjustmentDirection, meters: Double, unit: LengthUnit): String {
    if (direction == AdjustmentDirection.UNCHANGED) return context.getString(R.string.unchanged)
    val value = LengthFormatter.format(meters, unit)
    val label = when {
        direction == AdjustmentDirection.SHORTEN && value.isBelowResolution -> R.string.shorten_less
        direction == AdjustmentDirection.SHORTEN -> R.string.shorten
        value.isBelowResolution -> R.string.lengthen_less
        else -> R.string.lengthen
    }
    return context.getString(label, context.getString(R.string.value_unit, localizedNumber(context, value.number), context.getString(unit.labelResource())))
}

internal fun localizedNumber(context: Context, text: String): String =
    if (context.resources.configuration.locales[0].language == "ru") text.replace('.', ',') else text.replace(',', '.')
