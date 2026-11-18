package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import local.antenna.calculator.R
import local.antenna.calculator.core.matching.*
import local.antenna.calculator.state.*
import java.text.NumberFormat

internal fun MatchingMode.label() = when (this) {
    MatchingMode.SWR -> R.string.matching_swr
    MatchingMode.GAMMA -> R.string.matching_gamma
    MatchingMode.RETURN_LOSS -> R.string.matching_rl
    MatchingMode.IMPEDANCE -> R.string.matching_impedance
}
internal fun MatchingField.label() = when (this) {
    MatchingField.SWR -> R.string.matching_swr
    MatchingField.GAMMA -> R.string.matching_gamma
    MatchingField.RETURN_LOSS -> R.string.matching_rl_input
    MatchingField.RESISTANCE -> R.string.matching_r
    MatchingField.REACTANCE -> R.string.matching_x
    MatchingField.REFERENCE -> R.string.matching_z0
}
internal fun matchingNumber(context: Context, value: Double, places: Int = 3): String =
    NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        isGroupingUsed = false; maximumFractionDigits = places; minimumFractionDigits = 0
    }.format(if (value == 0.0) 0.0 else value)

private fun matchingMeasure(context: Context, value: MatchingMeasure, places: Int = 2) = when (value) {
    is MatchingMeasure.Finite -> if (value.value > 0 && value.value < Math.pow(10.0, -places.toDouble()))
        "< " + matchingNumber(context, Math.pow(10.0, -places.toDouble()), places)
        else matchingNumber(context, value.value, places)
    MatchingMeasure.Infinite -> "∞"
}

internal fun matchingRows(context: Context, result: MatchingResult): List<Pair<Int, String>> = listOf(
    R.string.matching_swr to context.getString(R.string.matching_ratio, matchingMeasure(context, result.swr)),
    R.string.matching_gamma to matchingMeasure(context, MatchingMeasure.Finite(result.magnitude), 6),
    R.string.matching_rl to context.getString(R.string.matching_db, matchingMeasure(context, result.returnLoss)),
    R.string.matching_reflected to context.getString(R.string.matching_percent,
        matchingMeasure(context, MatchingMeasure.Finite(result.reflectedFraction * 100), 2)),
)

private fun matchingDiagramReading(context: Context, draft: MatchingDraft): String {
    fun number(field: MatchingField) = checkNotNull(draft.number(field).value)
    fun format(value: Double) = matchingNumber(context, value, 6)

    return when (draft.mode) {
        MatchingMode.SWR -> {
            val ratio = context.getString(R.string.matching_ratio, format(number(MatchingField.SWR)))
            "${context.getString(R.string.matching_swr)} $ratio"
        }
        MatchingMode.GAMMA -> "|Γ| = ${format(number(MatchingField.GAMMA))}"
        MatchingMode.RETURN_LOSS -> "RL = " +
            context.getString(R.string.matching_db, format(number(MatchingField.RETURN_LOSS)))
        MatchingMode.IMPEDANCE -> {
            val reactance = number(MatchingField.REACTANCE)
            context.getString(R.string.hero_impedance_reading,
                format(number(MatchingField.RESISTANCE)), if (reactance < 0) "−" else "+",
                format(kotlin.math.abs(reactance)), format(number(MatchingField.REFERENCE)))
        }
    }
}

private fun complexText(context: Context, result: MatchingResult): String? = result.complex?.let {
    "Γ = ${matchingNumber(context, it.real, 6)} ${if (it.imaginary < 0) "−" else "+"} j${matchingNumber(context, kotlin.math.abs(it.imaginary), 6)}"
}

internal fun matchingCopy(context: Context, draft: MatchingDraft): String? {
    val result = draft.result() ?: return null
    return buildString {
        appendLine(context.getString(R.string.matching_title))
        appendLine(context.getString(draft.mode.label()))
        appendLine(context.getString(R.string.matching_model))
        draft.active.forEach { appendLine("${context.getString(it.label())}: ${draft.fields.getValue(it)}") }
        matchingRows(context, result).forEach { appendLine("${context.getString(it.first)}: ${it.second}") }
        appendLine("${context.getString(R.string.matching_ml)}: ${context.getString(R.string.matching_db, matchingMeasure(context, result.mismatchLoss))}")
        complexText(context, result)?.let { appendLine(it) }
        result.complex?.let { appendLine("${context.getString(R.string.matching_phase)}: ${it.phaseDegrees?.let { p -> context.getString(R.string.matching_degrees, matchingNumber(context, p)) } ?: context.getString(R.string.matching_undefined)}") }
        appendLine(context.getString(R.string.matching_note))
        append(context.getString(R.string.matching_impedance_note))
    }
}

