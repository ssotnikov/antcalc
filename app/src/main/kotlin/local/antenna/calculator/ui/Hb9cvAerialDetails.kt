package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.antenna.calculator.R
import local.antenna.calculator.core.Hb9cvAerial
import local.antenna.calculator.core.Hb9cvAerial.Dimension
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10

internal const val HB9CV_AERIAL_WAVE = "hb9cv_aerial_wave"
internal const val HB9CV_AERIAL_CAPACITANCE = "hb9cv_aerial_capacitance"

internal fun Dimension.labelResource() = when (this) {
    Dimension.LR -> R.string.hb9cv_aerial_lr
    Dimension.LF -> R.string.hb9cv_aerial_lf
    Dimension.MR -> R.string.hb9cv_aerial_mr
    Dimension.MF -> R.string.hb9cv_aerial_mf
    Dimension.A -> R.string.hb9cv_aerial_spacing
    Dimension.DIAMETER -> R.string.hb9cv_aerial_diameter
    Dimension.GAMMA_DIAMETER -> R.string.hb9cv_aerial_gamma_diameter
    Dimension.GAP -> R.string.hb9cv_aerial_gap
}

internal fun hb9cvAerialNumber(locale: Locale, value: Double, digits: Int): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
    }.format(value)

internal fun hb9cvAerialLength(
    locale: Locale, key: String, meters: Double, mhz: Double, mmUnit: String, meterUnit: String,
): String {
    val mm = meters * 1000
    val longDimension = key in setOf(HB9CV_AERIAL_WAVE, Dimension.LR.key, Dimension.LF.key)
    if (longDimension && mm > 1000) return "${hb9cvAerialNumber(locale, meters, 3)} $meterUnit"
    val digits = when (key) {
        HB9CV_AERIAL_WAVE, Dimension.LR.key, Dimension.LF.key -> if (mhz <= 400) 0 else 1
        Dimension.MR.key, Dimension.MF.key, Dimension.A.key -> if (mm >= 100) 0 else 1
        Dimension.DIAMETER.key, Dimension.GAMMA_DIAMETER.key, Dimension.GAP.key ->
            if (mm in 0.001..1000.0) (2 - floor(log10(mm)).toInt()).coerceAtLeast(0) else 3
        else -> 2
    }
    return "${hb9cvAerialNumber(locale, mm, digits)} $mmUnit"
}

