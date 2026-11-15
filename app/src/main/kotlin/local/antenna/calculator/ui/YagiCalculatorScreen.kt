package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import local.antenna.calculator.core.layout.YagiDesign
import local.antenna.calculator.core.layout.YagiMatchingCable
import local.antenna.calculator.core.layout.YagiElement
import local.antenna.calculator.core.layout.calculateYagiCutList
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit

private const val DK_HOME = "https://dk7zb.darc.de/"
private const val DG_HOME = "https://www.dg7ybn.de/"

internal enum class YagiProfile(
    val design: YagiDesign, val label: String, val hz: List<Long>, val image: Int,
    val mount: Int, val feed: Int, val original: String, val terms: String?, val rights: Int,
    val measure: Int = R.string.yagi_measure,
) {
    YBN(YagiDesign.YBN, "DG7YBN · YBN 2‑5m", listOf(144_300_000), R.drawable.yagi_dg_hero,
        R.string.yagi_dg_mount, R.string.yagi_dg_feed, DG_HOME + "144MHz/144_5elem.htm", DG_HOME, R.string.yagi_dg_rights, R.string.yagi_dg_measure),
    R3(YagiDesign.R3, "R3UDK", listOf(R3udkYagi.FREQUENCY_HZ), R.drawable.yagi_r3_hero,
        R.string.yagi_r3_mount, R.string.yagi_r3_feed, LIZAALERT_YAGI_URL, null, R.string.yagi_r3_rights),
    DK2(YagiDesign.DK2, "DK7ZB · 2 m · 3 el. · 50 Ω", listOf(144_000_000, 146_000_000), R.drawable.yagi_dk2_hero,
        R.string.yagi_dk2_mount, R.string.yagi_dk2_feed, DK_HOME + "PVC-Yagis/3-Ele-2m.htm", DK_HOME, R.string.yagi_dk_rights),
    DK50(YagiDesign.DK50, "DK7ZB · 70 cm · 5 el. · 50 Ω", listOf(430_000_000, 440_000_000), R.drawable.yagi_dk50_hero,
        R.string.yagi_dk50_mount, R.string.yagi_dk50_feed, DK_HOME + "70cm/5.htm", DK_HOME, R.string.yagi_dk_rights),
    DK28(YagiDesign.DK28, "DK7ZB · 70 cm · 5 el. · 28 Ω", listOf(432_000_000), R.drawable.yagi_dk28_hero,
        R.string.yagi_dk28_mount, R.string.yagi_dk28_feed, DK_HOME + "70cm/5.htm", DK_HOME, R.string.yagi_dk_rights),
    DUO(YagiDesign.DUO, "DK7ZB · Duoband 2+2", listOf(144_000_000, 146_000_000, 430_000_000, 440_000_000), R.drawable.yagi_duo_hero,
        R.string.yagi_duo_mount, R.string.yagi_duo_feed, DK_HOME + "Duoband/duoband_2-70_2-3.htm", DK_HOME, R.string.yagi_dk_rights, R.string.yagi_duo_measure),
    GTV(YagiDesign.GTV, "DG7YBN · GTV 2‑6m", listOf(144_100_000), R.drawable.yagi_gtv_hero,
        R.string.yagi_gtv_mount, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights);

    val id get() = design.id
    val matchingCables get() = if (this == DK28) YagiMatchingCable.entries else emptyList()
    val buildSource get() = when (this) {
        DK2 -> DK_HOME + "PVC-Yagis/PVC-details.htm"
        DK28 -> DK_HOME + "70cm/70cm-kurz.htm"
        else -> original
    }

    fun frequency(context: Context): String = hz.chunked(2).joinToString(" / ") { range ->
        range.joinToString(" – ") { context.frequencyWithUnit(it, FrequencyUnit.MEGAHERTZ) }
    }
}

private val inputFields = listOf(R.string.yagi_allowance, R.string.yagi_rear, R.string.yagi_front)
private val decimal = Regex("(?:[0-9]{1,4}(?:[.,][0-9]{1,3})?|[.,][0-9]{1,3})")

private fun Context.yagiCalculatorPreferences() =
    applicationContext.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)

