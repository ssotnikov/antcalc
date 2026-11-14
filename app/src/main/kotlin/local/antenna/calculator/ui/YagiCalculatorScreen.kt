package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.flow.drop
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.layout.R3udkYagi
import local.antenna.calculator.core.layout.YagiCutList
import local.antenna.calculator.core.layout.calculateR3udkCutList
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit

private const val DK_HOME = "https://dk7zb.darc.de/"
private const val DG_HOME = "https://www.dg7ybn.de/"

internal enum class YagiProfile(
    val id: String, val label: String, val hz: List<Long>, val image: Int,
    val mount: Int, val feed: Int, val original: String, val terms: String?, val rights: Int,
    val measure: Int = R.string.yagi_measure,
) {
    YBN("ybn_2_5m", "DG7YBN · YBN 2‑5m", listOf(144_300_000), R.drawable.yagi_dg_hero,
        R.string.yagi_dg_mount, R.string.yagi_dg_feed, DG_HOME + "144MHz/144_5elem.htm", DG_HOME, R.string.yagi_dg_rights, R.string.yagi_dg_measure),
    R3("r3udk", "R3UDK", listOf(R3udkYagi.FREQUENCY_HZ), R.drawable.yagi_r3_hero,
        R.string.yagi_r3_mount, R.string.yagi_r3_feed, LIZAALERT_YAGI_URL, null, R.string.yagi_r3_rights),
    DK2("dk7zb_2m", "DK7ZB · 3 el. · 50 Ω", listOf(144_000_000, 146_000_000), R.drawable.yagi_dk2_hero,
        R.string.yagi_dk2_mount, R.string.yagi_dk2_feed, DK_HOME + "PVC-Yagis/3-Ele-2m.htm", DK_HOME, R.string.yagi_dk_rights),
    DK50("dk7zb_70cm_50", "DK7ZB · 5 el. · 50 Ω", listOf(430_000_000, 440_000_000), R.drawable.yagi_dk50_hero,
        R.string.yagi_dk50_mount, R.string.yagi_dk50_feed, DK_HOME + "70cm/5.htm", DK_HOME, R.string.yagi_dk_rights),
    DK28("dk7zb_70cm_28", "DK7ZB · 5 el. · 28 Ω", listOf(432_000_000), R.drawable.yagi_dk28_hero,
        R.string.yagi_dk28_mount, R.string.yagi_dk28_feed, DK_HOME + "70cm/5.htm", DK_HOME, R.string.yagi_dk_rights),
    DUO("dk7zb_duoband_22", "DK7ZB · Duoband 2+2", listOf(144_000_000, 146_000_000, 430_000_000, 440_000_000), R.drawable.yagi_duo_hero,
        R.string.yagi_duo_mount, R.string.yagi_duo_feed, DK_HOME + "Duoband/duoband_2-70_2-3.htm", DK_HOME, R.string.yagi_dk_rights, R.string.yagi_duo_measure),
    GTV("gtv_2_6m", "DG7YBN · GTV 2‑6m", listOf(144_100_000), R.drawable.yagi_gtv_hero,
        R.string.yagi_gtv_mount, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights);

    fun frequency(context: Context): String = hz.chunked(2).joinToString(" / ") { range ->
        range.joinToString(" – ") { context.frequencyWithUnit(it, FrequencyUnit.MEGAHERTZ) }
    }
}

private val inputFields = listOf(R.string.yagi_allowance, R.string.yagi_rear, R.string.yagi_front)
private val rowLabels = listOf(R.string.construction_yagi_reflector, R.string.construction_yagi_driven,
    R.string.construction_yagi_director_1, R.string.construction_yagi_director_2)
