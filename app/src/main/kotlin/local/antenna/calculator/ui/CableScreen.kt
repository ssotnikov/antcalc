package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.feedline.*
import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*
import java.math.BigDecimal
import java.text.NumberFormat

internal fun CableMode.label() = if (this == CableMode.ANGLE_TO_LENGTH) R.string.cable_from_angle else R.string.cable_from_length
private fun CableField.label() = when (this) {
    CableField.FREQUENCY -> R.string.cable_frequency
    CableField.VELOCITY -> R.string.cable_velocity
    CableField.LENGTH -> R.string.cable_length
    CableField.ANGLE -> R.string.cable_angle
}
private fun cableNumber(context: Context, value: BigDecimal, scale: Int = 3): String {
    val step = BigDecimal.ONE.movePointLeft(scale)
    val format = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        isGroupingUsed = false; maximumFractionDigits = scale; roundingMode = java.math.RoundingMode.HALF_UP
    }
    return if (value.signum() > 0 && value < step) "< " + format.format(step) else format.format(value)
}
internal fun cableRows(context: Context, draft: CableDraft, result: CableResult): List<Pair<Int, String>> = listOf(
    R.string.cable_length to context.getString(R.string.cable_value_unit,
        cableNumber(context, result.lengthMeters.movePointRight(9).divide(BigDecimal.valueOf(draft.lengthUnit.nanometersPerUnit)), draft.lengthUnit.displayScale), context.getString(draft.lengthUnit.labelResource())),
    R.string.cable_full_angle to context.getString(R.string.cable_degrees, cableNumber(context, result.fullDegrees)),
    R.string.cable_wrapped_angle to context.getString(R.string.cable_degrees, cableNumber(context, result.wrappedDegrees)),
    R.string.cable_delay to context.getString(R.string.cable_ns, cableNumber(context, result.delayNanoseconds)),
)
internal fun cableCopy(context: Context, draft: CableDraft): String? {
    val result = draft.result() ?: return null
    return buildString {
        appendLine(context.getString(R.string.cable_title))
        appendLine(context.getString(draft.mode.label()))
        draft.active.forEach { field ->
            val unit = when (field) {
                CableField.FREQUENCY -> " " + context.getString(draft.frequencyUnit.labelResource())
                CableField.LENGTH -> " " + context.getString(draft.lengthUnit.labelResource())
                else -> ""
            }
            appendLine("${context.getString(field.label())}: ${draft.fields.getValue(field)}$unit")
        }
        cableRows(context, draft, result).forEach { appendLine("${context.getString(it.first)}: ${it.second}") }
        appendLine(context.getString(R.string.cable_vf_source))
        append(context.getString(R.string.cable_model))
    }
}

