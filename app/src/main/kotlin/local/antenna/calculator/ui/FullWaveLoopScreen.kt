package local.antenna.calculator.ui

import android.content.ClipData
import android.graphics.Paint
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.InputError
import local.antenna.calculator.core.layout.*
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.state.*

@Composable
internal fun FullWaveLoopScreen(
    state: CalculatorUiState,
    currentState: () -> CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    onSave: (() -> Unit)?,
    onCopied: () -> Unit,
) {
    val draft = state.draft.fullWaveLoop
    val layout = remember(draft) { draft.layout() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; isGroupingUsed = false }
    }
    val frequencyNumbers = remember(locale) { FrequencyNumberFormat(locale) }
    var matching by rememberSaveable { mutableStateOf(false) }
    var formulas by rememberSaveable { mutableStateOf(false) }
    val square = draft.shape == LoopShape.SQUARE
    fun update(change: (FullWaveLoopDraft) -> FullWaveLoopDraft) {
        onEvent(CalculatorEvent.FullWaveLoop(change(currentState().draft.fullWaveLoop)))
    }
    fun length(meters: Double, unit: LengthUnit = draft.outputUnit): String =
        resources.getString(R.string.loop_frequency_value,
            number.format(meters * 1e9 / unit.nanometersPerUnit), resources.getString(unit.labelResource()))
    fun frequency(mhz: Double): String {
        val (value, unit) = when {
            mhz < 1 -> mhz * 1000 to FrequencyUnit.KILOHERTZ
            mhz >= 1000 -> mhz / 1000 to FrequencyUnit.GIGAHERTZ
            else -> mhz to FrequencyUnit.MEGAHERTZ
        }
        return resources.getString(R.string.loop_frequency_value,
            frequencyNumbers.inUnit(value, unit), resources.getString(unit.labelResource()))
    }

    val scroll = rememberScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(scroll).padding(20.dp)
            .testTag("full-wave-loop-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StorageNotice(state, onEvent)
        Image(
            painterResource(if (square) R.drawable.full_wave_loop_square else R.drawable.full_wave_loop_triangle),
            stringResource(if (square) R.string.loop_square_image else R.string.loop_triangle_image),
            Modifier.fillMaxWidth().height(180.dp).testTag(if (square) "loop-square-image" else "loop-triangle-image"),
        )
        HelpText(R.string.loop_intro)
        ChoicePicker(stringResource(R.string.loop_shape), LoopShape.entries, draft.shape,
            { stringResource(it.loopLabel()) }, { shape -> update { it.chooseShape(shape) } }, "loop-shape")
        LoopHeading(R.string.input_data)
        ChoicePicker(stringResource(R.string.loop_mode), LoopMode.entries, draft.mode,
            { stringResource(it.loopLabel()) }, { mode -> update { it.copy(mode = mode) } }, "loop-mode")
        LoopInput(draft, draft.activeField, state.editingEnabled, geometryInvalid = layout == null) { field, raw ->
            update { it.edit(field, raw) }
        }
        if (draft.mode == LoopMode.PERIMETER) {
            ChoicePicker(stringResource(R.string.loop_input_unit), LengthUnit.entries, draft.inputUnit,
                { stringResource(it.labelResource()) }, { unit -> update { it.changeInputUnit(unit) } }, "loop-input-unit")
        }
        ChoicePicker(stringResource(R.string.loop_model), LoopModel.entries, draft.model,
            { stringResource(it.loopLabel()) }, { model -> update { it.copy(model = model) } }, "loop-model")
        if (draft.model == LoopModel.CUSTOM) {
            LoopInput(draft, LoopField.FACTOR, state.editingEnabled) { field, raw -> update { it.edit(field, raw) } }
        }
        HelpText(draft.model.hint())
        if (layout != null) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(14.dp)) {
                    val byFrequency = draft.mode == LoopMode.FREQUENCY
                    LoopValue(
                        if (byFrequency) R.string.loop_calculated_perimeter else R.string.loop_calculated_frequency,
                        if (byFrequency) length(layout.perimeterMeters) else frequency(layout.frequencyMhz),
                        Modifier.testTag("loop-input-result"),
                    )
                }
            }
            if (draft.mode == LoopMode.PERIMETER) HelpText(R.string.loop_frequency_hint)
        }

        LoopHeading(R.string.inverted_v_geometry)
        val shapeName = stringResource(draft.shape.loopLabel())
        val feedName = stringResource(draft.feed.loopLabel())
        LoopDiagram(
            draft.shape, draft.feed,
            if (layout == null) "$shapeName. $feedName" else stringResource(
                R.string.loop_diagram_description, shapeName, length(layout.widthMeters), length(layout.heightMeters), feedName),
        ) { feed -> update { it.copy(feed = feed) } }
        ChoicePicker(stringResource(R.string.loop_feed), LoopFeed.entries.filter { it.fits(draft.shape) }, draft.feed,
            { stringResource(it.loopLabel()) }, { feed -> update { it.copy(feed = feed) } }, "loop-feed")
        when (draft.feed) {
            LoopFeed.BASE, LoopFeed.APEX -> HelpText(R.string.loop_horizontal)
            LoopFeed.SIDE_MIDDLE, LoopFeed.QUARTER_FROM_APEX -> HelpText(R.string.loop_vertical)
            LoopFeed.CORNER -> Unit
        }
        if (draft.feed != LoopFeed.BASE) HelpText(R.string.loop_illustrated_feed)

        LoopHeading(R.string.loop_dimensions)
        ChoicePicker(stringResource(R.string.loop_output_unit), LengthUnit.entries, draft.outputUnit,
            { stringResource(it.labelResource()) }, { unit -> update { it.copy(outputUnit = unit) } }, "loop-output-unit")
        if (layout == null) HelpText(R.string.inverted_v_pending) else {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    layout.rows().forEach { (label, meters) -> LoopValue(label, length(meters)) }
                }
            }
        }
        if (onSave != null) Button(onSave, enabled = state.editingEnabled && layout != null,
            modifier = Modifier.fillMaxWidth().testTag("loop-save")) { Text(stringResource(R.string.inverted_v_save)) }
        OutlinedButton(
            onClick = {
                val latest = currentState().draft.fullWaveLoop
                val result = latest.layout() ?: return@OutlinedButton
                fun line(label: Int, value: String) = resources.getString(R.string.journal_property, resources.getString(label), value)
                val report = buildString {
                    appendLine(resources.getString(R.string.loop_title))
                    appendLine(line(R.string.loop_shape, resources.getString(latest.shape.loopLabel())))
                    appendLine(resources.getString(latest.mode.loopLabel()))
                    appendLine(line(R.string.loop_model, resources.getString(latest.model.loopLabel())))
                    if (latest.model == LoopModel.CUSTOM) appendLine(line(R.string.loop_factor, latest.fields.getValue(LoopField.FACTOR)))
                    appendLine(line(R.string.loop_calculated_frequency, frequency(result.frequencyMhz)))
                    result.rows().forEach { (label, meters) -> appendLine(line(label, length(meters, latest.outputUnit))) }
                    appendLine(line(R.string.loop_feed, resources.getString(latest.feed.loopLabel())))
                    append(resources.getString(R.string.loop_limits))
                }
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                clipboard.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.loop_title), report))
                onCopied()
            },
            enabled = layout != null,
            modifier = Modifier.fillMaxWidth().testTag("loop-copy"),
        ) { Text(stringResource(R.string.copy_result)) }
        HelpText(R.string.loop_limits)

        TextButton(onClick = { matching = !matching }, modifier = Modifier.testTag("loop-match-toggle")) {
            Text(stringResource(R.string.loop_matching), color = MaterialTheme.colorScheme.secondary)
        }
        if (matching) {
            HelpText(R.string.loop_matching_hint)
            for (field in listOf(LoopField.MATCH_FREQUENCY, LoopField.MATCH_LOAD, LoopField.MATCH_LINE, LoopField.MATCH_VF)) {
                LoopInput(draft, field, state.editingEnabled) { key, raw -> update { it.edit(key, raw) } }
            }
            draft.matching()?.let { section ->
                LoopValue(R.string.loop_impedance, stringResource(R.string.loop_impedance_value, number.format(section.impedanceOhms)),
                    Modifier.testTag("loop-match-impedance"))
                LoopValue(R.string.loop_section_length, length(section.lengthMeters), Modifier.testTag("loop-match-length"))
            }
        }
        FullWaveLoopBandTable()
        TextButton(onClick = { formulas = !formulas }) {
            Text(stringResource(R.string.loop_formulas), color = MaterialTheme.colorScheme.secondary)
        }
        if (formulas) {
            FormulaPlate(R.string.loop_formulas, R.drawable.formula_full_wave_loop,
                R.string.loop_formula_alt, "loop-formula")
        }
    }
}