private val decimal = Regex("(?:[0-9]{1,4}(?:[.,][0-9]{1,3})?|[.,][0-9]{1,3})")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YagiCalculatorScreen(
    onGuide: () -> Unit,
    onMeasurements: (() -> Unit)?,
    onSave: ((CalculationSnapshot, String) -> Unit)?,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
    }
    var profile by remember {
        mutableStateOf(YagiProfile.entries.firstOrNull { it.id == prefs.getString("profile", null) } ?: YagiProfile.R3)
    }
    var unit by remember {
        mutableStateOf(LengthUnit.entries.firstOrNull { it.name == prefs.getString("unit", null) } ?: LengthUnit.MILLIMETERS)
    }
    val allowance = rememberTextFieldState(prefs.getString("r3udk_0", "").orEmpty().take(24))
    val rear = rememberTextFieldState(prefs.getString("r3udk_1", "").orEmpty().take(24))
    val front = rememberTextFieldState(prefs.getString("r3udk_2", "").orEmpty().take(24))
    val fields = listOf(allowance, rear, front)
    val draft = fields.map { it.text.toString() }
    LaunchedEffect(prefs, allowance, rear, front) {
        snapshotFlow { fields.map { it.text.toString() } }.drop(1).collect { entries ->
            prefs.edit { entries.forEachIndexed { index, value -> putString("r3udk_$index", value) } }
        }
    }
    val exampleColor = inputExampleColor()
    val values = draft.map { raw ->
        raw.trim().takeIf(decimal::matches)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }
    }
    val errors = draft.indices.map { draft[it].isNotBlank() && values[it] == null }
    val cut = if (errors.none { it }) calculateR3udkCutList(values[0] ?: 0.0, values[1], values[2]) else null
    val profileLabel: (YagiProfile) -> String = { "${it.label} · ${it.frequency(context)}" }
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
            .padding(20.dp).testTag("yagi-calculator"), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(profile.image), profileLabel(profile), Modifier.fillMaxWidth().height(160.dp))
        YagiProfilePicker(profile, profileLabel) { selected ->
            profile = selected
            prefs.edit { putString("profile", selected.id) }
        }
        YagiProperty(R.string.frequency, profile.frequency(context))
        YagiProperty(R.string.yagi_mount, stringResource(profile.mount))
        YagiProperty(R.string.yagi_feed, stringResource(profile.feed))
        if (profile == YagiProfile.R3) {
            YagiHeading(R.string.yagi_geometry)
            YagiDiagram()
            HelpText(R.string.yagi_scheme)
            YagiHeading(R.string.yagi_dimensions)
            ChoicePicker(stringResource(R.string.loop_output_unit), LengthUnit.entries, unit,
                { stringResource(it.labelResource()) },
                { selected -> unit = selected; prefs.edit { putString("unit", selected.name) } }, "yagi-units")
            YagiTable(unit)
            YagiHeading(R.string.yagi_cuts)
            inputFields.forEachIndexed { index, label ->
                var focused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    state = fields[index],
                    label = { Text(stringResource(label)) },
                    labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                    placeholder = if (focused) null else {
                        { Text(if (index == 0) "0" else "20", color = exampleColor) }
                    },
                    inputTransformation = InputTransformation { if (length > 24) revertAllChanges() },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errors[index],
                    supportingText = if (errors[index]) {{ Text(stringResource(R.string.yagi_invalid)) }} else null,
                    modifier = Modifier.fillMaxWidth().testTag("yagi-input-$index")
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            YagiProperty(R.string.yagi_span, yagiLength(context, R3udkYagi.elements.last().positionMm, unit))
            if (cut != null) {
                YagiProperty(R.string.yagi_boom, cut.boomMm?.let { yagiLength(context, it, unit) }
                    ?: stringResource(R.string.yagi_unset))
                cut.blanksMm.forEachIndexed { index, mm -> YagiProperty(rowLabels[index], yagiLength(context, mm, unit)) }
            }
            HelpText(R.string.yagi_formed)
            if (onSave != null) {
                Button(onClick = {
                    if (cut != null) onSave(yagiSnapshot(values, cut, unit), yagiReport(context, cut, values, unit))
                }, enabled = cut != null, modifier = Modifier.fillMaxWidth().testTag("yagi-save")) {
                    Text(stringResource(R.string.jpole_copyProject))
                }
            }
            OutlinedButton(onClick = {
                if (cut != null) {
                    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                    clipboard.setPrimaryClip(ClipData.newPlainText(profile.label, yagiReport(context, cut, values, unit)))
                    onCopied()
                }
            }, enabled = cut != null, modifier = Modifier.fillMaxWidth().testTag("yagi-copy")) {
                Text(stringResource(R.string.yagi_copy))
            }
            TextButton(onClick = onGuide, modifier = Modifier.testTag("yagi-open-guide")) { Text(stringResource(R.string.yagi_guide)) }
        } else {
            HelpText(R.string.yagi_limited)
            if (profile == YagiProfile.GTV) HelpText(R.string.yagi_gtv_pending)
        }
        YagiHeading(R.string.yagi_check)
        HelpText(profile.measure)
        if (onMeasurements != null) {
            TextButton(onClick = onMeasurements) { Text(stringResource(R.string.sweep_tool_title)) }
        }
        HorizontalDivider()
        YagiHeading(R.string.yagi_rights)
        HelpText(profile.rights)
        TextButton(onClick = { openSourceWithFeedback(context, profile.original) }) {
            Text(stringResource(R.string.yagi_original))
        }
        profile.terms?.let { terms ->
            TextButton(onClick = { openSourceWithFeedback(context, terms) }) { Text(stringResource(R.string.yagi_policy)) }
        }
    }
}