internal fun Context.selectYagiCalculatorProfile(profile: YagiProfile) {
    yagiCalculatorPreferences().edit { putString("profile", profile.id) }
}

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
        context.yagiCalculatorPreferences()
    }
    var profile by remember {
        mutableStateOf(YagiProfile.entries.firstOrNull { it.id == prefs.getString("profile", null) } ?: YagiProfile.R3)
    }
    var unit by remember {
        mutableStateOf(LengthUnit.entries.firstOrNull { it.name == prefs.getString("unit", null) } ?: LengthUnit.MILLIMETERS)
    }
    val fields = key(profile) {
        val defaultOffset = if (profile == YagiProfile.GTV) GTV_REAR_OFFSET_MM.toInt().toString() else ""
        listOf(
            rememberTextFieldState(prefs.getString("${profile.id}_0", "").orEmpty().take(24)),
            rememberTextFieldState(prefs.getString("${profile.id}_1", defaultOffset).orEmpty().take(24)),
            rememberTextFieldState(prefs.getString("${profile.id}_2", defaultOffset).orEmpty().take(24)),
        )
    }
    val draft = fields.map { it.text.toString() }
    LaunchedEffect(prefs, profile, fields) {
        val prefix = profile.id
        snapshotFlow { fields.map { it.text.toString() } }.drop(1).collect { entries ->
            prefs.edit { entries.forEachIndexed { index, value -> putString("${prefix}_$index", value) } }
        }
    }
    val exampleColor = inputExampleColor()
    val values = draft.map { raw ->
        raw.trim().takeIf(decimal::matches)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }
    }
    val errors = draft.indices.map { draft[it].isNotBlank() && values[it] == null }
    val elements = profile.design.elements
    val cut = if (errors.none { it }) calculateYagiCutList(profile.design, values[0] ?: 0.0, values[1], values[2]) else null
    val profileLabel: (YagiProfile) -> String = { "${it.label} · ${it.frequency(context)}" }
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
            .padding(20.dp).testTag("yagi-calculator"), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RotatingAntenna("yagi-${profile.design.ordinal}", profileLabel(profile), baseHeight = 160.dp)
        YagiProfilePicker(profile, profileLabel) { selected ->
            val previousId = profile.id
            prefs.edit {
                fields.forEachIndexed { index, field -> putString("${previousId}_$index", field.text.toString()) }
                putString("profile", selected.id)
            }
            profile = selected
        }
        YagiProperty(R.string.frequency, profile.frequency(context))
        YagiProperty(R.string.yagi_mount, stringResource(profile.mount))
        YagiProperty(R.string.yagi_feed, stringResource(profile.feed))
        val published = profile.publishedNotes
        val isGtv = profile == YagiProfile.GTV
        YagiHeading(R.string.yagi_geometry)
        YagiDiagram(elements, isGtv)
        HelpText(if (isGtv) R.string.yagi_gtv_scheme else R.string.yagi_scheme)
        YagiHeading(R.string.yagi_dimensions)
        ChoicePicker(stringResource(R.string.loop_output_unit), LengthUnit.entries, unit,
            { stringResource(it.labelResource()) },
            { selected -> unit = selected; prefs.edit { putString("unit", selected.name) } }, "yagi-units")
        YagiTable(elements, unit, isGtv, values[1])
        if (published != null) {
            YagiHeading(R.string.yagi_published)
            HelpText(published)
        }
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
                inputTransformation = decimalInputTransformation(limit = 24),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errors[index],
                supportingText = if (errors[index]) {{ Text(stringResource(R.string.yagi_invalid)) }} else null,
                modifier = Modifier.fillMaxWidth().testTag("yagi-input-$index")
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        YagiProperty(R.string.yagi_span, yagiLength(context, elements.last().positionMm, unit))
        if (cut != null) {
            YagiProperty(R.string.yagi_boom, cut.boomMm?.let { yagiLength(context, it, unit) }
                ?: stringResource(R.string.yagi_unset))
            cut.blanksMm.forEachIndexed { index, mm ->
                YagiProperty(yagiElementLabel(elements[index].id), mm?.let { yagiLength(context, it, unit) }
                    ?: stringResource(R.string.yagi_blank_unknown))
            }
        }
        if (profile.matchingCables.isNotEmpty()) {
            YagiHeading(R.string.yagi_matching_title)
            Text(stringResource(R.string.yagi_matching_diagram), color = MaterialTheme.colorScheme.secondary)
            HelpText(R.string.yagi_matching_topology)
            profile.matchingCables.forEach { cable ->
                YagiProperty(cable.label(), stringResource(R.string.yagi_matching_pair, yagiLength(context, cable.sectionMm, unit)))
            }
        }
        HelpText(if (profile == YagiProfile.R3) R.string.yagi_formed else R.string.yagi_limited)
        if (onSave != null) {
            Button(onClick = {
                if (cut != null) onSave(yagiSnapshot(profile, values, cut, unit), yagiReport(context, profile, cut, values, unit))
            }, enabled = cut != null, modifier = Modifier.fillMaxWidth().testTag("yagi-save")) {
                Text(stringResource(R.string.jpole_copyProject))
            }
        }
        OutlinedButton(onClick = {
            if (cut != null) {
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                clipboard.setPrimaryClip(ClipData.newPlainText(profile.label, yagiReport(context, profile, cut, values, unit)))
                onCopied()
            }
        }, enabled = cut != null, modifier = Modifier.fillMaxWidth().testTag("yagi-copy")) {
            Text(stringResource(R.string.yagi_copy))
        }
        if (profile == YagiProfile.R3) {
            TextButton(onClick = onGuide, modifier = Modifier.testTag("yagi-open-guide")) { Text(stringResource(R.string.yagi_guide)) }
        } else {
            TextButton(onClick = { openSourceWithFeedback(context, profile.buildSource) }) {
                Text(stringResource(R.string.yagi_external_guide))
            }
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
private fun YagiDiagram(elements: List<YagiElement>, isGtv: Boolean) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(if (isGtv) R.string.yagi_gtv_scheme else R.string.yagi_scheme)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val left = size.width * .08f
        val right = size.width - left
        val center = size.height / 2
        drawLine(colors.outline, Offset(0f, center), Offset(size.width, center), 3.dp.toPx())
        val longest = elements.maxOf(YagiElement::overallMm)
        val boomScale = (right - left) / elements.last().positionMm.toFloat()
        elements.forEachIndexed { index, element ->
            val x = left + boomScale * element.positionMm.toFloat()
            val half = center * .72f * (element.overallMm / longest).toFloat()
            val stroke = 2.5.dp.toPx()
            val labelColor = if (element.id == "de") colors.tertiary else colors.secondary
            val label = if (element.id == "de") "DE" else element.id.uppercase()
            val layout = textMeasurer.measure(label, style = labelStyle)
            val labelY = if (index % 2 == 0) 0f else size.height - layout.size.height
            drawText(layout, color = labelColor, topLeft = Offset(x - layout.size.width / 2f, labelY))
            if (element.id == "de") {
                val bendHalf = if (isGtv) half * GTV_BEND_FRACTION.toFloat() else half
                val feedGapHalf = minOf(5.dp.toPx(), bendHalf / 2f)
                for (side in listOf(-1f, 1f)) {
                    val bend = Offset(x, center + side * bendHalf)
                    drawLine(colors.tertiary, Offset(x, center + side * feedGapHalf), bend, stroke)
                    if (isGtv) {
                        val tip = Offset(left + boomScale * GTV_TIP_FROM_REFLECTOR_MM.toFloat(), center + side * half)
                        drawLine(colors.tertiary, bend, tip, stroke)
                    }
                }
            } else drawLine(colors.primary, Offset(x, center - half), Offset(x, center + half), stroke)
        }
    }
}

