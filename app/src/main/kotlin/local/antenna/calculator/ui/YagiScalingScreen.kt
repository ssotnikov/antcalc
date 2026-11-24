package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.layout.YagiScaling
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.layout.YagiScaling.Geometry
import local.antenna.calculator.core.layout.YagiScaling.Mode
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.nec.OpenNec
import local.antenna.calculator.nec.necVersion
import local.antenna.calculator.state.boundedNumericText
import kotlin.math.roundToLong

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
    necAvailable: () -> Boolean = { OpenNec.available },
    onSegmentation: ((ArrayList<String>) -> Unit)? = null,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val loaded = remember(initial) { initial?.let(YagiScalingDraft::restore) }
    var draft by rememberSaveable(stateSaver = YagiScalingDraft.saver) { mutableStateOf(loaded ?: YagiScalingDraft()) }
    var selected by rememberSaveable { mutableIntStateOf(1) }
    val result = remember(draft) { draft.result() }
    val adjustmentGeometry = result?.elements?.map { element ->
        listOf(element.position, element.length, element.diameter).map { (it * 1e9).roundToLong() }
    }
    var appliedLengths by rememberSaveable(draft.mode, draft.geometry, draft.sourceHz, draft.targetHz,
        draft.split, adjustmentGeometry) {
        mutableStateOf(if (draft == loaded && result != null) initial?.appliedNecLengths(result) else null)
    }
    var appliedVersion by rememberSaveable(draft.mode, draft.geometry, draft.sourceHz, draft.targetHz,
        draft.split, adjustmentGeometry) {
        mutableIntStateOf(if (draft == loaded) initial?.appliedNecVersion() ?: OpenNec.VERSION_CODE else OpenNec.VERSION_CODE)
    }
    val diameterMode = draft.mode != Mode.FREQUENCY
    val unitLabel = stringResource(draft.unit.labelResource())
    val before = if (result == null) emptyList() else draft.rows.mapIndexed { index, row -> requireNotNull(draft.element(row, index)) }
    val scaleNumber: (Double) -> String = { matchingNumber(context, it * 1e9 / draft.unit.nanometersPerUnit, 4) }
    val lengthPlaces = when (draft.unit) {
        LengthUnit.MILLIMETERS -> 1
        LengthUnit.CENTIMETERS -> 2
        LengthUnit.METERS -> 4
    }
    val lengthNumber: (Double) -> String = if (draft.mode == Mode.BOTH) {
        { matchingNumber(context, it * 1e9 / draft.unit.nanometersPerUnit, lengthPlaces) }
    } else scaleNumber
    val signedNumber: (Double) -> String = { value ->
        when (val formatted = lengthNumber(value)) {
            "0", "-0" -> "0"
            else -> if (value > 0) "+$formatted" else formatted
        }
    }
    val resultNumber: (Double) -> String = {
        matchingNumber(context, it * 1e9 / draft.unit.nanometersPerUnit, lengthPlaces)
    }
    val diameterNumber: (Double) -> String = {
        matchingNumber(context, it * 1e9 / draft.unit.nanometersPerUnit, lengthPlaces + 1)
    }
    val detailDelta: (Double) -> String = { delta ->
        val value = resultNumber(delta)
        if (delta != 0.0 && (value == "0" || value == "-0")) {
            (if (delta > 0) "+" else "−") + "< " + resultNumber(draft.unit.nanometersPerUnit / 1e9 / Math.pow(10.0, lengthPlaces.toDouble()))
        } else if (delta > 0) "+$value" else value
    }
    val source = stringResource(if (draft.mode == Mode.FREQUENCY) R.string.ys_frequency_source else R.string.ys_formula_source)

    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberPageScrollState())
        .padding(20.dp).testTag("yagi-scaling"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (initial != null && loaded == null) {
            HelpText(R.string.ys_invalid_saved)
            return@Column
        }
        val mounted = draft.geometry == Geometry.MOUNTED
        Column(Modifier.selectableGroup()) {
            Text(stringResource(R.string.ys_geometry), style = MaterialTheme.typography.titleMedium)
            Geometry.entries.forEach { geometry ->
                val active = draft.geometry == geometry
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("ys-geometry-$geometry")
                        .selectable(active, role = Role.RadioButton) {
                            draft = draft.copy(geometry = geometry,
                                mode = if (geometry == Geometry.MOUNTED) Mode.FREQUENCY else draft.mode)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = active, onClick = null)
                    Text(stringResource(geometry.scalingLabel()))
                }
            }
        }
        HelpText(if (mounted) R.string.ys_mounted_error else R.string.ys_model_hint)
        TextButton({
            draft = YagiScalingDraft.example().copy(mode = draft.mode, target = "435", targetDiameter = "8").inUnit(draft.unit)
        }, Modifier.testTag("ys-example")) { Text(stringResource(R.string.ys_example)) }
        TextButton({
            draft = YagiScalingDraft.r3udkExample(draft.unit)
        }, Modifier.testTag("ys-example-r3udk")) { Text(stringResource(R.string.ys_example_r3udk)) }
        if (mounted) HelpText(R.string.ys_example_r3udk_scope)

        Text(stringResource(R.string.ys_mode), style = MaterialTheme.typography.titleMedium)
        Mode.entries.forEach { mode ->
            val choose = { draft = draft.copy(mode = mode) }
            val modifier = Modifier.fillMaxWidth().testTag("ys-mode-${mode.name}")
            if (draft.mode == mode) FilledTonalButton(choose, modifier) {
                Text(stringResource(mode.scalingLabel()), Modifier.fillMaxWidth())
            } else OutlinedButton(choose, modifier, enabled = !mounted || mode == Mode.FREQUENCY) {
                Text(stringResource(mode.scalingLabel()), Modifier.fillMaxWidth())
            }
        }
        HelpText(when (draft.mode) {
            Mode.FREQUENCY -> R.string.ys_effect_frequency
            Mode.DIAMETER -> R.string.ys_effect_diameter
            Mode.BOTH -> R.string.ys_effect_both
        })
        Text(stringResource(R.string.ys_source_parameters), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScalingField(draft.source, if (draft.mode == Mode.DIAMETER)
                R.string.ys_working_hz else R.string.ys_source_hz, "ys-source",
                Modifier.weight(1f), invalid = draft.sourceHz == null, errorText = R.string.ys_frequency_invalid) {
                draft = draft.copy(source = it)
            }
            if (draft.mode != Mode.DIAMETER) {
                ScalingField(draft.target, R.string.ys_target_hz, "ys-target", Modifier.weight(1f),
                    invalid = draft.targetHz == null, errorText = R.string.ys_frequency_invalid) {
                    draft = draft.copy(target = it)
                }
            }
        }
        ChoicePicker(stringResource(R.string.ys_unit), LengthUnit.entries, draft.unit,
            { stringResource(it.labelResource()) }, { draft = draft.inUnit(it) }, "ys-unit")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScalingField(draft.diameter, R.string.ys_common_d, "ys-diameter", Modifier.weight(1f), unitLabel,
                invalid = draft.diameter.isNotBlank() && scalingDecimal(draft.diameter)?.let { it > 0 } != true,
                errorText = R.string.ys_positive_invalid) { draft = draft.copy(diameter = it) }
            if (diameterMode) {
                ScalingField(draft.targetDiameter, R.string.ys_common_target_d, "ys-target-diameter",
                    Modifier.weight(1f), unitLabel,
                    invalid = draft.targetDiameter.isNotBlank() && scalingDecimal(draft.targetDiameter)?.let { it > 0 } != true,
                    errorText = R.string.ys_positive_invalid) { draft = draft.copy(targetDiameter = it) }
            }
        }
        HelpText(R.string.ys_default_d_help)
        Text(stringResource(R.string.ys_elements, draft.rows.size), style = MaterialTheme.typography.titleMedium)
        val gap = scalingDecimal(draft.gap)
        val drivenSpan = scalingDecimal(draft.rows[1].length)
        val invalidGap = draft.gap.isNotBlank() &&
            (gap == null || gap < 0 || drivenSpan != null && gap >= drivenSpan)
        ScalingField(draft.gap, R.string.ys_gap, "ys-gap", Modifier.fillMaxWidth(), unitLabel,
            invalid = invalidGap, errorText = R.string.ys_gap_invalid) { draft = draft.copy(gap = it) }
        HelpText(R.string.ys_gap_help)
        draft.rows.forEachIndexed { index, row ->
            key(index) {
                val position = scalingDecimal(row.position)
                val previous = draft.rows.getOrNull(index - 1)?.let { scalingDecimal(it.position) }
                val length = scalingDecimal(row.length)
                val diameter = scalingDecimal(row.diameter.ifBlank { draft.diameter })
                val targetDiameter = scalingDecimal(row.target.ifBlank { draft.targetDiameter })
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
                        if (index == 0) {
                            HelpText(R.string.ys_reflector_origin, unitLabel)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (index != 0) {
                                ScalingField(row.position, R.string.ys_position, "ys-position-$index",
                                    Modifier.weight(1f), unitLabel,
                                    invalid = position == null || position <= 0 || previous != null && position <= previous,
                                    errorText = R.string.ys_position_invalid) { edit(row.copy(position = it)) }
                            }
                            ScalingField(row.length, R.string.ys_length, "ys-length-$index",
                                Modifier.weight(1f), unitLabel, invalid = length == null || length <= 0,
                                errorText = R.string.ys_positive_invalid) { edit(row.copy(length = it)) }
                        }
                        ScalingDetails(R.string.ys_element_diameters, Modifier.testTag("ys-element-diameters-$index"),
                            initiallyExpanded = row.diameter.isNotBlank() || row.target.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScalingField(row.diameter, R.string.ys_d, "ys-d-$index",
                                    Modifier.weight(1f), unitLabel,
                                    invalid = row.diameter.isNotBlank() &&
                                        (diameter == null || diameter <= 0 || length != null && diameter >= length),
                                    errorText = R.string.ys_diameter_invalid) { edit(row.copy(diameter = it)) }
                                if (diameterMode && !(index == 1 && draft.split)) {
                                    ScalingField(row.target, R.string.ys_target_d, "ys-target-d-$index",
                                        Modifier.weight(1f), unitLabel,
                                        invalid = row.target.isNotBlank() && (targetDiameter == null || targetDiameter <= 0),
                                        errorText = R.string.ys_positive_invalid) { edit(row.copy(target = it)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton({ draft = draft.copy(rows = draft.rows + ScalingRow("", "")) },
            enabled = draft.rows.size < YagiScaling.MAX_ELEMENTS) { Text(stringResource(R.string.ys_add)) }
        if (result == null) {
            val missing = draft.hasMissingRequiredData()
            SelectableText(
                stringResource(if (missing) R.string.ys_missing_data else R.string.ys_pending),
                modifier = Modifier.testTag("ys-missing-data"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result != null) {
            HorizontalDivider()
            Text(stringResource(R.string.ys_result), style = MaterialTheme.typography.titleMedium)
            val factor = matchingNumber(context, result.factor, 6)
            val frequencyLengths = if (draft.mode == Mode.BOTH) before.map { it.length * result.factor } else null
            val rows = result.elements.mapIndexed { index, element ->
                val appliedLength = appliedLengths?.get(index)
                buildList {
                    add(scalingElementName(index))
                    add(scaleNumber(element.position))
                    add(appliedLength?.let(scaleNumber) ?: lengthNumber(element.length))
                    add(scaleNumber(element.diameter))
                    frequencyLengths?.get(index)?.let { frequencyLength ->
                        add(lengthNumber(frequencyLength))
                        add(signedNumber(element.length - frequencyLength))
                    }
                    add(signedNumber(element.length - before[index].length))
                    if (appliedLength != null) {
                        add(lengthNumber(element.length))
                        add(scaleNumber(appliedLength - element.length))
                    }
                }
            }
            val outputLengthLabel = if (appliedLengths == null) R.string.ys_length else R.string.opennec_applied_length
            val labelIds = listOf(R.string.ys_element, R.string.ys_position, outputLengthLabel, R.string.ys_d) +
                (if (frequencyLengths == null) listOf(R.string.ys_delta)
                else listOf(R.string.ys_frequency_length, R.string.ys_diameter_delta, R.string.ys_total_delta)) +
                if (appliedLengths == null) emptyList() else listOf(R.string.opennec_analytic_length, R.string.opennec_applied_delta)
            val labels = labelIds.map { stringResource(it) }
            val gapText = result.feedGap?.let { "${stringResource(R.string.ys_gap)} ${scaleNumber(it)} $unitLabel" }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (draft.mode != Mode.DIAMETER) Text(stringResource(R.string.ys_factor, factor), color = MaterialTheme.colorScheme.primary)
                }
            }
            if (diameterMode || appliedLengths != null) {
                Text(stringResource(R.string.ys_experimental_result), color = MaterialTheme.colorScheme.tertiary)
            }
            val current = selected.coerceIn(result.elements.indices)
            val working = result.elements.mapIndexed { index, element ->
                appliedLengths?.get(index)?.let { element.copy(length = it) } ?: element
            }
            ScalingDrawing(before, working, draft.enteredGap, result.feedGap, current) { selected = it }
            SelectionContainer {
                Column(Modifier.selectableGroup().testTag("ys-dimensions-table")) {
                    ScalingTableRow(listOf(stringResource(R.string.ys_element),
                        stringResource(R.string.ys_position_short, unitLabel),
                        stringResource(R.string.ys_length_short, unitLabel),
                        stringResource(R.string.ys_diameter_short, unitLabel)))
                    working.forEachIndexed { index, element ->
                        val active = current == index
                        ScalingTableRow(
                            listOf("${scalingElementName(index)} · ${scalingElementLabel(index)}",
                                resultNumber(element.position), resultNumber(element.length), diameterNumber(element.diameter)),
                            Modifier.fillMaxWidth().testTag("ys-diagram-selection-$index")
                                .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                .selectable(active, role = Role.RadioButton) { selected = index }
                                .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            val resultGap = result.feedGap
            if (resultGap != null && resultGap > 0.0) {
                val span = working[1].length
                HelpText(R.string.ys_driven_span, resultNumber(span), resultNumber(resultGap), unitLabel)
                if (draft.geometry == Geometry.FREE_ELEMENTS) {
                    HelpText(R.string.ys_driven_arm, resultNumber((span - resultGap) / 2.0), unitLabel)
                }
            }
            ScalingDetails(R.string.ys_all, Modifier.testTag("ys-comparison")) {
                before.indices.forEach { index ->
                    Text(scalingElementLabel(index), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        ScalingComparison(before[index], result.elements[index], frequencyLengths?.get(index),
                            resultNumber, diameterNumber, detailDelta, unitLabel, appliedLengths?.get(index))
                    }
                }
            }
            if (diameterMode) {
                HelpText(R.string.ys_estimate)
                WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_URL) }) { Text(stringResource(R.string.ys_article)) }
            }
            if (draft.geometry == Geometry.MOUNTED) HelpText(R.string.ys_similarity)
            val necReference = when (draft.mode) {
                Mode.FREQUENCY -> null
                Mode.DIAMETER -> before
                Mode.BOTH -> before.map { element ->
                    element.copy(position = element.position * result.factor, length = element.length * result.factor,
                        diameter = element.diameter * result.factor)
                }
            }
            if (appliedLengths != null) {
                Text(stringResource(R.string.opennec_applied, necVersion(appliedVersion)),
                    Modifier.testTag("opennec-applied"), color = MaterialTheme.colorScheme.primary)
                TextButton({ appliedLengths = null }) { Text(stringResource(R.string.opennec_reset_applied)) }
            }
            val blockedNec = when {
                draft.geometry == Geometry.MOUNTED -> R.string.opennec_mounted
                draft.split -> R.string.opennec_split
                !necAvailable() -> R.string.opennec_unavailable
                else -> null
            }
            YagiNecPanel(
                reference = necReference,
                elements = result.elements,
                targetHz = requireNotNull(draft.targetHz),
                blocked = blockedNec,
                appliedLengths = appliedLengths,
                combinedMode = draft.mode == Mode.BOTH,
                onSegmentation = onSegmentation,
            ) { found ->
                appliedLengths = ArrayList(found.map { it.length })
                appliedVersion = OpenNec.VERSION_CODE
            }
            if (onSave != null) Button({ onSave(draft.snapshot(result, appliedLengths, appliedVersion)) },
                modifier = Modifier.fillMaxWidth().testTag("ys-save")) { Text(stringResource(R.string.ys_save)) }
            OutlinedButton({
                val report = buildString {
                    appendLine(resources.getString(R.string.ys_title))
                    appendLine(resources.getString(R.string.ys_report_frequency, draft.source, (if (draft.mode == Mode.DIAMETER) draft.source else draft.target)))
                    appendLine(resources.getString(draft.mode.scalingLabel()))
                    appendLine(resources.getString(draft.geometry.scalingLabel()))
                    if (appliedLengths != null) appendLine(resources.getString(R.string.opennec_applied, necVersion(appliedVersion)))
                    appendLine(resources.getString(R.string.ys_factor, factor))
                    appendLine(resources.getString(R.string.ys_table_unit, unitLabel))
                    appendLine(labels.joinToString(" | "))
                    rows.forEach { appendLine(it.joinToString(" | ")) }
                    gapText?.let { appendLine(it) }
                    if (diameterMode || appliedLengths != null) appendLine(resources.getString(R.string.ys_experimental_result))
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
        ScalingDetails(R.string.ys_sources, Modifier.testTag("ys-method-sources")) {
            HelpText(R.string.ys_credits)
            WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_URL) }) { Text(stringResource(R.string.ys_article)) }
            WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_BOOK) }) { Text(stringResource(R.string.ys_book)) }
            HelpText(R.string.ys_rights)
            WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_TERMS) }) { Text(stringResource(R.string.ys_terms)) }
            NecMethodExamples()
        }
    }
}

