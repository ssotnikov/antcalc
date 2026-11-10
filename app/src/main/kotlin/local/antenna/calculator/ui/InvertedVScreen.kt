package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.R
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.InputError
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.layout.InvertedVResult
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.journal.invertedVSnapshot
import local.antenna.calculator.journal.label
import local.antenna.calculator.state.*
import local.antenna.calculator.state.InvertedVField.*

@Composable
internal fun InvertedVScreen(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    currentState: () -> CalculatorUiState,
    journal: JournalViewModel?,
    notify: (String) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val draft = state.draft.invertedV
    val result = draft.result()
    val title = stringResource(R.string.inverted_v_title)
    val copied = stringResource(R.string.copied)
    val russian = state.draft.settings.language == AppLanguage.RUSSIAN
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showProjects by rememberSaveable { mutableStateOf(false) }
    fun edit(update: (InvertedVDraft) -> InvertedVDraft) =
        onEvent(CalculatorEvent.InvertedV(update(currentState().draft.invertedV)))

    Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(20.dp).testTag("inverted-v-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StorageNotice(state, onEvent)
        HelpText(R.string.inverted_v_intro)
        InvertedVHeading(R.string.inverted_v_inputs)
        ChoicePicker(
            stringResource(R.string.inverted_v_mode), InvertedVMode.entries, draft.mode,
            { stringResource(it.label()) }, { focus.clearFocus(); edit { d -> d.copy(mode = it) } },
            "inverted-v-mode",
        )
        if (draft.mode == InvertedVMode.MEASURED) {
            InvertedVInput(draft, ARM) { edit { d -> d.edit(ARM, it) } }
        } else {
            InvertedVInput(draft, FREQUENCY) { edit { d -> d.edit(FREQUENCY, it) } }
            ChoicePicker(
                stringResource(R.string.frequency_units), FrequencyUnit.entries, draft.frequencyUnit,
                { stringResource(it.labelResource()) }, { edit { d -> d.changeFrequencyUnit(it) } },
                "inverted-v-frequency-unit",
            )
            InvertedVInput(draft, FACTOR) { edit { d -> d.edit(FACTOR, it) } }
            HelpText(R.string.inverted_v_estimate_help)
        }
        InvertedVInput(draft, HEIGHT) { edit { d -> d.edit(HEIGHT, it) } }
        InvertedVInput(draft, ANGLE) { edit { d -> d.edit(ANGLE, it) } }
        HelpText(R.string.inverted_v_angle_help)
        ChoicePicker(
            stringResource(R.string.length_units), LengthUnit.entries, draft.lengthUnit,
            { stringResource(it.labelResource()) }, { edit { d -> d.changeLengthUnit(it) } },
            "inverted-v-length-unit",
        )

        InvertedVHeading(R.string.inverted_v_geometry)
        if (result == null) {
            HelpText(R.string.inverted_v_pending)
        } else {
            val rows = result.rows(context, draft.lengthUnit)
            InvertedVDiagram(result, draft.lengthUnit, context)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rows.forEach { (label, value) ->
                        Column(Modifier.testTag("inverted-v-result-${label}").semantics(mergeDescendants = true) {}) {
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                value, style = MaterialTheme.typography.titleLarge,
                                color = if (label == R.string.inverted_v_end_height && result.endHeightMeters < 0)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            result.notice()?.let {
                val color = if (result.endHeightMeters < 0) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
                val ink = if (result.endHeightMeters < 0) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                Surface(color = color, contentColor = ink, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(it), Modifier.fillMaxWidth().padding(12.dp)
                        .testTag("inverted-v-ground-notice").semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        if (journal != null) {
            Button(
                onClick = { focus.clearFocus(); showProjects = true },
                enabled = result != null && result.endHeightMeters >= 0,
                modifier = Modifier.fillMaxWidth().testTag("inverted-v-save"),
            ) { Text(stringResource(R.string.inverted_v_save)) }
            if (showProjects) {
                InvertedVProjectPicker(journal, currentState, { showProjects = false }, notify)
            }
        }
        OutlinedButton(
            onClick = {
                focus.clearFocus()
                val copy = currentState().draft.invertedV.copyText(context) ?: return@OutlinedButton
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText(title, copy))
                if (clipboard != null) notify(copied)
            },
            enabled = result != null,
            modifier = Modifier.fillMaxWidth().testTag("inverted-v-copy"),
        ) { Text(stringResource(R.string.copy_result)) }
        HelpText(R.string.inverted_v_limits)
        TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.testTag("inverted-v-details")) {
            Text(stringResource(if (showDetails) R.string.matching_less else R.string.inverted_v_details))
        }
        if (showDetails) {
            FormulaPlate(R.string.inverted_v_span, R.drawable.formula_inverted_v_span,
                R.string.inverted_v_span_alt, "inverted-v-span-formula")
            FormulaPlate(R.string.inverted_v_end_height, R.drawable.formula_inverted_v_height,
                R.string.inverted_v_height_alt, "inverted-v-height-formula")
            HelpText(R.string.inverted_v_details_text)
        }
        val source = if (russian) R.string.source_inverted_v_ru_title else R.string.source_inverted_v_en_title
        val address = stringResource(if (russian) R.string.source_inverted_v_ru_url else R.string.source_inverted_v_en_url)
        TextButton(
            onClick = { openSourceWithFeedback(context, address) },
            modifier = Modifier.testTag("inverted-v-source"),
        ) { Text(stringResource(source)) }
    }
}

@Composable
private fun InvertedVHeading(label: Int) {
    Text(
        stringResource(label), style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary, modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun InvertedVInput(draft: InvertedVDraft, field: InvertedVField, onEdit: (String) -> Unit) {
    val status = draft.number(field)
    val message = when (status) {
        is FieldState.Valid -> null
        FieldState.Empty -> R.string.error_empty
        FieldState.Incomplete -> R.string.error_incomplete
        is FieldState.Invalid -> when (status.error) {
            InputError.TOO_LONG -> R.string.error_too_long
            InputError.INVALID_NUMBER -> R.string.error_number
            InputError.NON_POSITIVE -> R.string.error_positive
            InputError.OUT_OF_RANGE -> when (field) {
                ARM -> R.string.error_length_range
                FREQUENCY -> R.string.error_frequency_range
                FACTOR -> R.string.error_factor_range
                HEIGHT -> R.string.inverted_v_height_range
                ANGLE -> R.string.inverted_v_angle_range
            }
            InputError.TOO_PRECISE -> when (field) {
                ARM, HEIGHT -> R.string.error_length_step
                FREQUENCY -> R.string.error_frequency_step
                FACTOR -> R.string.error_factor_step
                ANGLE -> R.string.inverted_v_angle_step
            }
        }
    }?.let { stringResource(it) }
    val suffix = when (field) {
        ARM, HEIGHT -> stringResource(draft.lengthUnit.labelResource())
        FREQUENCY -> stringResource(draft.frequencyUnit.labelResource())
        ANGLE -> "°"
        FACTOR -> null
    }
    val label = stringResource(field.label())
    val inputLabel = if (suffix == null) label else stringResource(R.string.inverted_v_input_unit, label, suffix)
    OutlinedTextField(
        value = draft.fields.getValue(field), onValueChange = onEdit,
        label = { Text(inputLabel) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = message != null,
        supportingText = message?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        modifier = Modifier.fillMaxWidth().testTag("inverted-v-${field.name}").semantics {
            if (message != null) error(message)
        },
    )
}

@Composable
private fun InvertedVProjectPicker(
    journal: JournalViewModel,
    currentState: () -> CalculatorUiState,
    close: () -> Unit,
    notify: (String) -> Unit,
) {
    val title = stringResource(R.string.inverted_v_title)
    val savedMessage = stringResource(R.string.inverted_v_saved)
    val status by journal.state.collectAsState()
    val projects = status.projects.filter { it.antennaType == AntennaType.SYMMETRIC_DIPOLE }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.inverted_v_save)) },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                val problem = status.problem
                if (problem != null) HelpText(problem.label())
                if (status.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (status.readable && projects.isEmpty()) {
                    HelpText(R.string.inverted_v_no_projects)
                }
                if (status.readable) projects.forEach { project ->
                    TextButton(
                        enabled = !status.busy,
                        onClick = {
                            val snapshot = currentState().draft.invertedV.invertedVSnapshot() ?: return@TextButton
                            journal.addRecord(project.id, JournalRecord(
                                id = UUID.randomUUID(),
                                title = title,
                                time = OffsetDateTime.now(),
                                antennaType = project.antennaType,
                                installation = project.installation,
                                feedline = project.feedline,
                                calculation = snapshot,
                            )) {
                                close()
                                notify(savedMessage)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("inverted-v-project"),
                    ) { Text(project.title) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = close) { Text(stringResource(R.string.journal_cancel)) }
        },
    )
}

private fun InvertedVField.label(): Int = when (this) {
    ARM -> R.string.dipole_arm
    FREQUENCY -> R.string.frequency
    FACTOR -> R.string.factor
    HEIGHT -> R.string.inverted_v_height
    ANGLE -> R.string.inverted_v_angle
}

private fun InvertedVMode.label(): Int =
    if (this == InvertedVMode.MEASURED) R.string.inverted_v_measured else R.string.inverted_v_estimated

private fun InvertedVResult.notice(): Int? = when {
    endHeightMeters < 0 -> R.string.inverted_v_below_ground
    endHeightMeters == 0.0 -> R.string.inverted_v_ground_contact
    else -> null
}

private fun InvertedVResult.rows(context: Context, unit: LengthUnit) = listOf(
    R.string.dipole_arm to formatInvertedVLength(context, armMeters, unit),
    R.string.dipole_total to formatInvertedVLength(context, wireMeters, unit),
    R.string.inverted_v_span to formatInvertedVLength(context, spanMeters, unit),
    R.string.inverted_v_end_height to formatInvertedVLength(context, endHeightMeters, unit),
)

private fun formatInvertedVLength(context: Context, meters: Double, unit: LengthUnit): String {
    val rounded = BigDecimal.valueOf(meters).movePointRight(9)
        .divide(BigDecimal.valueOf(unit.nanometersPerUnit), 2, RoundingMode.HALF_UP)
    val number = localizedNumber(context, rounded.toPlainString().replace('-', '−'))
    return context.getString(R.string.value_unit, number, context.getString(unit.labelResource()))
}

private fun InvertedVDraft.copyText(context: Context): String? {
    val result = result() ?: return null
    fun line(label: Int, value: String) = context.getString(R.string.label_value, context.getString(label), value)
    fun input(field: InvertedVField) = localizedNumber(context, fields.getValue(field))
    fun withUnit(value: String, unit: Int) = context.getString(R.string.value_unit, value, context.getString(unit))
    return buildList {
        add(context.getString(R.string.inverted_v_title))
        add("")
        add(line(R.string.inverted_v_mode, context.getString(mode.label())))
        if (mode == InvertedVMode.ESTIMATED) {
            add(line(R.string.frequency, withUnit(input(FREQUENCY), frequencyUnit.labelResource())))
            add(line(R.string.factor, input(FACTOR)))
        } else {
            add(line(R.string.dipole_arm, withUnit(input(ARM), lengthUnit.labelResource())))
        }
        add(line(R.string.inverted_v_height, withUnit(input(HEIGHT), lengthUnit.labelResource())))
        add(line(R.string.inverted_v_angle, context.getString(R.string.inverted_v_degrees, input(ANGLE))))
        add("")
        add(context.getString(R.string.inverted_v_geometry))
        result.rows(context, lengthUnit).forEach { (label, value) -> add(line(label, value)) }
        result.notice()?.let { add(context.getString(it)) }
        add("")
        add(context.getString(R.string.inverted_v_formula_alt))
        add(context.getString(R.string.inverted_v_limits))
    }.joinToString("\n")
}

@Composable
private fun InvertedVDiagram(result: InvertedVResult, unit: LengthUnit, context: Context) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val description = stringResource(
        R.string.inverted_v_diagram_alt,
        formatInvertedVLength(context, result.armMeters, unit),
        formatInvertedVLength(context, result.apexMeters, unit),
        formatInvertedVLength(context, result.endHeightMeters, unit),
        formatInvertedVLength(context, result.spanMeters, unit),
        localizedNumber(context, (result.angleTenths / 10.0).toString().removeSuffix(".0")),
    )
    Canvas(Modifier.fillMaxWidth().height(168.dp).testTag("inverted-v-diagram").semantics {
        contentDescription = description
    }) {
        val halfSpan = result.spanMeters / 2
        val vertical = result.apexMeters - minOf(0.0, result.endHeightMeters)
        val scale = minOf((size.width * .32) / halfSpan, (size.height * .62) / vertical)
        val apex = Offset(size.width / 2, size.height * .16f)
        val groundY = apex.y + (result.apexMeters * scale).toFloat()
        val endY = groundY - (result.endHeightMeters * scale).toFloat()
        val left = Offset(apex.x - (halfSpan * scale).toFloat(), endY)
        val right = Offset(apex.x + (halfSpan * scale).toFloat(), endY)
        val spanY = maxOf(groundY, endY) + 12.dp.toPx()
        val guide = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        fun guideLine(start: Offset, end: Offset) {
            drawLine(colors.onSurfaceVariant, start, end, 1.dp.toPx(), pathEffect = guide)
        }
        fun label(mark: String, x: Float, y: Float) {
            val text = textMeasurer.measure(mark, TextStyle(color = colors.secondary, fontSize = 12.sp))
            drawText(text, topLeft = Offset(
                x.coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f)),
                y.coerceIn(0f, (size.height - text.size.height).coerceAtLeast(0f)),
            ))
        }
        drawLine(colors.outline, Offset(0f, groundY), Offset(size.width, groundY), 1.dp.toPx())
        guideLine(apex, Offset(apex.x, groundY))
        guideLine(right, Offset(right.x, groundY))
        guideLine(left, Offset(left.x, spanY))
        guideLine(right, Offset(right.x, spanY))
        drawLine(colors.outline, Offset(left.x, spanY), Offset(right.x, spanY), 1.dp.toPx())
        drawLine(colors.primary, left, apex, 3.dp.toPx())
        drawLine(colors.primary, apex, right, 3.dp.toPx())
        drawCircle(colors.primary, 3.dp.toPx(), apex)
        val tips = if (result.endHeightMeters < 0) colors.error else colors.primary
        drawCircle(tips, 3.dp.toPx(), left)
        drawCircle(tips, 3.dp.toPx(), right)
        val radius = minOf(17.dp.toPx(), (result.armMeters * scale * .46).toFloat())
        val degrees = result.angleTenths / 10f
        drawArc(
            colors.secondary, 90f - degrees / 2, degrees, false,
            topLeft = apex - Offset(radius, radius), size = Size(2 * radius, 2 * radius),
            style = Stroke(1.dp.toPx()),
        )
        label("α", apex.x + radius + 3.dp.toPx(), apex.y - 13.dp.toPx())
        label("H", apex.x - 18.dp.toPx(), (apex.y + groundY) / 2)
        label("h", right.x + 5.dp.toPx(), minOf(groundY, endY))
        label("D", apex.x + 6.dp.toPx(), spanY)
    }
}