@Composable
internal fun Hb9cvAerialDetails(
    onSave: ((CalculationSnapshot, String) -> Unit)?,
    onCopied: () -> Unit,
    onMeasurements: (() -> Unit)?,
) {
    val context = LocalContext.current
    val locale = LocalResources.current.configuration.locales[0]
    val mm = stringResource(R.string.hb9cv_aerial_mm)
    val meters = stringResource(R.string.hb9cv_aerial_m)
    val pf = stringResource(R.string.hb9cv_aerial_pf)
    val name = stringResource(R.string.hb9cv_aerial_name)
    val defaultFrequency = stringResource(R.string.hb9cv_aerial_default_frequency)
    var frequency by rememberSaveable { mutableStateOf(defaultFrequency) }
    var rearRaw by rememberSaveable { mutableStateOf("") }
    var frontRaw by rememberSaveable { mutableStateOf("") }
    var formulasOpen by rememberSaveable { mutableStateOf(false) }
    var tuningOpen by rememberSaveable { mutableStateOf(false) }
    val mhz = valueIn(frequency, Hb9cvAerial.MIN_MHZ, Hb9cvAerial.MAX_MHZ)
    val result = mhz?.let { Hb9cvAerial.Result(it) }
    val rear = valueIn(rearRaw, 0.0, Hb9cvAerial.MAX_OVERHANG_MM)
    val front = valueIn(frontRaw, 0.0, Hb9cvAerial.MAX_OVERHANG_MM)
    val boom = if (rear != null && front != null) result?.boom(rear, front) else null
    val rearInvalid = rearRaw.isNotEmpty() && rear == null
    val frontInvalid = frontRaw.isNotEmpty() && front == null
    val source = Hb9cvSource.AERIAL.url
    fun length(key: String, value: Double) =
        hb9cvAerialLength(locale, key, value, checkNotNull(mhz), mm, meters)
    fun metric(key: String, valueMm: Double) = SnapshotValue(key, valueMm / 1000, SnapshotUnit.METERS)
    fun millimeters(value: Double) = "${hb9cvAerialNumber(locale, value, 2)} $mm"

    Text(stringResource(R.string.hb9cv_aerial_intro), style = MaterialTheme.typography.bodyMedium)
    AerialInput(frequency, { frequency = it }, R.string.hb9cv_aerial_frequency, R.string.hb9cv_aerial_frequency_range,
        mhz == null, "hb9cv-aerial-frequency")
    Text(stringResource(R.string.hb9cv_diagram), style = MaterialTheme.typography.titleMedium)
    AerialDiagram()
    Text(stringResource(R.string.hb9cv_aerial_gamma_note), style = MaterialTheme.typography.bodySmall)

    Text(stringResource(R.string.hb9cv_aerial_dimensions), style = MaterialTheme.typography.titleMedium)
    val wavelengthLabel = stringResource(R.string.hb9cv_aerial_wavelength)
    val capacitanceLabel = stringResource(R.string.hb9cv_aerial_capacitance)
    val dimensions = buildList {
        add(wavelengthLabel to result?.let { length(HB9CV_AERIAL_WAVE, it.wavelengthMeters) })
        for (dimension in Dimension.entries) {
            add(stringResource(dimension.labelResource()) to result?.let { length(dimension.key, it.millimeters(dimension) / 1000) })
        }
        add(capacitanceLabel to result?.let { "${hb9cvAerialNumber(locale, it.capacitancePf, 1)} $pf" })
    }
    Column { dimensions.forEach { (label, value) -> AerialRow(label, value) } }
    val scalingNote = stringResource(R.string.hb9cv_aerial_scaling_note)
    SelectableText(scalingNote, style = MaterialTheme.typography.bodySmall)

    Text(stringResource(R.string.yagi_cuts), style = MaterialTheme.typography.titleMedium)
    AerialInput(rearRaw, { rearRaw = it }, R.string.hb9cv_rear, R.string.hb9cv_aerial_overhang_range,
        rearInvalid, "hb9cv-aerial-rear")
    AerialInput(frontRaw, { frontRaw = it }, R.string.hb9cv_front, R.string.hb9cv_aerial_overhang_range,
        frontInvalid, "hb9cv-aerial-front")
    val boomRows = listOf(
        stringResource(R.string.hb9cv_boom) to boom?.let { millimeters(it.lengthMm) },
        stringResource(R.string.hb9cv_aerial_position_rear) to boom?.let { millimeters(it.rearPositionMm) },
        stringResource(R.string.hb9cv_aerial_position_front) to boom?.let { millimeters(it.frontPositionMm) },
    )
    val cutRows = dimensions.filterNot { it.first == wavelengthLabel } + boomRows
    Column { cutRows.forEach { (label, value) -> AerialRow(label, value) } }
    if (boom == null) Text(stringResource(R.string.hb9cv_aerial_boom_hint), style = MaterialTheme.typography.bodySmall)
    val blankNote = stringResource(R.string.hb9cv_aerial_gamma_blank)
    SelectableText(blankNote, style = MaterialTheme.typography.bodySmall)

    val enabled = result != null && !rearInvalid && !frontInvalid
    val frequencyLabel = stringResource(R.string.hb9cv_aerial_frequency)
    val rearLabel = stringResource(R.string.hb9cv_rear)
    val frontLabel = stringResource(R.string.hb9cv_front)
    val boomHint = stringResource(R.string.hb9cv_aerial_boom_hint)
    fun report() = buildString {
        appendLine(name)
        appendLine("$frequencyLabel · ${hb9cvAerialNumber(locale, checkNotNull(mhz), 16)}")
        rear?.let { appendLine("$rearLabel · ${millimeters(it)}") }
        front?.let { appendLine("$frontLabel · ${millimeters(it)}") }
        (dimensions.take(1) + cutRows).forEach { (label, value) -> if (value != null) appendLine("$label · $value") }
        if (boom == null) appendLine(boomHint)
        appendLine(blankNote)
        appendLine(scalingNote)
        append(source)
    }
    if (onSave != null) Button(onClick = {
        val calculated = checkNotNull(result)
        val inputs = buildList {
            add(SnapshotValue("frequency", calculated.frequencyMhz * 1e6, SnapshotUnit.HERTZ))
            rear?.let { add(metric("hb9cv_rear", it)) }
            front?.let { add(metric("hb9cv_front", it)) }
        }
        val outputs = buildList {
            add(SnapshotValue(HB9CV_AERIAL_WAVE, calculated.wavelengthMeters, SnapshotUnit.METERS))
            for (dimension in Dimension.entries) add(metric(dimension.key, calculated.millimeters(dimension)))
            add(SnapshotValue(HB9CV_AERIAL_CAPACITANCE, calculated.capacitancePf, SnapshotUnit.PICOFARADS))
            boom?.let {
                add(metric("hb9cv_boom", it.lengthMm))
                add(metric("hb9cv_aerial_position_rear", it.rearPositionMm))
                add(metric("hb9cv_aerial_position_front", it.frontPositionMm))
            }
        }
        onSave(CalculationSnapshot(Hb9cvAerial.MODEL, 1, inputs, outputs), report())
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("hb9cv-aerial-save")) {
        Text(stringResource(R.string.jpole_copyProject))
    }
    OutlinedButton(onClick = {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(name, report()))
        onCopied()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("hb9cv-aerial-copy")) {
        Text(stringResource(R.string.yagi_copy))
    }

    TextButton(onClick = { formulasOpen = !formulasOpen }) {
        Text(stringResource(R.string.hb9cv_formulas) + if (formulasOpen) "  ▴" else "  ▾")
    }
    if (formulasOpen) {
        SelectableText(stringResource(R.string.hb9cv_aerial_formulas))
        Column {
            for (dimension in Dimension.entries) {
                AerialRow(stringResource(dimension.labelResource()), millimeters(dimension.referenceMm))
            }
            AerialRow(capacitanceLabel, "16 $pf")
        }
    }
    TextButton(onClick = { tuningOpen = !tuningOpen }, modifier = Modifier.testTag("hb9cv-aerial-tuning")) {
        Text(stringResource(R.string.hb9cv_aerial_tuning) + if (tuningOpen) "  ▴" else "  ▾")
    }
    if (tuningOpen) {
        Text(stringResource(R.string.hb9cv_aerial_tuning_pattern))
        Text(stringResource(R.string.hb9cv_aerial_tuning_swr))
        onMeasurements?.let { open ->
            TextButton(onClick = open, modifier = Modifier.testTag("hb9cv-aerial-vna")) {
                Text(stringResource(R.string.hb9cv_aerial_s1p))
            }
        }
    }
    HorizontalDivider()
    Text(stringResource(R.string.hb9cv_aerial_sources), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.hb9cv_aerial_graphs_note), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { openSourceWithFeedback(context, source) }) {
        Text(stringResource(R.string.hb9cv_work_aerial))
    }
}

