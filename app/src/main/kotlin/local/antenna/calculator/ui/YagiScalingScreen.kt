package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.layout.YagiScaling
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.layout.YagiScaling.Geometry
import local.antenna.calculator.core.layout.YagiScaling.Mode
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.state.boundedNumericText

internal fun Mode.scalingLabel() = when (this) {
    Mode.FREQUENCY -> R.string.ys_frequency
    Mode.DIAMETER -> R.string.ys_diameter
    Mode.BOTH -> R.string.ys_both
}

internal fun Geometry.scalingLabel() = when (this) {
    Geometry.FREE_ELEMENTS -> R.string.ys_free
    Geometry.MOUNTED -> R.string.ys_mounted
}

@Composable
internal fun YagiScalingScreen(
    initial: CalculationSnapshot?,
    onSave: ((CalculationSnapshot) -> Unit)?,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val loaded = remember(initial) { initial?.let(YagiScalingDraft::restore) }
    var draft by rememberSaveable(stateSaver = YagiScalingDraft.saver) { mutableStateOf(loaded ?: YagiScalingDraft()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(1) }
    val result = remember(draft) { draft.result() }
    val diameterMode = draft.mode != Mode.FREQUENCY
    val unitLabel = stringResource(draft.unit.labelResource())
    val before = if (result == null) emptyList() else draft.rows.mapIndexed { index, row -> requireNotNull(draft.element(row, index)) }
    val scaleNumber: (Double) -> String = { matchingNumber(context, it * 1e9 / draft.unit.nanometersPerUnit, 4) }
    val source = stringResource(if (draft.mode == Mode.FREQUENCY) R.string.ys_frequency_source else R.string.ys_formula_source)

    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberPageScrollState())
        .padding(20.dp).testTag("yagi-scaling"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (initial != null && loaded == null) {
            HelpText(R.string.ys_invalid_saved)
            return@Column
        }
        Text(stringResource(R.string.ys_mode), style = MaterialTheme.typography.titleMedium)
        Mode.entries.forEach { mode ->
            val choose = { draft = draft.copy(mode = mode) }
            val modifier = Modifier.fillMaxWidth().testTag("ys-mode-${mode.name}")
            if (draft.mode == mode) FilledTonalButton(choose, modifier) {
                Text(stringResource(mode.scalingLabel()), Modifier.fillMaxWidth())
            } else OutlinedButton(choose, modifier) {
                Text(stringResource(mode.scalingLabel()), Modifier.fillMaxWidth())
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(when (draft.mode) {
                    Mode.FREQUENCY -> R.string.ys_effect_frequency
                    Mode.DIAMETER -> R.string.ys_effect_diameter
                    Mode.BOTH -> R.string.ys_effect_both
                }), style = MaterialTheme.typography.bodyMedium)
                if (draft.sourceHz != null) Text(stringResource(R.string.ys_source_summary, draft.source),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (draft.mode != Mode.DIAMETER) ScalingField(draft.target, R.string.ys_target_hz, "ys-target",
            Modifier.fillMaxWidth(), invalid = draft.target.isNotBlank() && draft.targetHz == null) {
            draft = draft.copy(target = it)
        }
        if (diameterMode) ScalingField(draft.targetDiameter, R.string.ys_common_target_d, "ys-target-diameter",
            Modifier.fillMaxWidth(), unitLabel) { draft = draft.copy(targetDiameter = it) }
        TextButton({ draft = YagiScalingDraft.example().copy(mode = draft.mode) }, Modifier.testTag("ys-example")) {
            Text(stringResource(R.string.ys_example))
        }
        TextButton({ expanded = !expanded }) {
            Text(stringResource(R.string.ys_elements, draft.rows.size) + if (expanded) " ▴" else " ▾")
        }
        if (expanded) {
            ChoicePicker(stringResource(R.string.ys_geometry), Geometry.entries, draft.geometry,
                { stringResource(it.scalingLabel()) }, { draft = draft.copy(geometry = it) }, "ys-geometry")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScalingField(draft.source, if (draft.mode == Mode.DIAMETER) R.string.ys_working_hz else R.string.ys_source_hz,
                    "ys-source", Modifier.weight(1f), invalid = draft.source.isNotBlank() && draft.sourceHz == null) {
                    draft = draft.copy(source = it)
                }
            }
            ChoicePicker(stringResource(R.string.ys_unit), LengthUnit.entries, draft.unit,
                { stringResource(it.labelResource()) }, { draft = draft.inUnit(it) }, "ys-unit")
            ScalingField(draft.diameter, R.string.ys_common_d, "ys-diameter", Modifier.fillMaxWidth(), unitLabel) {
                draft = draft.copy(diameter = it)
            }
            ScalingField(draft.gap, R.string.ys_gap, "ys-gap", Modifier.fillMaxWidth(), unitLabel) { draft = draft.copy(gap = it) }
            HelpText(R.string.ys_gap_help)
            draft.rows.forEachIndexed { index, row ->
                fun edit(next: ScalingRow) {
                    draft = draft.copy(rows = draft.rows.toMutableList().also { it[index] = next })
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(scalingElementLabel(index), style = MaterialTheme.typography.titleMedium)
                            if (index > 1) TextButton({
                                draft = draft.copy(rows = draft.rows.filterIndexed { i, _ -> i != index })
                            }) { Text(stringResource(R.string.ys_remove)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScalingField(row.position, R.string.ys_position, "ys-position-$index",
                                Modifier.weight(1f), unitLabel, enabled = index > 0) { edit(row.copy(position = it)) }
                            ScalingField(row.length, R.string.ys_length, "ys-length-$index",
                                Modifier.weight(1f), unitLabel) { edit(row.copy(length = it)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScalingField(row.diameter, R.string.ys_d, "ys-d-$index",
                                Modifier.weight(1f), unitLabel) { edit(row.copy(diameter = it)) }
                            if (diameterMode && !(index == 1 && draft.split)) {
                                ScalingField(row.target, R.string.ys_target_d, "ys-target-d-$index",
                                    Modifier.weight(1f), unitLabel) { edit(row.copy(target = it)) }
                            }
                        }
                    }
                }
            }
            OutlinedButton({ draft = draft.copy(rows = draft.rows + ScalingRow("", "")) },
                enabled = draft.rows.size < YagiScaling.MAX_ELEMENTS) { Text(stringResource(R.string.ys_add)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.ys_results), style = MaterialTheme.typography.titleMedium)
        if (result == null) {
            HelpText(if (draft.geometry == Geometry.MOUNTED && diameterMode) R.string.ys_mounted_error else R.string.ys_pending)
        } else {
            val factor = matchingNumber(context, result.factor, 6)
            val rows = result.elements.mapIndexed { index, element ->
                val delta = element.length - before[index].length
                listOf(scalingElementName(index), scaleNumber(element.position), scaleNumber(element.length),
                    scaleNumber(element.diameter), (if (delta > 0) "+" else "") + scaleNumber(delta))
            }
            val labels = listOf(R.string.ys_element, R.string.ys_position, R.string.ys_length, R.string.ys_d, R.string.ys_delta)
                .map { stringResource(it) }
            val gapText = result.feedGap?.let { "${stringResource(R.string.ys_gap)} ${scaleNumber(it)} $unitLabel" }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (draft.mode != Mode.DIAMETER) Text(stringResource(R.string.ys_factor, factor), color = MaterialTheme.colorScheme.primary)
                    Text(scalingElementLabel(1), style = MaterialTheme.typography.titleSmall)
                    ScalingComparison(before[1], result.elements[1], scaleNumber, unitLabel)
                    gapText?.let { Text(it) }
                }
            }
            ScalingDetails(R.string.ys_scheme) {
                val current = selected.coerceIn(result.elements.indices)
                ScalingDrawing(before, result.elements, current) { selected = it }
                ChoicePicker(stringResource(R.string.ys_element), result.elements.indices.toList(), current,
                    { scalingElementLabel(it) }, { selected = it }, "ys-diagram-selection")
                Text(scalingElementLabel(current), style = MaterialTheme.typography.titleSmall)
                ScalingComparison(before[current], result.elements[current], scaleNumber, unitLabel)
            }
            ScalingDetails(R.string.ys_all) {
                before.indices.forEach { index ->
                    Text(scalingElementLabel(index), style = MaterialTheme.typography.titleSmall)
                    ScalingComparison(before[index], result.elements[index], scaleNumber, unitLabel)
                }
            }
            SelectableText(source)
            if (diameterMode) HelpText(R.string.ys_estimate)
            if (draft.geometry == Geometry.MOUNTED) HelpText(R.string.ys_similarity)
            if (onSave != null) Button({ onSave(draft.snapshot(result)) },
                modifier = Modifier.fillMaxWidth().testTag("ys-save")) { Text(stringResource(R.string.ys_save)) }
            OutlinedButton({
                val report = buildString {
                    appendLine(resources.getString(R.string.ys_title))
                    appendLine(resources.getString(R.string.ys_report_frequency, draft.source, (if (draft.mode == Mode.DIAMETER) draft.source else draft.target)))
                    appendLine(resources.getString(draft.mode.scalingLabel()))
                    appendLine(resources.getString(draft.geometry.scalingLabel()))
                    appendLine(resources.getString(R.string.ys_factor, factor))
                    appendLine(resources.getString(R.string.ys_table_unit, unitLabel))
                    appendLine(labels.joinToString(" | "))
                    rows.forEach { appendLine(it.joinToString(" | ")) }
                    gapText?.let { appendLine(it) }
                    appendLine(source)
                    if (diameterMode) appendLine(resources.getString(R.string.ys_estimate))
                    if (draft.geometry == Geometry.MOUNTED) appendLine(resources.getString(R.string.ys_similarity))
                    append(YAGI_SCALING_URL)
                }
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.ys_title), report))
                onCopied()
            }, Modifier.fillMaxWidth().testTag("ys-copy")) { Text(stringResource(R.string.copy_result)) }
        }
        ScalingDetails(R.string.ys_formulas) {
            SelectableText("k = f₁ / f₂\nλ = c / f₂\nC(d) = 430,3 log₁₀(2λ/d) − 320\nL₂ = λ/2 × [1 + C(d₁)/C(d₂) × (2L₁/λ − 1)]")
            HelpText(R.string.ys_formula_help)
        }
        ScalingDetails(R.string.ys_sources) {
            HelpText(R.string.ys_credits)
            TextButton({ openSourceWithFeedback(context, YAGI_SCALING_URL) }) { Text(stringResource(R.string.ys_article)) }
            TextButton({ openSourceWithFeedback(context, YAGI_SCALING_BOOK) }) { Text(stringResource(R.string.ys_book)) }
            HelpText(R.string.ys_rights)
            TextButton({ openSourceWithFeedback(context, YAGI_SCALING_TERMS) }) { Text(stringResource(R.string.ys_terms)) }
        }
    }
}