@Composable
private fun YagiTable(elements: List<YagiElement>, unit: LengthUnit, isGtv: Boolean, rearMm: Double?) {
    val context = LocalContext.current
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val width = if (rearMm != null) 700.dp else 580.dp
    ScrollableTable(Modifier.width(width * scale).testTag("yagi-dimensions-table")) {
        val headers = buildList {
            addAll(listOf(R.string.yagi_element, R.string.yagi_diameter, R.string.yagi_position))
            if (rearMm != null) add(R.string.yagi_gtv_position)
            add(R.string.yagi_spacing)
            add(if (isGtv) R.string.yagi_gtv_table_length else R.string.yagi_overall)
        }
        YagiRow(headers.map { stringResource(it) }, header = true)
        elements.forEachIndexed { index, element ->
            HorizontalDivider()
            val gap = elements.getOrNull(index - 1)?.let { element.positionMm - it.positionMm }
            YagiRow(buildList {
                add(stringResource(yagiElementLabel(element.id)))
                add(yagiLength(context, element.diameterMm, unit))
                add(yagiLength(context, element.positionMm, unit))
                if (rearMm != null) add(yagiLength(context, element.positionMm + rearMm, unit))
                add(gap?.let { yagiLength(context, it, unit) } ?: "—")
                add(yagiLength(context, element.overallMm, unit))
            })
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

internal fun yagiLength(context: Context, mm: Double, unit: LengthUnit): String {
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

private fun yagiSnapshot(profile: YagiProfile, values: List<Double?>, cut: YagiCutList, unit: LengthUnit): CalculationSnapshot {
    val inputs = buildList {
        val primary = profile.hz.take(2).average()
        add(SnapshotValue("frequency", primary, SnapshotUnit.HERTZ))
        if (profile.hz.size > 1) {
            profile.hz.chunked(2).forEachIndexed { index, band ->
                add(SnapshotValue("yagi_band_${index + 1}_low", band.first().toDouble(), SnapshotUnit.HERTZ))
                add(SnapshotValue("yagi_band_${index + 1}_high", band.last().toDouble(), SnapshotUnit.HERTZ))
            }
        }
        if (profile != YagiProfile.R3) {
            profile.design.elements.forEach { add(lengthMetric("yagi_${it.id}_diameter", it.diameterMm)) }
        }
        add(SnapshotValue("yagi_unit", unit.ordinal.toDouble(), SnapshotUnit.FACTOR))
        add(lengthMetric("yagi_allowance", values[0] ?: 0.0))
        values[1]?.let { add(lengthMetric("yagi_rear", it)) }
        values[2]?.let { add(lengthMetric("yagi_front", it)) }
    }
    val outputs = buildList {
        add(lengthMetric("yagi_span", cut.spanMm))
        cut.boomMm?.let { add(lengthMetric("yagi_boom", it)) }
        profile.design.elements.forEachIndexed { index, element ->
            add(lengthMetric("yagi_${element.id}_position", element.positionMm))
            val lengthKey = if (profile == YagiProfile.GTV) "table" else "overall"
            add(lengthMetric("yagi_${element.id}_$lengthKey", element.overallMm))
            if (profile == YagiProfile.R3) add(lengthMetric("yagi_${element.id}_diameter", element.diameterMm))
            cut.blanksMm[index]?.let { add(lengthMetric("yagi_${element.id}_blank", it)) }
        }
        profile.matchingCables.forEach { add(lengthMetric(it.metricKey, it.sectionMm)) }
    }
    val warnings = if (cut.blanksMm.any { it == null }) listOf("YAGI_PARTIAL_CUT_LIST") else emptyList()
    return CalculationSnapshot(profile.design.model, profile.design.revision, inputs, outputs, warnings)
}

private fun lengthMetric(key: String, mm: Double) = SnapshotValue(key, mm / 1000, SnapshotUnit.METERS)

private fun yagiReport(context: Context, profile: YagiProfile, cut: YagiCutList, values: List<Double?>, unit: LengthUnit): String = buildString {
    fun line(label: Int, text: String) = appendLine(context.getString(R.string.journal_property, context.getString(label), text))
    appendLine("${profile.label} · ${profile.frequency(context)} · #${profile.design.revision}")
    appendLine()
    appendLine(context.getString(profile.mount))
    appendLine(context.getString(profile.feed))
    appendLine()
    inputFields.forEachIndexed { index, label ->
        val value = values[index] ?: if (index == 0) 0.0 else null
        line(label, value?.let { yagiLength(context, it, LengthUnit.MILLIMETERS) } ?: "—")
    }
    line(R.string.yagi_span, yagiLength(context, cut.spanMm, unit))
    line(R.string.yagi_boom, cut.boomMm?.let { yagiLength(context, it, unit) } ?: "—")
    profile.design.elements.forEachIndexed { index, element ->
        appendLine()
        appendLine(context.getString(yagiElementLabel(element.id)))
        line(R.string.yagi_diameter, yagiLength(context, element.diameterMm, unit))
        line(R.string.yagi_position, yagiLength(context, element.positionMm, unit))
        profile.design.elements.getOrNull(index - 1)?.let { previous ->
            line(R.string.yagi_spacing, yagiLength(context, element.positionMm - previous.positionMm, unit))
        }
        values[1]?.let { rear -> line(R.string.yagi_gtv_position, yagiLength(context, element.positionMm + rear, unit)) }
        line(if (profile == YagiProfile.GTV) R.string.yagi_gtv_table_length else R.string.yagi_overall,
            yagiLength(context, element.overallMm, unit))
        line(R.string.yagi_record_blank, cut.blanksMm[index]?.let { yagiLength(context, it, unit) }
            ?: context.getString(R.string.yagi_blank_unknown))
    }
    appendLine()
    profile.matchingCables.forEach { cable ->
        line(cable.label(), context.getString(R.string.yagi_matching_pair, yagiLength(context, cable.sectionMm, unit)))
    }
    appendLine(context.getString(if (profile == YagiProfile.R3) R.string.yagi_formed else R.string.yagi_limited))
    profile.publishedNotes?.let { appendLine(context.getString(it)) }
    if (profile.buildSource != profile.original) appendLine(profile.buildSource)
    append(profile.original)
}