@Composable
internal fun MatchingScreen(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit, currentState: () -> CalculatorUiState, onCopied: () -> Unit) {
    val draft = state.draft.matching
    val result = draft.result()
    val context = LocalContext.current
    val resources = LocalResources.current
    val focus = LocalFocusManager.current
    val appName = stringResource(R.string.app_name)
    var details by rememberSaveable { mutableStateOf(false) }
    fun change(value: MatchingDraft) { onEvent(CalculatorEvent.Matching(value)) }
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState()).padding(20.dp).testTag("matching-scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StorageNotice(state, onEvent)
        TransmissionDiagram(TransmissionIllustration.REFLECTION, stringResource(R.string.hero_generator),
            stringResource(R.string.hero_load), result?.let { matchingDiagramReading(context, draft) },
            result?.magnitude, R.string.hero_reflect_legend)
        MatchingModePicker(draft.mode) { mode ->
            focus.clearFocus()
            change(currentState().draft.matching.copy(mode = mode))
        }
        draft.active.forEach { key ->
            val parseError = draft.number(key).error
            val visible = parseError != null && (parseError !in listOf(MatchingError.EMPTY, MatchingError.INCOMPLETE) || key in draft.checked)
            val message = if (parseError == MatchingError.RANGE) stringResource(R.string.matching_error_range,
                matchingNumber(context, key.minimum), matchingNumber(context, key.maximum)) else stringResource(R.string.matching_error_number)
            var hadFocus by remember(key, draft.mode) { mutableStateOf(false) }
            OutlinedTextField(draft.fields.getValue(key), { if (it.acceptsDecimalEdit(signed = key.signed)) change(draft.edit(key, it)) },
                label = { Text(stringResource(key.label())) }, singleLine = true, isError = visible,
                enabled = state.editingEnabled,
                supportingText = if (visible) { { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } } else null,
                modifier = Modifier.fillMaxWidth().testTag("matching-${key.name}").semantics { if (visible) error(message) }.onFocusChanged {
                    if (hadFocus && !it.isFocused) {
                        val latest = currentState().draft.matching
                        change(latest.copy(checked = latest.checked + key))
                    }
                    hadFocus = it.isFocused
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (key.signed) KeyboardType.Text else KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        }
        if (result == null) HelpText(R.string.matching_pending) else {
            val rows = matchingRows(context, result)
            val stacked = LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 360
            Layout(
                content = { rows.forEach { (label, value) -> MatchingMetric(label, value, highlightReflection = true) } },
                modifier = Modifier.fillMaxWidth(),
            ) { children, constraints ->
                val columns = if (stacked) 1 else 2
                val columnGap = 12.dp.roundToPx()
                val rowGap = 16.dp.roundToPx()
                val width = (constraints.maxWidth - columnGap * (columns - 1)).coerceAtLeast(0) / columns
                val height = children.maxOf { it.maxIntrinsicHeight(width) }
                val cards = children.map { it.measure(Constraints.fixed(width, height)) }
                layout(constraints.maxWidth, (cards.size / columns) * (height + rowGap) - rowGap) {
                    cards.forEachIndexed { index, card ->
                        card.placeRelative(index % columns * (width + columnGap), index / columns * (height + rowGap))
                    }
                }
            }
        }
        Button(onClick = {
            focus.clearFocus()
            val text = matchingCopy(context, currentState().draft.matching) ?: return@Button
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(appName, text))
            onCopied()
        }, enabled = result != null, modifier = Modifier.fillMaxWidth().testTag("matching-copy")) { Text(stringResource(R.string.copy_result)) }
        HelpText(R.string.matching_note)
        TextButton(onClick = { details = !details }, modifier = Modifier.testTag("matching-details")) {
            Text(stringResource(if (details) R.string.matching_less else R.string.matching_more))
        }
        if (details) {
            Text(stringResource(R.string.matching_details), style = MaterialTheme.typography.titleMedium)
            result?.let {
                MatchingMetric(R.string.matching_ml, stringResource(R.string.matching_db, matchingMeasure(context, it.mismatchLoss)))
                complexText(context, it)?.let { text -> MatchingMetric(R.string.matching_complex, text) }
                it.complex?.let { c -> MatchingMetric(R.string.matching_phase, c.phaseDegrees?.let { p -> stringResource(R.string.matching_degrees, matchingNumber(context, p)) } ?: stringResource(R.string.matching_undefined)) }
            }
            HelpText(R.string.matching_impedance_note)
            HelpText(R.string.matching_model)
            ReflectionIllustrations()
            TransmitterPowerExample()
            listOf(R.drawable.formula_reflection to R.string.matching_formula_gamma_alt,
                R.drawable.formula_swr to R.string.matching_formula_swr_alt,
                R.drawable.formula_return_loss to R.string.matching_formula_rl_alt,
                R.drawable.formula_reflected_power to R.string.matching_formula_power_alt).forEach { (image, alt) ->
                Image(painterResource(image), contentDescription = stringResource(alt), contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
    }
}

@Composable
private fun MatchingModePicker(mode: MatchingMode, onSelected: (MatchingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.matching_source), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("matching-mode")) {
                Text(stringResource(mode.label()), Modifier.weight(1f))
                Text("▾", Modifier.clearAndSetSemantics {})
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                MatchingMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label())) },
                        onClick = { expanded = false; onSelected(option) },
                        modifier = Modifier.testTag("matching-mode-$option").semantics { selected = mode == option },
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchingMetric(label: Int, value: String, highlightReflection: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val valueColor = if (highlightReflection && label == R.string.matching_reflected) {
        if (colors.surface.luminance() < .5f) Color(0xFFF3BA80) else Color(0xFF824410)
    } else colors.primary
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(Modifier.padding(16.dp).testTag("matching-result-${label}").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            }
        }
    }
}