@Composable
private fun ScalingField(
    value: String, label: Int, tag: String, modifier: Modifier, unit: String = "",
    invalid: Boolean = false, enabled: Boolean = true, onChange: (String) -> Unit,
) {
    OutlinedTextField(value, { if (it.acceptsDecimalEdit()) onChange(it.boundedNumericText()) },
        modifier.testTag(tag), label = { Text(stringResource(label) + if (unit.isEmpty()) "" else ", $unit") },
        singleLine = true, enabled = enabled,
        isError = invalid || value.isNotBlank() && scalingDecimal(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun ScalingTableRow(cells: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEachIndexed { index, value ->
            Text(value, Modifier.weight(if (index == 0) 1.4f else 1f).padding(end = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (index == cells.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
    HorizontalDivider()
}

@Composable
private fun ScalingDetails(title: Int, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton({ expanded = !expanded }) { Text(stringResource(title) + if (expanded) " ▴" else " ▾") }
    if (expanded) content()
}

@Composable
private fun ScalingDrawing(before: List<Element>, after: List<Element>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.ys_diagram)
    val all = before + after
    val span = all.maxOf(Element::position).coerceAtLeast(0.000001)
    val length = all.maxOf(Element::length)
    fun xAt(position: Double, width: Float, height: Float): Float {
        val scale = minOf(width * .82 / span, height * .82 / length)
        return (width / 2 + (position - span / 2) * scale).toFloat()
    }
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Canvas(Modifier.fillMaxWidth().height(154.dp).semantics { contentDescription = description }
                .pointerInput(after, span, length) {
                    detectTapGestures { tap ->
                        val nearest = after.indices.minBy { kotlin.math.abs(xAt(after[it].position, size.width.toFloat(), size.height.toFloat()) - tap.x) }
                        onSelect(nearest)
                    }
                }) {
                val scale = minOf(size.width * .82 / span, size.height * .82 / length)
                val center = size.height / 2
                drawLine(colors.outline, Offset(0f, center), Offset(size.width, center), 1.dp.toPx())
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                for ((elements, original) in listOf(before to true, after to false)) {
                    elements.forEachIndexed { index, element ->
                        val x = xAt(element.position, size.width, size.height)
                        val half = (element.length * scale / 2).toFloat()
                        val color = if (original) colors.onSurfaceVariant else if (index == selected) colors.tertiary else colors.primary
                        drawLine(color, Offset(x, center - half), Offset(x, center + half),
                            (if (original) 2 else 3).dp.toPx(), pathEffect = if (original) dash else null)
                    }
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}


@Composable
internal fun scalingElementLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.ys_reflector)
    1 -> stringResource(R.string.ys_driven)
    else -> stringResource(R.string.ys_director, index - 1)
}

@Composable
private fun ScalingComparison(before: Element, after: Element, number: (Double) -> String, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ScalingTableRow(listOf(unit, stringResource(R.string.ys_before), stringResource(R.string.ys_after)))
        ScalingTableRow(listOf(stringResource(R.string.ys_length), number(before.length), number(after.length)))
        ScalingTableRow(listOf(stringResource(R.string.ys_position), number(before.position), number(after.position)))
        ScalingTableRow(listOf(stringResource(R.string.ys_d), number(before.diameter), number(after.diameter)))
    }
}
