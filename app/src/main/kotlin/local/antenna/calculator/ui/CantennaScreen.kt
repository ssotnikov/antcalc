package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.antenna.calculator.R
import local.antenna.calculator.core.Cantenna
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue

private enum class CantennaMode(val label: Int) {
    CHECK(R.string.cantenna_check), DIAMETER(R.string.cantenna_select),
    COMPARE(R.string.cantenna_compare), DESIGNS(R.string.cantenna_profiles),
}

@Composable
internal fun CantennaScreen(
    onMeasurements: (() -> Unit)?,
    onSave: ((CalculationSnapshot, String) -> Unit)?,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember(context) { context.getSharedPreferences("cantenna_calculator", Context.MODE_PRIVATE) }
    var draft by remember(prefs) { mutableStateOf(CantennaDraft.restore(prefs.getString("draft", null))) }
    SideEffect {
        val data = draft.json()
        if (prefs.getString("draft", null) != data) prefs.edit().putString("draft", data).apply()
    }
    var mode by rememberSaveable { mutableStateOf(CantennaMode.CHECK) }
    var design by rememberSaveable { mutableStateOf(CantennaDesign.MIT) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var chosenDiameter by rememberSaveable { mutableStateOf("") }
    var focal by rememberSaveable { mutableIntStateOf(2) }
    var gain by rememberSaveable { mutableStateOf(false) }
    val index = selected.coerceIn(draft.cans.indices)
    val candidate = draft.cans[index]
    val profile = design.takeIf { mode == CantennaMode.DESIGNS }
    val hz = profile?.let { it.mhz * 1e6 } ?: draft.hertz
    val band = when {
        profile != null -> Cantenna.Band(profile.mhz * 1e6)
        mode == CantennaMode.DIAMETER -> draft.diameterBand()
        else -> draft.band()
    }
    val diameterRange = if (mode == CantennaMode.DIAMETER) band?.let(Cantenna::diameterRange) else null
    val geometry = profile?.can ?: candidate.geometry()
    val result = if (hz != null && band != null && geometry != null) Cantenna.analyze(geometry, hz, band) else null
    val ring = if (profile == CantennaDesign.VE4MA) Cantenna.ringPosition(focal, gain) else null
    val throat = if (profile == CantennaDesign.HORN) cantennaLength(draft.throat) else null
    val mouth = if (profile == CantennaDesign.HORN) cantennaLength(draft.mouth) else null
    val flareLength = if (profile == CantennaDesign.HORN) cantennaLength(draft.flareLength) else null
    val sector = if (throat != null && mouth != null && flareLength != null && mouth - throat >= MIN_CANTENNA_LENGTH) {
        Cantenna.flare(throat, mouth, flareLength)
    } else null
    val seam = if (draft.seam.isBlank()) null else cantennaLength(draft.seam, true)
    val seamValid = draft.seam.isBlank() || seam != null
    val data = remember(resources.configuration.locales[0]) { CantennaText(resources) }
    fun editCandidate(value: CantennaCandidate) {
        draft = draft.copy(cans = draft.cans.mapIndexed { row, can -> if (row == index) value else can })
    }
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.cantenna_title), text))
        onCopied()
    }
    val scroll = rememberPageScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(scroll).padding(20.dp).testTag("cantenna-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val diameter = geometry?.diameter ?: .085
        val visualDepth = geometry?.depth ?: result?.suggestedDepth ?: .174
        val visualOffset = profile?.offset ?: result?.offset ?: .058
        val visualProbe = profile?.probe ?: result?.probe ?: .031
        val art = remember(diameter, visualDepth, visualProbe, visualOffset, throat, mouth, flareLength, profile, ring) {
            cantennaVector(diameter, visualDepth.coerceAtMost(diameter * 6), visualProbe, visualOffset,
                throat.takeIf { sector != null }, mouth.takeIf { sector != null }, flareLength.takeIf { sector != null },
                ring, profile == CantennaDesign.HORN)
        }
        RotatingAntenna("cantenna-${mode.name}-${profile?.name ?: index}", stringResource(profile?.title ?: R.string.cantenna_title),
            geometry = art)
        CantennaPicker(stringResource(R.string.cantenna_title), CantennaMode.entries, mode, { stringResource(it.label) }) { mode = it }
        if (profile == null) {
            CantennaFrequency(draft, diameterOnly = mode == CantennaMode.DIAMETER) { draft = it }
        } else {
            CantennaPicker(stringResource(R.string.cantenna_profiles), CantennaDesign.entries, profile,
                { stringResource(it.title) }, modifier = Modifier.testTag("cantenna-profiles")) { design = it }
        }
        if (mode == CantennaMode.DIAMETER) {
            val range = diameterRange
            if (band == null) HelpText(if (draft.withBand) R.string.cantenna_invalid_edges else R.string.cantenna_invalid)
            else if (range == null) HelpText(R.string.cantenna_interval_none)
            else {
                CantennaHeading(R.string.cantenna_strict)
                Text("${data.mm(range.first)} < D < ${data.mm(range.second)}")
                val midpoint = (range.first + range.second) / 2
                val diameterText = chosenDiameter.ifBlank { (midpoint * 1000).toBigDecimal()
                    .setScale(9, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }
                val chosen = cantennaLength(diameterText)?.takeIf { it > range.first && it < range.second }
                CantennaField(chosenDiameter, R.string.cantenna_chosen_d, chosen == null,
                    placeholder = data.mm(midpoint)) { chosenDiameter = it }
                if (chosen != null) {
                    val analysis = Cantenna.analyze(Cantenna.Can(chosen), band.lowHz, band)
                    CantennaRows(data.marginRows(analysis))
                } else HelpText(R.string.cantenna_d_outside)
                Button(onClick = {
                    val target = draft.hertz
                    val nextTarget = if (target != null && target in band.lowHz..band.highHz) {
                        draft.target
                    } else ((band.lowHz + band.highHz) / 2e6).toBigDecimal()
                        .setScale(9, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    draft = draft.copy(target = nextTarget, cans = draft.cans.mapIndexed { row, can ->
                        if (row == index) can.copy(diameter = diameterText) else can
                    })
                    mode = CantennaMode.CHECK
                }, enabled = chosen != null, modifier = Modifier.testTag("cantenna-transfer-diameter")) {
                    Text(stringResource(R.string.cantenna_take_d))
                }
            }
        } else {
            if (profile == null) {
                if (mode == CantennaMode.COMPARE || draft.cans.size > 1) {
                    CantennaPicker(stringResource(R.string.cantenna_choose), draft.cans.indices.toList(), index,
                        { data.name(draft.cans[it], it) }) { selected = it }
                }
                if (mode == CantennaMode.COMPARE) {
                    OutlinedTextField(candidate.name, { editCandidate(candidate.copy(name = it.take(64))) },
                        Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cantenna_name)) }, singleLine = true)
                    OutlinedButton(onClick = {
                        selected = draft.cans.size
                        draft = draft.copy(cans = draft.cans + CantennaCandidate(diameter = "90"))
                    }, enabled = draft.cans.size < MAX_CANS) { Text(stringResource(R.string.cantenna_add)) }
                    TextButton(onClick = {
                        draft = draft.copy(cans = draft.cans.filterIndexed { row, _ -> row != index })
                        selected = index.coerceAtMost(draft.cans.lastIndex)
                    }, enabled = draft.cans.size > 1) { Text(stringResource(R.string.cantenna_remove)) }
                }
                CantennaCanFields(candidate, ::editCandidate)
            } else {
                when (profile) {
                    CantennaDesign.MIT -> HelpText(R.string.cantenna_mit_note)
                    CantennaDesign.VE4MA -> {
                        HelpText(R.string.cantenna_ve_note)
                        CantennaPicker("f/D", Cantenna.focalRatios.indices.toList(), focal,
                            { data.number(Cantenna.focalRatios[it]) }) { focal = it }
                        CantennaPicker(stringResource(R.string.cantenna_ring_p), listOf(false, true), gain,
                            { stringResource(if (it) R.string.cantenna_gain else R.string.cantenna_noise) }) { gain = it }
                    }
                    CantennaDesign.HORN -> {
                        HelpText(R.string.cantenna_horn_note)
                        CantennaField(draft.throat, R.string.cantenna_throat, throat == null) { draft = draft.copy(throat = it) }
                        CantennaField(draft.mouth, R.string.cantenna_mouth, mouth == null) { draft = draft.copy(mouth = it) }
                        CantennaField(draft.flareLength, R.string.cantenna_flare_l, flareLength == null) { draft = draft.copy(flareLength = it) }
                        CantennaField(draft.seam, R.string.cantenna_seam, !seamValid) { draft = draft.copy(seam = it) }
                        CantennaHeading(R.string.cantenna_sector)
                        HelpText(R.string.cantenna_flare_note)
                        if (sector == null) HelpText(R.string.cantenna_flare_invalid)
                        else {
                            CantennaSectorDiagram(sector)
                            CantennaRows(data.sectorRows(sector, seam))
                        }
                    }
                }
            }
            if (geometry != null && result != null && hz != null && band != null) {
                CantennaHeading(R.string.cantenna_diagram)
                CantennaDiagram(diameter, visualDepth, visualOffset, visualProbe,
                    throat.takeIf { sector != null }, mouth.takeIf { sector != null }, ring, profile == CantennaDesign.HORN)
                HelpText(R.string.cantenna_diagram_desc)
                CantennaHeading(R.string.cantenna_dimensions)
                val publishedRows = profile?.let { data.publishedRows(it, ring, focal, gain) }.orEmpty()
                if (publishedRows.isNotEmpty()) {
                    CantennaHeading(R.string.cantenna_metrics)
                    CantennaRows(publishedRows)
                    CantennaHeading(R.string.cantenna_calc)
                }
                val rows = data.resultRows(result, geometry, profile != null)
                CantennaRows(rows)
                val source = profile?.source ?: CantennaSource.CHANGPUAK
                val header = profile?.let { stringResource(it.title) } ?: data.name(candidate, index)
                val developmentRows = if (sector == null) emptyList() else listOf(
                    stringResource(R.string.cantenna_throat) to data.mm(throat),
                    stringResource(R.string.cantenna_mouth) to data.mm(mouth),
                    stringResource(R.string.cantenna_flare_l) to data.mm(flareLength),
                ) + data.sectorRows(sector, seam)
                val report = data.report(header, hz, band, geometry, publishedRows + rows + developmentRows, source)
                val canSave = profile != CantennaDesign.HORN || sector != null && seamValid
                onSave?.let {
                    Button(onClick = {
                        val snapshot = cantennaSnapshot(hz, band, geometry, result, profile, ring, focal, gain, throat, mouth, flareLength, sector, seam)
                        it(snapshot, "$header\n${source.author} · ${source.url}")
                    }, enabled = canSave, modifier = Modifier.fillMaxWidth().testTag("cantenna-save")) {
                        Text(stringResource(R.string.cantenna_save))
                    }
                }
                OutlinedButton(onClick = { copy(report) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cantenna_copy))
                }
            } else HelpText(R.string.cantenna_invalid)
            if (mode == CantennaMode.COMPARE && hz != null && band != null) {
                val table = data.comparison(draft.cans, hz, band)
                ScrollableTable {
                    table.forEachIndexed { index, row ->
                        Row {
                            row.forEach { cell ->
                                Text(cell, Modifier.width(148.dp).padding(8.dp),
                                    style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                OutlinedButton(onClick = { copy(table.joinToString("\n") { it.joinToString("\t") }) }) {
                    Text(stringResource(R.string.cantenna_copy_compare))
                }
            }
        }
        HelpText(R.string.cantenna_geometry_only)
        CantennaFormulas()
        CantennaReferencePanels(
            frequency = if (mode == CantennaMode.DIAMETER && draft.withBand)
                band?.let { (it.lowHz + it.highHz) / 2 } else hz,
            diameter = if (mode == CantennaMode.DIAMETER) {
                if (chosenDiameter.isBlank()) diameterRange?.let { (it.first + it.second) / 2 }
                else cantennaLength(chosenDiameter)
            } else geometry?.diameter,
            band = band,
            format = data,
        )
        onMeasurements?.let { TextButton(onClick = it) { Text(stringResource(R.string.sweep_title)) } }
        CantennaSourceLinks((listOfNotNull(profile?.source) + listOf(CantennaSource.CHANGPUAK, CantennaSource.CERN,
            CantennaSource.GOUGH, CantennaSource.AERIAL, CantennaSource.ELEPAL)).distinct())
    }
}

@Composable
private fun CantennaHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun CantennaRows(rows: List<Pair<String, String>>) {
    rows.forEach { (label, value) -> Text("$label · $value") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CantennaFrequency(draft: CantennaDraft, diameterOnly: Boolean, edit: (CantennaDraft) -> Unit) {
    CantennaHeading(R.string.cantenna_shared)
    if (!diameterOnly || !draft.withBand) {
        CantennaField(draft.target, R.string.frequency, draft.hertz == null, frequency = true) { edit(draft.copy(target = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("1296", "1420.405752", "2437").forEach { preset ->
                TextButton(onClick = { edit(draft.copy(target = preset, withBand = false)) }) {
                    Text(preset)
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(draft.withBand, { edit(draft.copy(withBand = it)) }, Modifier.testTag("cantenna-band"))
        Text(stringResource(R.string.cantenna_band))
    }
    TextButton(onClick = {
        edit(draft.copy(target = "2441.75", withBand = true, lower = "2400", upper = "2483.5"))
    }) { Text(stringResource(R.string.cantenna_wifi_preset)) }
    if (draft.withBand) {
        val invalid = (if (diameterOnly) draft.diameterBand() else draft.band()) == null
        CantennaField(draft.lower, R.string.cantenna_low, invalid, frequency = true) { edit(draft.copy(lower = it)) }
        CantennaField(draft.upper, R.string.cantenna_high, invalid, frequency = true) { edit(draft.copy(upper = it)) }
    }
}

@Composable
private fun CantennaCanFields(can: CantennaCandidate, edit: (CantennaCandidate) -> Unit) {
    val diameter = cantennaLength(can.diameter)
    val tolerance = cantennaLength(can.tolerance, true)
    CantennaField(can.diameter, R.string.cantenna_diameter, diameter == null) { edit(can.copy(diameter = it)) }
    CantennaField(can.depth, R.string.cantenna_depth, can.depth.isNotBlank() && cantennaLength(can.depth) == null) { edit(can.copy(depth = it)) }
    CantennaField(can.tolerance, R.string.cantenna_tolerance, can.tolerance.isNotBlank() &&
        (tolerance == null || diameter != null && diameter - tolerance < MIN_CANTENNA_LENGTH)) { edit(can.copy(tolerance = it)) }
    CantennaField(can.contact, R.string.cantenna_contact, can.contact.isNotBlank() && cantennaLength(can.contact, true) == null) {
        edit(can.copy(contact = it))
    }
    CantennaField(can.probeDiameter, R.string.cantenna_probe_d, can.probeDiameter.isNotBlank() && cantennaLength(can.probeDiameter) == null) {
        edit(can.copy(probeDiameter = it))
    }
}

@Composable
internal fun CantennaField(raw: String, label: Int, invalid: Boolean, frequency: Boolean = false,
                           showUnit: Boolean = true, placeholder: String? = null, edit: (String) -> Unit) {
    OutlinedTextField(raw, { if (it.acceptsDecimalEdit()) edit(it) }, Modifier.fillMaxWidth().testTag("cantenna-input-$label"),
        label = { Text(stringResource(label)) }, singleLine = true, isError = invalid,
        placeholder = placeholder?.let { example -> { Text(example) } },
        suffix = if (showUnit) {
            { Text(stringResource(if (frequency) R.string.mhz else R.string.hb9cv_aerial_mm)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun <T> CantennaPicker(
    label: String, values: List<T>, current: T, text: @Composable (T) -> String,
    modifier: Modifier = Modifier, select: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, color = MaterialTheme.colorScheme.secondary)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = modifier.fillMaxWidth()) {
            Text(text(current), Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(text = { Text(text(value)) }, onClick = { expanded = false; select(value) })
            }
        }
    }
}

@Composable
private fun CantennaFormulas() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.cantenna_formula) + if (expanded) " ▴" else " ▾") }
    if (expanded) {
        Text("c = 299 792 458 m/s\nfTE₁₁ = 1.841183781 c / (πD)\nfTM₀₁ = 2.404825558 c / (πD)\nλ₀ = c/f\nλg = λ₀ / √(1 − (fTE₁₁/f)²)")
        Text("P = λ₀/4     A = λg/4     L ≈ 3λg/4")
        Text("Dmin = 1.841183781 c / (π fmin)\nDmax = 2.404825558 c / (π fmax)")
        Text("s = √(H² + (r₂ − r₁)²)\nR₁ = s·r₁/(r₂ − r₁)     R₂ = s·r₂/(r₂ − r₁)\nα = 360°·(r₂ − r₁)/s")
    }
}

@Composable
internal fun CantennaSourceLinks(entries: List<CantennaSource> = CantennaSource.entries) {
    val context = LocalContext.current
    CantennaHeading(R.string.cantenna_sources)
    entries.forEach { source ->
        TextButton(onClick = { openSourceWithFeedback(context, source.url) }, Modifier.fillMaxWidth()) {
            Text("${source.author} · ${stringResource(source.title)}", Modifier.fillMaxWidth())
        }
        source.terms?.let { HelpText(it) }
        source.termsUrl?.let { url ->
            TextButton(onClick = { openSourceWithFeedback(context, url) }) {
                Text(stringResource(R.string.cantenna_source_terms, source.author))
            }
        }
    }
}

@Composable
internal fun CantennaDiagram(d: Double, l: Double, a: Double, p: Double, throat: Double?, mouth: Double?, ring: Double?, tuning: Boolean) {
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val guide = MaterialTheme.colorScheme.tertiary
    val textStyle = TextStyle(color = body, fontSize = 13.sp)
    val labels = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = size.width * .08f
        val right = size.width * if (mouth != null || ring != null) .66f else .72f
        val top = size.height * .31f
        val bottom = size.height * .71f
        val axis = left + (right - left) * (a / l).toFloat().coerceIn(0f, 1f)
        val pin = top + (bottom - top) * (p / d).toFloat().coerceIn(.04f, 1f)
        val stroke = 2.dp.toPx()
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(body, Offset(x, y), Offset(x2, y2), stroke)
        fun label(text: String, x: Float, y: Float) =
            drawText(labels.measure(text, textStyle), topLeft = Offset(x, y))
        line(left, top, left, bottom)
        line(left, top, right, top)
        line(left, bottom, right, bottom)
        drawLine(guide, Offset(axis, top - 9.dp.toPx()), Offset(axis, pin), stroke * 2)
        if (tuning) {
            val second = axis + (right - left) * (CantennaDesign.TUNING_PIN_SPACING / l).toFloat()
            val endpoint = top + (bottom - top) * (CantennaDesign.TUNING_PIN_HEIGHT / d).toFloat()
            drawLine(guide, Offset(second, top), Offset(second, endpoint), stroke * 2)
        }
        label("P", axis + 5.dp.toPx(), top)
        line(left, bottom + 5.dp.toPx(), axis, bottom + 5.dp.toPx())
        label("A", (left + axis) / 2, bottom + 4.dp.toPx())
        line(left, top - 12.dp.toPx(), right, top - 12.dp.toPx())
        label("L", (left + right) / 2, top - 26.dp.toPx())
        if (mouth != null && throat != null) {
            val far = size.width * .88f
            val start = ((throat / d - 1) * (bottom - top) / 2).toFloat().coerceIn(-top * .75f, top * .75f)
            val opening = ((mouth / d - 1) * (bottom - top) / 2).toFloat().coerceIn(-top * .75f, top * .75f)
            line(right, top - start, far, top - opening)
            line(right, bottom + start, far, bottom + opening)
            val beam = (top + bottom) / 2
            val tip = size.width * .98f
            line(far + 4.dp.toPx(), beam, tip, beam)
            line(tip - 5.dp.toPx(), beam - 4.dp.toPx(), tip, beam)
            line(tip - 5.dp.toPx(), beam + 4.dp.toPx(), tip, beam)
            label("H", (far + right) / 2, bottom + opening)
        } else if (ring != null) {
            val rear = right - ((right - left) * ring / l).toFloat()
            val far = rear + ((right - left) * CantennaDesign.RING_DEPTH / l).toFloat()
            line(rear, top, rear, top * .58f)
            line(rear, bottom, rear, bottom + top * .42f)
            line(rear, top * .58f, far, top * .58f)
            line(rear, bottom + top * .42f, far, bottom + top * .42f)
        } else {
            val radius = minOf(size.width * .10f, (bottom - top) / 2)
            val center = Offset(size.width * .87f, (top + bottom) / 2)
            drawOval(body, center - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(stroke))
            line(center.x - radius, center.y, center.x + radius, center.y)
            drawLine(guide, Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + radius * (p / d * 2).toFloat().coerceIn(.05f, 2f)), stroke)
            label("D", center.x, center.y + radius)
        }
    }
}

@Composable
private fun CantennaSectorDiagram(sector: Cantenna.Sector) {
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val radius = size.minDimension * .44f
        val inner = (sector.innerRadius / sector.outerRadius).toFloat() * radius
        val angle = sector.angle.toFloat()
        val start = -90f - angle / 2
        val line = 2.dp.toPx()
        fun arc(r: Float) {
            drawArc(color, start, angle, useCenter = false, topLeft = center - Offset(r, r),
                size = Size(2 * r, 2 * r), style = Stroke(line))
        }
        arc(inner)
        arc(radius)
        for (end in listOf(start, start + angle)) {
            val rad = Math.toRadians(end.toDouble())
            val direction = Offset(kotlin.math.cos(rad).toFloat(), kotlin.math.sin(rad).toFloat())
            drawLine(color, center + direction * inner, center + direction * radius, line)
        }
    }
}