@Composable
private fun LoopHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun LoopValue(label: Int, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LoopInput(
    draft: FullWaveLoopDraft, field: LoopField, enabled: Boolean, geometryInvalid: Boolean = false,
    onEdit: (LoopField, String) -> Unit,
) {
    val raw = draft.fields.getValue(field)
    val issue = when (val parsed = draft.number(field)) {
        is FieldState.Valid -> R.string.loop_perimeter_error.takeIf {
            geometryInvalid && field == LoopField.FREQUENCY &&
                (draft.model != LoopModel.CUSTOM || draft.number(LoopField.FACTOR).validValue() != null)
        }
        FieldState.Empty -> R.string.error_empty.takeIf { field.ordinal < LoopField.MATCH_FREQUENCY.ordinal }
        FieldState.Incomplete -> R.string.error_incomplete
        is FieldState.Invalid -> when (parsed.error) {
            InputError.TOO_LONG -> R.string.error_too_long
            InputError.INVALID_NUMBER -> R.string.error_number
            InputError.NON_POSITIVE -> R.string.error_positive
            else -> when (field) {
                LoopField.FREQUENCY, LoopField.MATCH_FREQUENCY -> R.string.error_frequency_step
                LoopField.FACTOR -> R.string.loop_factor_error
                LoopField.MATCH_VF -> R.string.loop_vf_error
                else -> R.string.loop_perimeter_error
            }
        }
    }?.let { stringResource(it) }
    val (fieldModifier, frequencyTransformation) = megahertzFieldPresentation(
        Modifier, field == LoopField.FREQUENCY || field == LoopField.MATCH_FREQUENCY,
    )
    OutlinedTextField(
        visualTransformation = frequencyTransformation,
        value = raw, onValueChange = { onEdit(field, it) }, enabled = enabled, singleLine = true,
        label = { Text(stringResource(field.loopLabel())) },
        supportingText = issue?.let { { Text(it) } },
        isError = issue != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = fieldModifier.fillMaxWidth().testTag("loop-${field.name}").semantics { if (issue != null) error(issue) },
    )
}

internal fun LoopShape.loopLabel() = if (this == LoopShape.SQUARE) R.string.loop_square else R.string.loop_triangle
internal fun LoopMode.loopLabel() = if (this == LoopMode.FREQUENCY) R.string.loop_by_frequency else R.string.loop_by_perimeter
internal fun LoopModel.loopLabel() = when (this) {
    LoopModel.IDEAL -> R.string.loop_ideal
    LoopModel.EMPIRICAL -> R.string.loop_empirical
    LoopModel.CUSTOM -> R.string.loop_custom
}
private fun LoopModel.hint() = when (this) {
    LoopModel.IDEAL -> R.string.loop_ideal_hint
    LoopModel.EMPIRICAL -> R.string.loop_empirical_hint
    LoopModel.CUSTOM -> R.string.loop_custom_hint
}
internal fun LoopFeed.loopLabel() = when (this) {
    LoopFeed.BASE -> R.string.loop_base
    LoopFeed.CORNER -> R.string.loop_corner
    LoopFeed.SIDE_MIDDLE -> R.string.loop_side_middle
    LoopFeed.APEX -> R.string.loop_apex
    LoopFeed.QUARTER_FROM_APEX -> R.string.loop_quarter_apex
}
private fun LoopField.loopLabel() = when (this) {
    LoopField.FREQUENCY, LoopField.MATCH_FREQUENCY -> R.string.loop_frequency
    LoopField.PERIMETER -> R.string.loop_perimeter
    LoopField.FACTOR -> R.string.loop_factor
    LoopField.MATCH_LOAD -> R.string.loop_load
    LoopField.MATCH_LINE -> R.string.loop_line
    LoopField.MATCH_VF -> R.string.loop_vf
}
private fun LoopLayout.rows() = listOf(R.string.loop_perimeter to perimeterMeters, R.string.loop_side to sideMeters,
    R.string.loop_width to widthMeters, R.string.loop_height to heightMeters)

private data class LoopDrawing(val vertices: List<Offset>, val feed: Map<LoopFeed, Offset>)

private fun loopDrawing(shape: LoopShape, size: Size): LoopDrawing {
    val side = minOf(size.width * .56f, size.height * .66f)
    val center = size.width / 2
    val bottom = size.height * .78f
    val left = Offset(center - side / 2, bottom)
    val right = Offset(center + side / 2, bottom)
    val apex = Offset(center, bottom - side * .8660254f)
    val topLeft = left.copy(y = bottom - side)
    val vertices = if (shape == LoopShape.SQUARE) listOf(left, topLeft, right.copy(y = topLeft.y), right)
        else listOf(left, apex, right)
    val feed = buildMap {
        put(LoopFeed.BASE, Offset(center, bottom))
        put(LoopFeed.CORNER, left)
        if (shape == LoopShape.SQUARE) put(LoopFeed.SIDE_MIDDLE, (left + topLeft) / 2f)
        else {
            put(LoopFeed.APEX, apex)
            put(LoopFeed.QUARTER_FROM_APEX, apex + (left - apex) * .75f)
        }
    }
    return LoopDrawing(vertices, feed)
}

@Composable
private fun LoopDiagram(shape: LoopShape, feed: LoopFeed, description: String, onFeed: (LoopFeed) -> Unit) {
    val color = MaterialTheme.colorScheme
    val colorForLabel = color.secondary
    val marker = if (color.surface.luminance() < .5f) Color(0xFFF6C487) else Color(0xFF8D4A12)
    val choose by rememberUpdatedState(onFeed)
    Canvas(Modifier.fillMaxWidth().height(168.dp).testTag("loop-diagram").semantics { contentDescription = description }
        .pointerInput(shape) {
            detectTapGestures { point ->
                val drawing = loopDrawing(shape, Size(size.width.toFloat(), size.height.toFloat()))
                val nearest = drawing.feed.minBy { (_, target) -> (point - target).getDistance() }
                if ((point - nearest.value).getDistance() <= 24.dp.toPx()) choose(nearest.key)
            }
        }) {
        val drawing = loopDrawing(shape, size)
        val corners = drawing.vertices
        corners.forEachIndexed { index, start ->
            drawLine(color.primary, start, corners[(index + 1) % corners.size], 2.dp.toPx())
        }
        val left = corners.first().x
        val right = corners.last().x
        val top = corners.minOf { it.y }
        val bottom = corners.first().y
        val widthY = bottom + 15.dp.toPx()
        val heightX = right + 16.dp.toPx()
        val cap = 3.dp.toPx()
        drawLine(color.outline, Offset(left, widthY), Offset(right, widthY))
        drawLine(color.outline, Offset(heightX, top), Offset(heightX, bottom))
        for (x in listOf(left, right)) drawLine(color.outline, Offset(x, widthY - cap), Offset(x, widthY + cap))
        for (y in listOf(top, bottom)) drawLine(color.outline, Offset(heightX - cap, y), Offset(heightX + cap, y))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = colorForLabel.toArgb()
            textSize = 13.dp.toPx()
            textAlign = Paint.Align.CENTER
        }
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawText("W", (left + right) / 2, widthY + 15.dp.toPx(), paint)
        canvas.drawText("H", heightX + 10.dp.toPx(), (top + bottom) / 2, paint)
        canvas.drawText("a", left + if (shape == LoopShape.SQUARE) -12.dp.toPx() else 5.dp.toPx(), (top + bottom) / 2, paint)
        drawing.feed.forEach { (key, point) ->
            drawCircle(marker, (if (key == feed) 5 else 3).dp.toPx(), point)
            if (key != feed) drawCircle(color.surface, 2.dp.toPx(), point)
            else {
                paint.color = marker.toArgb()
                canvas.drawText("F", point.x + 11.dp.toPx(), point.y - 8.dp.toPx(), paint)
            }
        }
    }
}