@Composable
internal fun CableScreen(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit, currentState: () -> CalculatorUiState, onCopied: () -> Unit) {
    val draft = state.draft.cable
    val result = draft.result()
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val focus = LocalFocusManager.current
    var details by rememberSaveable { mutableStateOf(false) }
    fun change(value: CableDraft) = onEvent(CalculatorEvent.Cable(value))
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("cable-scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StorageNotice(state, onEvent)
        ChoicePicker(stringResource(R.string.cable_direction), CableMode.entries, draft.mode, { stringResource(it.label()) },
            { focus.clearFocus(); change(currentState().draft.cable.copy(mode = it)) }, "cable-mode")
        draft.active.forEach { key ->
            val parsed = draft.number(key)
            val visible = parsed is FieldState.Invalid || parsed !is FieldState.Valid && key in draft.checked
            val multiplier = BigDecimal.valueOf(cableMultiplier(key, draft.frequencyValidationUnit, draft.lengthValidationUnit))
            val validationSuffix = when (key) {
                CableField.FREQUENCY -> " " + stringResource(draft.frequencyValidationUnit.labelResource())
                CableField.LENGTH -> " " + stringResource(draft.lengthValidationUnit.labelResource())
                CableField.ANGLE -> "°"
                CableField.VELOCITY -> ""
            }
            val message = when ((parsed as? FieldState.Invalid)?.error) {
                InputError.OUT_OF_RANGE -> stringResource(R.string.cable_error_range,
                    cableNumber(context, BigDecimal.valueOf(key.minimum).divide(multiplier), 9) + validationSuffix, cableNumber(context, BigDecimal.valueOf(key.maximum).divide(multiplier), 9) + validationSuffix)
                InputError.TOO_PRECISE -> stringResource(R.string.cable_error_step, cableNumber(context, BigDecimal.ONE.divide(multiplier), 9) + validationSuffix)
                else -> stringResource(R.string.cable_error_number)
            }
            var hadFocus by remember(key, draft.mode) { mutableStateOf(false) }
            val suffix = when (key) {
                CableField.FREQUENCY -> ", " + stringResource(draft.frequencyUnit.labelResource())
                CableField.LENGTH -> ", " + stringResource(draft.lengthUnit.labelResource())
                else -> ""
            }
            OutlinedTextField(draft.fields.getValue(key), { change(currentState().draft.cable.edit(key, it)) },
                label = { Text(stringResource(key.label()) + suffix) }, enabled = state.editingEnabled,
                modifier = Modifier.fillMaxWidth().testTag("cable-${key.name}").semantics { if (visible) error(message) }.onFocusChanged {
                    if (hadFocus && !it.isFocused) { val live = currentState().draft.cable; change(live.copy(checked = live.checked + key)) }
                    hadFocus = it.isFocused
                }, singleLine = true, isError = visible,
                supportingText = if (visible) { { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            if (key == CableField.FREQUENCY) ChoicePicker(stringResource(R.string.frequency_units), FrequencyUnit.entries, draft.frequencyUnit,
                { stringResource(it.labelResource()) }, { change(currentState().draft.cable.changeFrequencyUnit(it)) }, "cable-frequency-unit")
            if (key == CableField.VELOCITY) HelpText(R.string.cable_vf_source)
            if (key == CableField.ANGLE) {
                val presets = listOf(90, 180, 360)
                ChoicePicker(stringResource(R.string.cable_preset), presets, presets.firstOrNull { draft.value(CableField.ANGLE) == it * 1000L } ?: -1,
                    { stringResource(when (it) { 90 -> R.string.cable_quarter; 180 -> R.string.cable_half; else -> R.string.cable_wave }) },
                    { change(currentState().draft.cable.edit(CableField.ANGLE, it.toString())) }, "cable-preset")
                if (draft.value(CableField.ANGLE) != null && draft.value(CableField.ANGLE) !in listOf(90_000L, 180_000L, 360_000L)) HelpText(R.string.cable_custom)
            }
        }
        ChoicePicker(stringResource(R.string.length_units), LengthUnit.entries, draft.lengthUnit,
            { stringResource(it.labelResource()) }, { change(currentState().draft.cable.changeLengthUnit(it)) }, "cable-length-unit")
        CableDiagram()
        if (result == null) HelpText(R.string.cable_pending) else {
            cableRows(context, draft, result).forEachIndexed { index, row ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp).testTag("cable-result-$index").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(row.first), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.second, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Button(onClick = {
            focus.clearFocus()
            val text = cableCopy(context, currentState().draft.cable) ?: return@Button
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(appName, text)); onCopied()
        }, enabled = result != null, modifier = Modifier.fillMaxWidth().testTag("cable-copy")) { Text(stringResource(R.string.copy_result)) }
        TextButton(onClick = { details = !details }, modifier = Modifier.testTag("cable-details")) { Text(stringResource(if (details) R.string.matching_less else R.string.cable_details)) }
        if (details) {
            Text(stringResource(R.string.cable_principle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            HelpText(R.string.cable_electrical_length_help)
            CableIllustrations()
            HelpText(R.string.cable_delay_help)
            listOf(R.drawable.formula_cable_speed to R.string.cable_speed_alt,
                R.drawable.formula_cable_phase to R.string.cable_phase_alt,
                R.drawable.formula_cable_length to R.string.cable_length_alt).forEach { (image, alt) ->
                Image(painterResource(image), stringResource(alt), contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth().height(100.dp))
            }
            Text(stringResource(R.string.cable_limits), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            HelpText(R.string.cable_model)
            HelpText(R.string.cable_vf_note)
        }
    }
}

@Composable
private fun CableDiagram() {
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.cable_diagram_alt)
    Text(stringResource(R.string.cable_planes), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.cable_start), modifier = Modifier.weight(1f))
        Text(stringResource(R.string.cable_end), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    Canvas(Modifier.fillMaxWidth().height(60.dp).testTag("cable-diagram").semantics { contentDescription = description }) {
        val left = 16.dp.toPx(); val right = size.width - left
        listOf(.4f, .6f).forEach { y -> drawLine(color, Offset(left, size.height * y), Offset(right, size.height * y), 2.dp.toPx()) }
        listOf(left, right).forEach { x -> drawLine(color, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx()) }
    }
}