@Composable
private fun YagiProfilePicker(
    profile: YagiProfile, label: (YagiProfile) -> String, select: (YagiProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YagiHeading(R.string.yagi_profile)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("yagi-profile")) {
                Text(label(profile), Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded, { expanded = false }) {
                YagiProfile.entries.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(label(item)) },
                        onClick = { expanded = false; select(item) },
                        modifier = Modifier.testTag("yagi-profile-${item.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun YagiHeading(label: Int) {
    Text(stringResource(label), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun YagiProperty(label: Int, value: String) {
    Text(stringResource(R.string.journal_property, stringResource(label), value))
}

@Composable
private fun YagiDiagram() {
    val elements = R3udkYagi.elements
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.yagi_scheme)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val left = size.width * .08f
        val right = size.width - left
        val center = size.height / 2
        drawLine(colors.outline, Offset(0f, center), Offset(size.width, center), 3.dp.toPx())
        elements.forEach { element ->
            val x = left + (right - left) * (element.positionMm / elements.last().positionMm).toFloat()
            val half = center * .72f * (element.overallMm / elements.first().overallMm).toFloat()
            val stroke = 2.5.dp.toPx()
            val labelColor = if (element.id == "de") colors.tertiary else colors.secondary
            val label = if (element.id == "de") "DE" else element.id.uppercase()
            val layout = textMeasurer.measure(label, style = labelStyle)
            drawText(layout, color = labelColor, topLeft = Offset(x - layout.size.width / 2f, 0f))
            if (element.id == "de") {
                drawLine(colors.tertiary, Offset(x, center - half), Offset(x, center - 5.dp.toPx()), stroke)
                drawLine(colors.tertiary, Offset(x, center + 5.dp.toPx()), Offset(x, center + half), stroke)
            } else drawLine(colors.primary, Offset(x, center - half), Offset(x, center + half), stroke)
        }
    }
}

@Composable
private fun YagiTable(unit: LengthUnit) {
    val context = LocalContext.current
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    ScrollableTable(Modifier.width(580.dp * scale)) {
        val headers = listOf(R.string.yagi_element, R.string.yagi_diameter, R.string.yagi_position,
            R.string.yagi_spacing, R.string.yagi_overall)
        YagiRow(headers.map { stringResource(it) }, header = true)
        val elements = R3udkYagi.elements
        elements.forEachIndexed { index, element ->
            HorizontalDivider()
            val gap = elements.getOrNull(index - 1)?.let { element.positionMm - it.positionMm }
            YagiRow(listOf(stringResource(rowLabels[index]), yagiLength(context, element.diameterMm, unit),
                yagiLength(context, element.positionMm, unit), gap?.let { yagiLength(context, it, unit) } ?: "—",
                yagiLength(context, element.overallMm, unit)))
        }
    }
}

@Composable
private fun YagiRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(cell, Modifier.weight(if (index == 0) 1.5f else 1f),
                color = if (header) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun yagiLength(context: Context, mm: Double, unit: LengthUnit): String {
    val factor = when (unit) {
        LengthUnit.MILLIMETERS -> 1.0
        LengthUnit.CENTIMETERS -> 10.0
        LengthUnit.METERS -> 1000.0
    }
    val locale = context.resources.configuration.locales[0]
    val value = java.text.NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = if (unit == LengthUnit.METERS) 6 else if (unit == LengthUnit.CENTIMETERS) 4 else 3
    }.format(mm / factor)
    return "$value ${context.getString(unit.labelResource())}"
}

private fun yagiSnapshot(values: List<Double?>, cut: YagiCutList, unit: LengthUnit): CalculationSnapshot {
    val inputs = buildList {
        add(SnapshotValue("frequency", R3udkYagi.FREQUENCY_HZ.toDouble(), SnapshotUnit.HERTZ))
        add(SnapshotValue("yagi_unit", unit.ordinal.toDouble(), SnapshotUnit.FACTOR))
        add(lengthMetric("yagi_allowance", values[0] ?: 0.0))
        values[1]?.let { add(lengthMetric("yagi_rear", it)) }
        values[2]?.let { add(lengthMetric("yagi_front", it)) }
    }
    val outputs = buildList {
        add(lengthMetric("yagi_span", cut.spanMm))
        cut.boomMm?.let { add(lengthMetric("yagi_boom", it)) }
        R3udkYagi.elements.forEachIndexed { index, element ->
            add(lengthMetric("yagi_${element.id}_position", element.positionMm))
            add(lengthMetric("yagi_${element.id}_overall", element.overallMm))
            add(lengthMetric("yagi_${element.id}_diameter", element.diameterMm))
            add(lengthMetric("yagi_${element.id}_blank", cut.blanksMm[index]))
        }
    }
    return CalculationSnapshot("yagi_r3udk", R3udkYagi.REVISION, inputs, outputs)
}

private fun lengthMetric(key: String, mm: Double) = SnapshotValue(key, mm / 1000, SnapshotUnit.METERS)

private fun yagiReport(context: Context, cut: YagiCutList, values: List<Double?>, unit: LengthUnit): String = buildString {
    fun line(label: Int, text: String) = appendLine(context.getString(R.string.journal_property, context.getString(label), text))
    appendLine("R3UDK · ${YagiProfile.R3.frequency(context)} · #${R3udkYagi.REVISION}")
    appendLine()
    appendLine(context.getString(R.string.yagi_r3_mount))
    appendLine(context.getString(R.string.yagi_r3_feed))
    appendLine()
    inputFields.forEachIndexed { index, label ->
        val value = values[index] ?: if (index == 0) 0.0 else null
        line(label, value?.let { yagiLength(context, it, LengthUnit.MILLIMETERS) } ?: "—")
    }
    line(R.string.yagi_span, yagiLength(context, cut.spanMm, unit))
    line(R.string.yagi_boom, cut.boomMm?.let { yagiLength(context, it, unit) } ?: "—")
    R3udkYagi.elements.forEachIndexed { index, element ->
        appendLine()
        appendLine(context.getString(rowLabels[index]))
        line(R.string.yagi_diameter, yagiLength(context, element.diameterMm, unit))
        line(R.string.yagi_position, yagiLength(context, element.positionMm, unit))
        R3udkYagi.elements.getOrNull(index - 1)?.let { previous ->
            line(R.string.yagi_spacing, yagiLength(context, element.positionMm - previous.positionMm, unit))
        }
        line(R.string.yagi_overall, yagiLength(context, element.overallMm, unit))
        line(R.string.yagi_record_blank, yagiLength(context, cut.blanksMm[index], unit))
    }
    appendLine()
    appendLine(context.getString(R.string.yagi_formed))
    append(LIZAALERT_YAGI_URL)
}
