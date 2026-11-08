package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*

@Composable
fun CalculatorInputs(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit) {
    val draft = state.draft
    val settings = draft.settings
    val sizing = settings.section == Section.SIZING
    val context = LocalContext.current
    Text(stringResource(R.string.input_data), style = MaterialTheme.typography.titleMedium)
    if (sizing) {
        NumericInput(FieldKey.SIZING_FREQUENCY, R.string.frequency, state, onEvent)
        NumericInput(FieldKey.FACTOR, R.string.factor, state, onEvent)
        val factor = draft[FieldKey.FACTOR].factor().validValue()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShorteningFactor.PRESETS.forEach { preset ->
                val value = java.math.BigDecimal.valueOf(preset.tenThousandths.toLong(), 4).inputText()
                FilterChip(selected = factor == preset, onClick = { onEvent(CalculatorEvent.Edit(FieldKey.FACTOR, value)) },
                    label = { Text(localizedNumber(context, coefficientText(preset.tenThousandths / 100))) }, modifier = Modifier.testTag("factor-${preset.tenThousandths}"))
            }
        }
        if (factor != null && factor !in ShorteningFactor.PRESETS) Text(stringResource(R.string.custom_factor), style = MaterialTheme.typography.labelLarge)
        HelpText(R.string.factor_note)
        if (factor != null && factor.tenThousandths < 9000) HelpText(R.string.factor_low_note)
    } else {
        ChoicePicker(stringResource(R.string.antenna_type), AntennaType.entries, settings.antennaType,
            { stringResource(if (it == AntennaType.MONOPOLE) R.string.monopole else R.string.dipole) },
            { onEvent(CalculatorEvent.Settings(settings.copy(antennaType = it))) }, "antenna")
        AntennaDiagram(dipole = settings.antennaType == AntennaType.SYMMETRIC_DIPOLE)
        NumericInput(FieldKey.ACTUAL_LENGTH, if (settings.antennaType == AntennaType.MONOPOLE) R.string.actual_length else R.string.dipole_actual_length, state, onEvent)
        NumericInput(FieldKey.MEASURED, R.string.measured_frequency, state, onEvent)
        NumericInput(FieldKey.TARGET, R.string.target_frequency, state, onEvent)
        if (draft[FieldKey.ACTUAL_LENGTH].text.isEmpty()) HelpText(R.string.measured_length_required)
    }
    ChoicePicker(stringResource(R.string.result_units), LengthUnit.entries,
        if (sizing) settings.sizingLengthUnit else settings.trimmingLengthUnit, { stringResource(it.labelResource()) },
        { onEvent(CalculatorEvent.Settings(if (sizing) settings.copy(sizingLengthUnit = it) else settings.copy(trimmingLengthUnit = it))) }, "output-unit")
}

@Composable
private fun NumericInput(key: FieldKey, label: Int, state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit) {
    val field = state.draft[key]
    val error = inputErrorResource(key, field)
    val errorMessage = error?.let { stringResource(it) }
    val focusManager = LocalFocusManager.current
    val requester = remember(key) { FocusRequester() }
    var hadFocus by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { if (field.focused) requester.requestFocus() }
    OutlinedTextField(value = field.text, onValueChange = { onEvent(CalculatorEvent.Edit(key, it)) },
        modifier = Modifier.fillMaxWidth().testTag("field-${key.name}").semantics {
            if (errorMessage != null) error(errorMessage)
        }.focusRequester(requester).onFocusChanged {
            // Initial unfocused composition is not a user blur and must not reveal empty-field errors.
            if (it.isFocused || hadFocus) onEvent(CalculatorEvent.Focus(key, it.isFocused))
            hadFocus = it.isFocused
        },
        label = { Text(stringResource(label)) }, singleLine = true,
        isError = error != null, supportingText = if (errorMessage != null) { { Text(errorMessage, Modifier.testTag("error-${key.name}").semantics { liveRegion = LiveRegionMode.Polite }) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
    if (key != FieldKey.FACTOR) {
        if (key == FieldKey.ACTUAL_LENGTH) {
            ChoicePicker(stringResource(R.string.length_units), LengthUnit.entries, LengthUnit.valueOf(field.unit),
                { stringResource(it.labelResource()) }, { onEvent(CalculatorEvent.ChangeUnit(key, it.name)) }, "unit-${key.name}")
        } else {
            ChoicePicker(stringResource(R.string.frequency_units), FrequencyUnit.entries, FrequencyUnit.valueOf(field.unit),
                { stringResource(it.labelResource()) }, { onEvent(CalculatorEvent.ChangeUnit(key, it.name)) }, "unit-${key.name}")
        }
    }
}

fun inputErrorResource(key: FieldKey, field: NumericDraft): Int? {
    if (!field.hasVisibleError(key)) return null
    return when (val parsed = field.state(key)) {
        FieldState.Empty -> R.string.error_empty
        FieldState.Incomplete -> R.string.error_incomplete
        is FieldState.Valid -> null
        is FieldState.Invalid -> when (parsed.error) {
            InputError.TOO_LONG -> R.string.error_too_long
            InputError.INVALID_NUMBER -> R.string.error_number
            InputError.NON_POSITIVE -> R.string.error_positive
            InputError.OUT_OF_RANGE -> when (key) {
                FieldKey.FACTOR -> R.string.error_factor_range
                FieldKey.ACTUAL_LENGTH -> R.string.error_length_range
                else -> R.string.error_frequency_range
            }
            InputError.TOO_PRECISE -> when (key) {
                FieldKey.FACTOR -> R.string.error_factor_step
                FieldKey.ACTUAL_LENGTH -> R.string.error_length_step
                else -> R.string.error_frequency_step
            }
        }
    }
}

@Composable
fun <T> ChoicePicker(label: String, options: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { value ->
                val option = text(value)
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(option) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-$value").semantics {
                        contentDescription = "$label: $option"
                    })
            }
        }
    }
}

@Composable
fun HelpText(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
