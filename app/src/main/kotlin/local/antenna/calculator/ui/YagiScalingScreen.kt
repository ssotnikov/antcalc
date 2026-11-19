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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
    val geometryIncomplete = draft.rows.any { it.position.isBlank() || it.length.isBlank() }
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
    val source = stringResource(if (draft.mode == Mode.FREQUENCY) R.string.ys_frequency_source else R.string.ys_formula_source)

    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberPageScrollState())
        .padding(20.dp).testTag("yagi-scaling"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (initial != null && loaded == null) {
            HelpText(R.string.ys_invalid_saved)
            return@Column
        }
        ScalingIllustration(draft)
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
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(when (draft.mode) {
                        Mode.FREQUENCY -> R.string.ys_effect_frequency
                        Mode.DIAMETER -> R.string.ys_effect_diameter
                        Mode.BOTH -> R.string.ys_effect_both
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result == null) HelpText(
                    if (draft.geometry == Geometry.MOUNTED && diameterMode) R.string.ys_mounted_error
                    else R.string.ys_pending,
                )
            }
        }
        Text(stringResource(R.string.ys_source_parameters), style = MaterialTheme.typography.titleMedium)
        ScalingField(draft.source, if (draft.mode == Mode.DIAMETER) R.string.ys_working_hz else R.string.ys_source_hz,
            "ys-source", Modifier.fillMaxWidth(), invalid = draft.source.isNotBlank() && draft.sourceHz == null) {
            draft = draft.copy(source = it)
        }
        ChoicePicker(stringResource(R.string.ys_unit), LengthUnit.entries, draft.unit,
            { stringResource(it.labelResource()) }, { draft = draft.inUnit(it) }, "ys-unit")
        ScalingField(draft.diameter, R.string.ys_common_d, "ys-diameter", Modifier.fillMaxWidth(), unitLabel) {
            draft = draft.copy(diameter = it)
        }
        HelpText(R.string.ys_default_d_help)
        Text(stringResource(R.string.ys_target_parameters), style = MaterialTheme.typography.titleMedium)
        if (draft.mode != Mode.DIAMETER) ScalingField(draft.target, R.string.ys_target_hz, "ys-target",
            Modifier.fillMaxWidth(), invalid = draft.target.isNotBlank() && draft.targetHz == null) {
            draft = draft.copy(target = it)
        }
        if (diameterMode) ScalingField(draft.targetDiameter, R.string.ys_common_target_d, "ys-target-diameter",
            Modifier.fillMaxWidth(), unitLabel) { draft = draft.copy(targetDiameter = it) }
        Text(stringResource(R.string.ys_elements, draft.rows.size), style = MaterialTheme.typography.titleMedium)
        if (geometryIncomplete) HelpText(R.string.ys_geometry_required)
        ChoicePicker(stringResource(R.string.ys_geometry), Geometry.entries, draft.geometry,
            { stringResource(it.scalingLabel()) }, { draft = draft.copy(geometry = it) }, "ys-geometry")
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
                    if (index == 0) {
                        Text(
                            stringResource(R.string.ys_reflector_origin, unitLabel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ScalingField(row.length, R.string.ys_length, "ys-length-$index",
                            Modifier.fillMaxWidth(), unitLabel) { edit(row.copy(length = it)) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScalingField(row.position, R.string.ys_position, "ys-position-$index",
                                Modifier.weight(1f), unitLabel) { edit(row.copy(position = it)) }
                            ScalingField(row.length, R.string.ys_length, "ys-length-$index",
                                Modifier.weight(1f), unitLabel) { edit(row.copy(length = it)) }
                        }
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
        if (result == null && draft.hasMissingRequiredData()) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("ys-missing-data"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    stringResource(R.string.ys_missing_data),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        TextButton({ draft = YagiScalingDraft.example().copy(mode = draft.mode) }, Modifier.testTag("ys-example")) {
            Text(stringResource(R.string.ys_example))
        }
        if (draft.mode == Mode.FREQUENCY) {
            TextButton({ draft = YagiScalingDraft.r3udkExample(draft.unit) }, Modifier.testTag("ys-example-r3udk")) {
                Text(stringResource(R.string.ys_example_r3udk))
            }
            HelpText(R.string.ys_example_r3udk_scope)
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
                    gapText?.let { Text(it) }
                }
            }
            val current = selected.coerceIn(result.elements.indices)
            val working = result.elements.mapIndexed { index, element ->
                appliedLengths?.get(index)?.let { element.copy(length = it) } ?: element
            }
            ScalingDrawing(before, working, current) { selected = it }
            ChoicePicker(stringResource(R.string.ys_element), result.elements.indices.toList(), current,
                { scalingElementLabel(it) }, { selected = it }, "ys-diagram-selection")
            SelectionContainer {
                ScalingComparison(before[current], result.elements[current], frequencyLengths?.get(current),
                    scaleNumber, lengthNumber, signedNumber, unitLabel, appliedLengths?.get(current))
            }
            ScalingDetails(R.string.ys_all) {
                before.indices.forEach { index ->
                    Text(scalingElementLabel(index), style = MaterialTheme.typography.titleSmall)
                    ScalingComparison(before[index], result.elements[index], frequencyLengths?.get(index),
                        scaleNumber, lengthNumber, signedNumber, unitLabel, appliedLengths?.get(index))
                }
            }
            SelectableText(source)
            if (diameterMode) HelpText(R.string.ys_estimate)
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
            YagiNecPanel(necReference, result.elements, requireNotNull(draft.targetHz), blockedNec, appliedLengths, onSegmentation = onSegmentation) { found ->
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
            WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_URL) }) { Text(stringResource(R.string.ys_article)) }
            WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_BOOK) }) { Text(stringResource(R.string.ys_book)) }
            HelpText(R.string.ys_rights)
            WebLinkButton({ openSourceWithFeedback(context, YAGI_SCALING_TERMS) }) { Text(stringResource(R.string.ys_terms)) }
        }
    }
}