private fun valueIn(raw: String, min: Double, max: Double): Double? =
    (PositiveDecimalParser.parse(raw, allowZero = min == 0.0) as? FieldState.Valid)?.value
        ?.toDouble()?.takeIf { it in min..max }

@Composable
private fun AerialInput(raw: String, onEdit: (String) -> Unit, label: Int, hint: Int, invalid: Boolean, tag: String) {
    OutlinedTextField(
        value = raw, onValueChange = { if (it.acceptsDecimalEdit(limit = 16)) onEdit(it) },
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = true, isError = invalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text(stringResource(hint)) },
    )
}

@Composable
private fun AerialRow(label: String, value: String?) {
    SelectionContainer {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value ?: "—", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    HorizontalDivider()
}

@Composable
private fun AerialDiagram() {
    val measurer = rememberTextMeasurer()
    val description = stringResource(R.string.hb9cv_aerial_diagram_description)
    val metal = MaterialTheme.colorScheme.secondary
    val phase = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = description }) {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: androidx.compose.ui.graphics.Color, width: Float) {
            drawLine(color, point(x1, y1), point(x2, y2), width.dp.toPx(), StrokeCap.Round)
        }
        fun mark(label: String, x: Float, y: Float, color: androidx.compose.ui.graphics.Color) {
            val layout = measurer.measure(label, TextStyle(fontSize = 12.sp))
            drawText(layout, color, topLeft = point(x, y) - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        line(.5f, .26f, .5f, .64f, metal, 5f)
        line(.085f, .26f, .915f, .26f, metal, 3f)
        line(.13f, .64f, .87f, .64f, metal, 3f)
        line(.35f, .26f, .35f, .19f, phase, 2f)
        line(.35f, .19f, .5f, .19f, phase, 2f)
        line(.5f, .19f, .5f, .57f, phase, 2f)
        line(.5f, .57f, .645f, .57f, phase, 2f)
        line(.645f, .57f, .645f, .64f, phase, 2f)
        drawCircle(phase, 4.dp.toPx(), point(.5f, .57f))
        line(.91f, .26f, .91f, .64f, metal, 1f)
        mark("LR", .18f, .14f, metal)
        mark("LF", .2f, .75f, metal)
        mark("MR", .415f, .1f, phase)
        mark("MF", .59f, .48f, phase)
        mark("C", .45f, .54f, phase)
        mark("A", .94f, .46f, metal)
        val outer = point(.43f, .9f)
        val inner = point(.64f, .9f)
        drawCircle(metal, 12.dp.toPx(), outer, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawCircle(phase, 6.dp.toPx(), inner, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawLine(metal, outer + Offset(12.dp.toPx(), 0f), inner - Offset(6.dp.toPx(), 0f), 1.dp.toPx())
        mark("D", .34f, .9f, metal)
        mark("h", .55f, .81f, metal)
        mark("d", .72f, .9f, phase)
    }
}