@Composable
internal fun ScalingField(
    value: String, label: Int, tag: String, modifier: Modifier, unit: String = "",
    invalid: Boolean = false, errorText: Int = R.string.ys_positive_invalid, onChange: (String) -> Unit,
) {
    var touched by rememberSaveable { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    val showError = (value.isNotBlank() || touched) && (invalid || value.isNotBlank() && scalingDecimal(value) == null)
    AppTextField(
        value, { if (it.acceptsDecimalEdit()) onChange(it.boundedNumericText()) },
        modifier.testTag(tag).onFocusChanged {
            if (!it.isFocused && wasFocused) touched = true
            wasFocused = it.isFocused
        },
        label = stringResource(label) + if (unit.isEmpty()) "" else ", $unit",
        singleLine = true,
        isError = showError,
        supportingText = if (showError) ({ Text(stringResource(errorText)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
internal fun ScalingTableRow(cells: List<String>, modifier: Modifier = Modifier.fillMaxWidth()) {
    Row(modifier) {
        cells.forEachIndexed { index, value ->
            Text(value, Modifier.weight(if (index == 0) 1.4f else 1f).padding(end = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (index == cells.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
    HorizontalDivider()
}

@Composable
internal fun ScalingDetails(
    title: Int,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    PanelButton(expanded, { expanded = !expanded }, modifier) { Text(stringResource(title)) }
    if (expanded) content()
}

@Composable
private fun ScalingDrawing(
    before: List<Element>, after: List<Element>, beforeGap: Double?, afterGap: Double?,
    selected: Int, onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.ys_diagram)
    val measurer = rememberTextMeasurer()
    val markerStyle = MaterialTheme.typography.labelSmall
    val all = before + after
    val span = all.maxOf(Element::position).coerceAtLeast(0.000001)
    val length = all.maxOf(Element::length)
    val layers = listOf(before, after)
    fun elementSegment(element: Element, canvasSize: Size): Pair<Offset, Offset> {
        val scale = minOf(canvasSize.width * .82 / span, canvasSize.height * .82 / length)
        val x = (canvasSize.width / 2 + (element.position - span / 2) * scale).toFloat()
        val center = canvasSize.height / 2
        val half = (element.length * scale / 2).toFloat()
        return Offset(x, center - half) to Offset(x, center + half)
    }
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Canvas(Modifier.fillMaxWidth().height(154.dp).testTag("ys-result-diagram").semantics { contentDescription = description }
                .pointerInput(before, after) {
                    detectTapGestures { tap ->
                        val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                        fun distance(element: Element): Float {
                            val (top, bottom) = elementSegment(element, canvasSize)
                            val nearestPoint = Offset(top.x, tap.y.coerceIn(top.y, bottom.y))
                            return (tap - nearestPoint).getDistanceSquared()
                        }
                        val nearest = after.indices.minBy { minOf(distance(before[it]), distance(after[it])) }
                        onSelect(nearest)
                    }
                }) {
                val center = size.height / 2
                drawLine(colors.outline, Offset(0f, center), Offset(size.width, center), 1.dp.toPx())
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                layers.forEachIndexed { layer, elements ->
                    val original = layer == 0
                    elements.forEachIndexed { index, element ->
                        val (top, bottom) = elementSegment(element, size)
                        val color = if (original) colors.onSurfaceVariant else if (index == selected) colors.tertiary else colors.primary
                        val width = (if (original) 2 else 3).dp.toPx()
                        val path = if (original) dash else null
                        val gap = if (original) beforeGap else afterGap
                        if (index == 1 && gap != null && gap > 0.0) {
                            val halfGap = ((bottom.y - top.y) * gap / (2 * element.length)).toFloat()
                            drawLine(color, top, Offset(top.x, center - halfGap), width, pathEffect = path)
                            drawLine(color, Offset(top.x, center + halfGap), bottom, width, pathEffect = path)
                        } else {
                            drawLine(color, top, bottom, width, pathEffect = path)
                        }
                    }
                }
                val labelBottom = 16.dp.toPx()
                val occupied = mutableListOf<Pair<Float, Float>>()
                val order = listOf(selected) + after.indices.filter { it != selected }
                for (index in order) {
                    val (top, _) = elementSegment(after[index], size)
                    val text = measurer.measure(scalingElementName(index), markerStyle)
                    val from = (top.x - text.size.width / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                    val right = from + text.size.width + 4.dp.toPx()
                    if (occupied.any { (a, b) -> from < b && right > a }) continue
                    occupied += from to right
                    drawText(text, color = if (index == selected) colors.tertiary else colors.onSurfaceVariant,
                        topLeft = Offset(from, (labelBottom - text.size.height).coerceAtLeast(0f)))
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
private fun ScalingComparison(
    before: Element,
    after: Element,
    frequencyLength: Double?,
    lengthNumber: (Double) -> String,
    diameterNumber: (Double) -> String,
    signedNumber: (Double) -> String,
    unit: String,
    appliedLength: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ScalingTableRow(listOf(unit, stringResource(R.string.ys_before), stringResource(R.string.ys_after)))
        ScalingTableRow(listOf(stringResource(R.string.ys_length), lengthNumber(before.length), lengthNumber(after.length)))
        ScalingTableRow(listOf(stringResource(R.string.ys_position), lengthNumber(before.position), lengthNumber(after.position)))
        ScalingTableRow(listOf(stringResource(R.string.ys_d), diameterNumber(before.diameter), diameterNumber(after.diameter)))
        if (frequencyLength != null) {
            ScalingTableRow(listOf(stringResource(R.string.ys_frequency_length), "—", lengthNumber(frequencyLength)))
            ScalingTableRow(listOf(stringResource(R.string.ys_diameter_delta), "—", signedNumber(after.length - frequencyLength)))
        }
        ScalingTableRow(listOf(stringResource(R.string.ys_total_delta), "—", signedNumber(after.length - before.length)))
        if (appliedLength != null) {
            ScalingTableRow(listOf(stringResource(R.string.opennec_applied_length), "—", lengthNumber(appliedLength)))
            ScalingTableRow(listOf(stringResource(R.string.opennec_applied_delta), "—", signedNumber(appliedLength - after.length)))
        }
    }
}