@Composable
internal fun ScalingField(
    value: String, label: Int, tag: String, modifier: Modifier, unit: String = "",
    invalid: Boolean = false, onChange: (String) -> Unit,
) {
    AppTextField(
        value, { if (it.acceptsDecimalEdit()) onChange(it.boundedNumericText()) },
        modifier.testTag(tag),
        label = stringResource(label) + if (unit.isEmpty()) "" else ", $unit",
        singleLine = true,
        isError = invalid || value.isNotBlank() && scalingDecimal(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
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
    PanelButton(expanded, { expanded = !expanded }) { Text(stringResource(title)) }
    if (expanded) content()
}

@Composable
private fun ScalingIllustration(draft: YagiScalingDraft) {
    val sourceHz = draft.sourceHz
    val targetHz = draft.targetHz
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.ys_illustration)
    val targetScale = when {
        draft.mode == Mode.DIAMETER -> 1f
        sourceHz == null || targetHz == null -> .78f
        else -> (sourceHz.toDouble() / targetHz).toFloat().coerceIn(.55f, 1.38f)
    }
    val originalScale = 1f / maxOf(1f, targetScale)
    val originalDiameter = scalingDecimal(draft.diameter)?.takeIf { it > 0 }
    val requestedDiameter = scalingDecimal(draft.targetDiameter)?.takeIf { it > 0 }
    val tubeScale = when {
        draft.mode == Mode.FREQUENCY -> targetScale
        originalDiameter != null && requestedDiameter != null -> (requestedDiameter / originalDiameter).toFloat()
        else -> 1f
    }.coerceIn(.6f, 1.65f)

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(104.dp).semantics { contentDescription = description }) {
            val radius = minOf(size.width * .15f, size.height * .42f)
            val centerY = size.height / 2
            fun antenna(centerX: Float, multiplier: Float, original: Boolean) {
                val color = if (original) colors.onSurfaceVariant else colors.primary
                val center = Offset(centerX, centerY)
                val r = radius * multiplier
                val boom = Offset(r * .91f, -r * .31f)
                val element = Offset(r * .44f, r * .84f)
                val tube = (2.5f * if (original) 1f else tubeScale).dp.toPx()
                drawLine(colors.outline, center - boom, center + boom, 4.dp.toPx(), StrokeCap.Round)
                listOf(1f, .88f, .77f, .69f).forEachIndexed { i, length ->
                    val mount = center + boom * (-.82f + i * .55f)
                    val conductor = element * length
                    drawLine(color, mount - conductor, mount + conductor, tube, StrokeCap.Round)
                    drawCircle(colors.surfaceContainer, 3.dp.toPx(), mount)
                    drawCircle(color, 1.35.dp.toPx(), mount)
                }
            }
            antenna(size.width * .23f, originalScale, original = true)
            antenna(size.width * .77f, originalScale * targetScale, original = false)
            val arrow = 11.dp.toPx()
            val tip = Offset(size.width / 2 + arrow, centerY)
            drawLine(colors.outline, Offset(tip.x - 2 * arrow, tip.y), tip, 2.dp.toPx(), StrokeCap.Round)
            drawLine(colors.outline, Offset(tip.x - arrow / 2, tip.y - arrow / 2), tip, 2.dp.toPx(), StrokeCap.Round)
            drawLine(colors.outline, Offset(tip.x - arrow / 2, tip.y + arrow / 2), tip, 2.dp.toPx(), StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Text(stringResource(R.string.ys_before), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(stringResource(R.string.ys_after), style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
    }
}

@Composable
private fun ScalingDrawing(before: List<Element>, after: List<Element>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.ys_diagram)
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
                        drawLine(color, top, bottom,
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
private fun ScalingComparison(
    before: Element,
    after: Element,
    frequencyLength: Double?,
    number: (Double) -> String,
    lengthNumber: (Double) -> String,
    signedNumber: (Double) -> String,
    unit: String,
    appliedLength: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ScalingTableRow(listOf(unit, stringResource(R.string.ys_before), stringResource(R.string.ys_after)))
        ScalingTableRow(listOf(stringResource(R.string.ys_length), number(before.length), lengthNumber(after.length)))
        ScalingTableRow(listOf(stringResource(R.string.ys_position), number(before.position), number(after.position)))
        ScalingTableRow(listOf(stringResource(R.string.ys_d), number(before.diameter), number(after.diameter)))
        if (frequencyLength != null) {
            ScalingTableRow(listOf(stringResource(R.string.ys_frequency_length), "—", lengthNumber(frequencyLength)))
            ScalingTableRow(listOf(stringResource(R.string.ys_diameter_delta), "—", signedNumber(after.length - frequencyLength)))
            ScalingTableRow(listOf(stringResource(R.string.ys_total_delta), "—", signedNumber(after.length - before.length)))
        }
        if (appliedLength != null) {
            ScalingTableRow(listOf(stringResource(R.string.opennec_applied_length), "—", number(appliedLength)))
            ScalingTableRow(listOf(stringResource(R.string.opennec_applied_delta), "—", number(appliedLength - after.length)))
        }
    }
}
