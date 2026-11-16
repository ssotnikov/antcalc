package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat

private class Hb9cvProfile(val number: Int, val data: JSONObject, val russian: Boolean) {
    fun local(ru: String, en: String) = if (russian) ru else en
    fun text(key: String) = data.getString(if (russian) key else key + "En")
    fun size(key: String) = data.optDouble(key).takeIf { it.isFinite() }
    fun rows(key: String) = data.optJSONArray(key)?.let { rows ->
        List(rows.length()) { index ->
            val item = rows.getJSONArray(index)
            val offset = if (russian) 0 else 2
            item.getString(offset) to item.getString(offset + 1)
        }
    }.orEmpty()

    val id get() = data.getString("id")
    val boom get() = size("boom") ?: listOf(780, 260, 800, 1350, 1286, 300, 300, 111).getOrNull(number)?.toDouble()
    val extension get() = number >= 14
}

@Composable
internal fun Hb9cvScreen(
    onSave: ((CalculationSnapshot, String) -> Unit)?,
    onCopied: () -> Unit,
    onMeasurements: (() -> Unit)?,
) {
    val context = LocalContext.current
    val locale = LocalResources.current.configuration.locales[0]
    val profiles = remember(context.assets, locale) {
        context.assets.open("antennas/hb9cv-catalog.json").bufferedReader().use {
            val data = JSONArray(it.readText())
            List(data.length()) { index -> Hb9cvProfile(index, data.getJSONObject(index), locale.language == "ru") }
        }
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val profile = profiles.getOrNull(selected)
    val name = profile?.text("name") ?: stringResource(R.string.hb9cv_aerial_name)
    val number = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; isGroupingUsed = false } }
    val millimeterUnit = stringResource(R.string.hb9cv_aerial_mm)
    val mm: (Double) -> String = { number.format(it) + " " + millimeterUnit }
    val state = rememberSaveableStateHolder()
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
        .padding(20.dp).testTag("hb9cv-screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val geometry = remember(selected) { profile?.takeIf { it.extension }?.let(::hb9cvExtensionVector) }
        RotatingAntenna(if (profile == null) "hb-aerial" else "hb-$selected", name, geometry = geometry)
        if (profile != null) Text(profile.text("description"), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.yagi_profile), style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("hb9cv-profile")) {
                Text(name)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hb9cv_aerial_name)) },
                    onClick = { selected = -1; expanded = false },
                    modifier = Modifier.testTag("hb9cv-aerial-profile"),
                )
                profiles.forEach { item ->
                    DropdownMenuItem(text = { Text(item.text("name")) }, onClick = { selected = item.number; expanded = false })
                }
            }
        }
        state.SaveableStateProvider(selected) {
            if (profile == null) Hb9cvAerialDetails(onSave, onCopied, onMeasurements)
            else Hb9cvDetails(profile, mm, onSave, onCopied)
        }
    }
}

@Composable
private fun Hb9cvDetails(profile: Hb9cvProfile, mm: (Double) -> String,
    onSave: ((CalculationSnapshot, String) -> Unit)?, onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val name = profile.text("name")
    val uw = profile.id == "uw3bj"
    val portable = profile.id == "dow-portable"
    val rows = profile.rows("cuts").filterIndexed { index, _ -> profile.id != "u5mh" || index == 0 }.toMutableList()
    val inputs = mutableListOf<SnapshotValue>()
    val outputs = mutableListOf<SnapshotValue>()
    val missing = mutableListOf<String>()
    var sectional by rememberSaveable { mutableStateOf(false) }
    var formula by rememberSaveable { mutableStateOf(false) }
    var formulasOpen by rememberSaveable { mutableStateOf(false) }
    var customLine by rememberSaveable { mutableStateOf(false) }
    val r = profile.size("r")
    val s = profile.size("s")
    val spacing = profile.size("spacing")
    fun metric(key: String, value: Double) = SnapshotValue(key, value / 1000, SnapshotUnit.METERS)
    fun add(key: String, label: String, value: Double) {
        rows += label to mm(value)
        outputs += metric(key, value)
    }
    @Composable fun input(key: String, label: String, min: Double, max: Double, unit: SnapshotUnit? = SnapshotUnit.METERS): Double? {
        val (value, valid) = hb9cvInput(key, label, min, max)
        if (!valid) missing += label
        if (value != null && unit != null) inputs += if (unit == SnapshotUnit.METERS) metric(key, value) else SnapshotValue(key, value, unit)
        return value
    }

    Text(stringResource(R.string.hb9cv_diagram), style = MaterialTheme.typography.titleMedium)
    Hb9cvDiagram(profile, sectional)
    Text(stringResource(if (profile.size("rTap") != null) R.string.hb9cv_symbols_taps else R.string.hb9cv_symbols),
        style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.hb9cv_published), style = MaterialTheme.typography.titleMedium)
    val dimensions = mutableListOf<Pair<String, String>>()
    listOf("r" to "R", "s" to "S", "spacing" to "A", "rTap" to "RS", "sTap" to "SS", "gap" to "h").forEach { (key,label) ->
        profile.size(key)?.let { value ->
            dimensions += label to mm(value)
            outputs += metric("hb9cv_${key.lowercase()}", value)
        }
    }
    if (profile.id == "pd0ddr") dimensions += "h" to profile.local("15–20 мм", "15–20 mm")
    dimensions += profile.rows("dimensions")
    dimensions.forEach { (label,value) -> SelectableText("$label  $value") }
    val target = profile.size("f") ?: profile.size("suggestedFrequency")
    val targetText = target?.let {
        val locale = LocalResources.current.configuration.locales[0]
        "${NumberFormat.getNumberInstance(locale).format(it)} ${stringResource(R.string.mhz)}"
    }
    if (target != null && targetText != null) {
        val label = stringResource(if (profile.size("f") == null) R.string.hb9cv_nominal_frequency else R.string.hb9cv_published_frequency)
        SelectableText("$label · $targetText")
        inputs += SnapshotValue("frequency", target * 1e6, SnapshotUnit.HERTZ)
    }
    profile.boom?.let { outputs += metric("hb9cv_boom", it) }

    if (portable) {
        input("hb9cv_tip", stringResource(R.string.hb9cv_tip), 0.0, 50.0)?.let { tip ->
            add("hb9cv_cut_r", profile.local("Рез каждой штанги R", "Cut length of each R rod"), 505.5 - tip)
            add("hb9cv_cut_s", profile.local("Рез каждой штанги S", "Cut length of each S rod"), 465.5 - tip)
        }
    }
    if ((uw || profile.id == "radio") && spacing != null && r != null && s != null) {
        Text(stringResource(R.string.hb9cv_custom), style = MaterialTheme.typography.titleMedium)
        val rear = input("hb9cv_rear", stringResource(R.string.hb9cv_rear), 0.0, spacing)
        val front = input("hb9cv_front", stringResource(R.string.hb9cv_front), 0.0, spacing)
        val boomWidth = input("hb9cv_boom_width", stringResource(R.string.hb9cv_boom_width), if (uw) 40.0 else 1.0, if (uw) 45.0 else 150.0)
        if (rear != null && front != null) {
            add("hb9cv_boom", stringResource(R.string.hb9cv_boom), rear + spacing + front)
            add("hb9cv_position_r", stringResource(R.string.hb9cv_position_r), rear)
            add("hb9cv_position_s", stringResource(R.string.hb9cv_position_s), rear + spacing)
        }
        if (boomWidth != null) rows += stringResource(R.string.hb9cv_boom_width) to mm(boomWidth)
        if (uw) {
            rows += listOf(
                profile.local("Центральные трубы Ø22 × 1 мм", "Center tubes Ø22 × 1 mm") to "2 × ${mm(3500.0)}",
                profile.local("Промежуточные трубы Ø20 × 1 мм", "Intermediate tubes Ø20 × 1 mm") to "4 × ${mm(2500.0)}",
                profile.local("Концы R, Ø18 × 1 мм", "R ends, Ø18 × 1 mm") to "2 × ${mm(2000.0)}",
                profile.local("Концы S, Ø18 × 1 мм", "S ends, Ø18 × 1 mm") to "2 × ${mm(1800.0)}",
            )
            for ((role, end) in listOf("R" to 2000.0, "S" to 1800.0)) {
                val key = role.lowercase()
                val inner = input("hb9cv_${key}_inner", profile.local("$role · посадка средней трубы, мм", "$role · intermediate tube insertion, mm"), .1, 1750.0)
                val outer = input("hb9cv_${key}_outer", profile.local("$role · посадка концевой трубы, мм", "$role · end tube insertion, mm"), .1, minOf(end,2500 - (inner ?: 0.0)))
                if (inner != null && outer != null) add("hb9cv_${key}_assembled", profile.local("Размах труб $role без наконечников", "$role span without end fittings"), 3500 + 2 * (2500 + end - inner - outer))
            }
            ChoicePicker(stringResource(R.string.hb9cv_phasing), listOf(false,true), formula,
                { stringResource(if (it) R.string.hb9cv_formula_mode else R.string.hb9cv_manual_mode) }, { formula = it }, "hb9cv-phasing-mode")
            if (formula) {
                val vf = input("hb9cv_vf", "VF", .01, 1.0, SnapshotUnit.FACTOR)
                val allowance = input("hb9cv_allowance", stringResource(R.string.hb9cv_allowance), 100.0, 200.0)
                if (vf != null && allowance != null) {
                    val difference = 299800 / checkNotNull(profile.size("f")) * vf / 8
                    add("hb9cv_branch_r", stringResource(R.string.hb9cv_branch_r), (spacing + 2 * allowance + difference) / 2)
                    add("hb9cv_branch_s", stringResource(R.string.hb9cv_branch_s), (spacing + 2 * allowance - difference) / 2)
                }
            } else {
                input("hb9cv_rear_cable", stringResource(R.string.hb9cv_branch_r), .1, 10000.0)?.let { add("hb9cv_branch_r", stringResource(R.string.hb9cv_branch_r), it) }
                input("hb9cv_front_cable", stringResource(R.string.hb9cv_branch_s), .1, 10000.0)?.let { add("hb9cv_branch_s", stringResource(R.string.hb9cv_branch_s), it) }
            }
        } else {
            ChoicePicker(stringResource(R.string.hb9cv_elements), listOf(false,true), sectional,
                { stringResource(if (it) R.string.hb9cv_sectional else R.string.hb9cv_single) }, { sectional = it }, "hb9cv-elements-mode")
            val diameter = input("hb9cv_diameter", stringResource(R.string.hb9cv_diameter), .1, 100.0)
            if (diameter != null) rows += stringResource(R.string.hb9cv_diameter) to mm(diameter)
            if (sectional) {
                val wall = input("hb9cv_wall", stringResource(R.string.hb9cv_wall), .1, (diameter?.div(2)?.minus(.1))?.coerceIn(.1, 50.0) ?: 50.0)
                val clearance = if (wall != null && diameter != null) diameter - 2 * wall else null
                if (clearance != null && clearance < .1) missing += stringResource(R.string.hb9cv_wall)
                val tipDiameter = input("hb9cv_tip_diameter", stringResource(R.string.hb9cv_tip_diameter), .1, clearance?.coerceAtLeast(.1) ?: 100.0)
                if (wall != null) rows += stringResource(R.string.hb9cv_wall) to mm(wall)
                if (tipDiameter != null) rows += stringResource(R.string.hb9cv_tip_diameter) to mm(tipDiameter)
                val center = input("hb9cv_center", stringResource(R.string.hb9cv_center), .1, s - .1)
                val overlap = input("hb9cv_overlap", stringResource(R.string.hb9cv_overlap), .1, center?.div(2)?.coerceAtLeast(.1) ?: s / 2)
                if (center != null && overlap != null) {
                    add("hb9cv_center_cut", stringResource(R.string.hb9cv_center_pair), center)
                    add("hb9cv_cut_r", stringResource(R.string.hb9cv_cut_r), (r - center) / 2 + overlap)
                    add("hb9cv_cut_s", stringResource(R.string.hb9cv_cut_s), (s - center) / 2 + overlap)
                }
            } else {
                rows += "R / S" to "${mm(r)} / ${mm(s)}"
            }
            val line = checkNotNull(profile.size("rear"))
            val impedance = input("hb9cv_impedance", stringResource(R.string.hb9cv_impedance), 1.0, 600.0, unit = null)
            if (impedance != null) rows += stringResource(R.string.hb9cv_impedance) to "${impedance.toString().removeSuffix(".0")} ${profile.local("Ом", "Ω")}"
            add("hb9cv_branch_r", stringResource(R.string.hb9cv_branch_r), line)
            add("hb9cv_branch_s", stringResource(R.string.hb9cv_branch_s), checkNotNull(profile.size("e")) - line)
        }
    }
    if (profile.extension && r != null && s != null) {
        Text(stringResource(R.string.hb9cv_custom), style = MaterialTheme.typography.titleMedium)
        val rearLabel = stringResource(R.string.hb9cv_rear)
        val frontLabel = stringResource(R.string.hb9cv_front)
        val boomLabel = stringResource(R.string.hb9cv_boom)
        val positionR = stringResource(R.string.hb9cv_position_r)
        val positionS = stringResource(R.string.hb9cv_position_s)
        val branchR = stringResource(R.string.hb9cv_branch_r)
        val branchS = stringResource(R.string.hb9cv_branch_s)
        val cutR = stringResource(R.string.hb9cv_cut_r)
        val cutS = stringResource(R.string.hb9cv_cut_s)

        @Composable fun addBoom(axisSpacing: Double) {
            val rear = input("hb9cv_rear", rearLabel, 0.0, axisSpacing)
            val front = input("hb9cv_front", frontLabel, 0.0, axisSpacing)
            if (rear != null && front != null) {
                add("hb9cv_boom", boomLabel, rear + axisSpacing + front)
                add("hb9cv_position_r", positionR, rear)
                add("hb9cv_position_s", positionS, rear + axisSpacing)
            }
        }

        @Composable fun branches() {
            input("hb9cv_rear_cable", branchR, .1, 20000.0)?.let { add("hb9cv_branch_r", branchR, it) }
            input("hb9cv_front_cable", branchS, .1, 20000.0)?.let { add("hb9cv_branch_s", branchS, it) }
        }

        when (profile.id) {
            "f1lvt" -> {
                val a = input("hb9cv_hinge_r", stringResource(R.string.hb9cv_hinge_r), .1, r - .1)
                val b = input("hb9cv_hinge_s", stringResource(R.string.hb9cv_hinge_s), .1, s - .1)
                val tail = input("hb9cv_hinge_margin", stringResource(R.string.hb9cv_hinge_margin), 0.0, 100.0)
                if (tail != null) {
                    a?.let { add("hb9cv_cut_r", "2 × $cutR", (r - it) / 2 + tail) }
                    b?.let { add("hb9cv_cut_s", "2 × $cutS", (s - it) / 2 + tail) }
                }
                TextButton(onClick = { openSourceWithFeedback(context, Hb9cvSource.LVT_PCB.url) }) {
                    Text(stringResource(R.string.hb9cv_work_lvt_pcb))
                }
            }
            "jo2asq" -> {
                addBoom(checkNotNull(spacing))
                val line = stringResource(R.string.hb9cv_jo_line)
                input("hb9cv_line", line, checkNotNull(spacing), 10000.0)?.let { add("hb9cv_line_length", line, it) }
            }
            "u5mh" -> {
                val inner = input("hb9cv_u5_inner", stringResource(R.string.hb9cv_u5_inner), .1, 1750.0)
                val outer = input("hb9cv_u5_outer", stringResource(R.string.hb9cv_u5_outer), .1, 1000.0)
                if (inner != null && outer != null) {
                    add("hb9cv_cut_r", "2 × $cutR · Ø16 mm", (r - 3500) / 2 - 900 + inner + outer)
                    add("hb9cv_cut_s", "2 × $cutS · Ø16 mm", (s - 3500) / 2 - 500 + inner + outer)
                    add("hb9cv_strip_r", profile.local("Шина R · 2 шт.", "R strip · 2 pieces"), 900 + outer)
                    add("hb9cv_strip_s", profile.local("Шина S · 2 шт.", "S strip · 2 pieces"), 500 + outer)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(customLine, { customLine = it })
                    Text(stringResource(R.string.hb9cv_u5_custom_line))
                }
                if (customLine) branches() else {
                    val cable = profile.local("РК-75 · VF 0,67", "RK-75 · VF 0.67")
                    add("hb9cv_branch_r", "$branchR · $cable", 2280.0)
                    add("hb9cv_branch_s", "$branchS · $cable", 500.0)
                }
            }
            "uw3bj-other", "pa0tax" -> {
                if (spacing != null) {
                    addBoom(spacing)
                    ChoicePicker(stringResource(R.string.hb9cv_phasing), listOf(false, true), formula,
                        { stringResource(if (it) R.string.hb9cv_formula_mode else R.string.hb9cv_manual_mode) },
                        { formula = it }, "hb9cv-phasing-mode")
                    if (!formula) branches() else {
                        val vf = input("hb9cv_vf", "VF", .01, 1.0, SnapshotUnit.FACTOR)
                        val allowance = input("hb9cv_allowance", stringResource(R.string.hb9cv_allowance), 100.0, 200.0)
                        if (vf != null && allowance != null && target != null) {
                            val difference = 299800 / target * vf / 8
                            val total = spacing + 2 * allowance
                            add("hb9cv_branch_r", branchR, (total + difference) / 2)
                            add("hb9cv_branch_s", branchS, (total - difference) / 2)
                        }
                    }
                } else {
                    val boom = checkNotNull(profile.boom)
                    val axes = input("hb9cv_spacing", stringResource(R.string.hb9cv_tax_spacing), .1, boom)
                    if (axes != null) {
                        val rear = input("hb9cv_rear", stringResource(R.string.hb9cv_tax_trim), 0.0, boom - axes)
                        if (rear != null) {
                            add("hb9cv_position_r", positionR, rear)
                            add("hb9cv_position_s", positionS, rear + axes)
                        }
                    }
                }
                ChoicePicker(stringResource(R.string.hb9cv_elements), listOf(false, true), sectional,
                    { stringResource(if (it) R.string.hb9cv_sectional else R.string.hb9cv_single) },
                    { sectional = it }, "hb9cv-elements-mode")
                val diameter = input("hb9cv_diameter", stringResource(R.string.hb9cv_diameter), .3, 150.0)
                if (!sectional) {
                    rows += "R / S" to "${mm(r)} / ${mm(s)}"
                } else {
                    val wall = input("hb9cv_wall", stringResource(R.string.hb9cv_wall), .1, (diameter?.div(2)?.minus(.05) ?: 50.0).coerceAtLeast(.1))
                    val bore = diameter?.let { d -> wall?.let { d - 2 * it } }
                    if (bore != null && bore < .1) missing += stringResource(R.string.hb9cv_wall)
                    input("hb9cv_tip_diameter", stringResource(R.string.hb9cv_tip_diameter), .1, bore?.coerceAtLeast(.1) ?: 150.0)
                    val center = input("hb9cv_center", stringResource(R.string.hb9cv_center), .1, s - .1)
                    val overlap = center?.let { input("hb9cv_overlap", stringResource(R.string.hb9cv_overlap), .1, it / 2) }
                    if (center != null && overlap != null) {
                        add("hb9cv_center_cut", stringResource(R.string.hb9cv_center_pair), center)
                        add("hb9cv_cut_r", "2 × $cutR", (r - center) / 2 + overlap)
                        add("hb9cv_cut_s", "2 × $cutS", (s - center) / 2 + overlap)
                    }
                }
            }
        }
    }
    if (inputs.isEmpty()) (outputs.firstOrNull()?.let { inputs += it.copy(key = "hb9cv_reference") })
    val cuts = stringResource(R.string.yagi_cuts)
    val link = profile.data.getString("url")
    val report = buildString {
        appendLine(name)
        targetText?.let { appendLine(it) }
        dimensions.forEach { (label,value) -> appendLine("$label  $value") }
        rows.forEach { (label,value) -> appendLine("$label  $value") }
        if (missing.isNotEmpty()) appendLine(missing.joinToString())
        append(link)
    }
    Text(cuts, style = MaterialTheme.typography.titleMedium)
    rows.forEach { (label,value) -> SelectableText("$label  $value") }
    if (missing.isNotEmpty()) Text(stringResource(R.string.hb9cv_missing, missing.joinToString()), color = MaterialTheme.colorScheme.error)
    if (onSave != null) Button(onClick = {
        onSave(CalculationSnapshot("hb9cv_${profile.number}", 1, inputs.toList(), outputs.toList()), report)
    }, enabled = missing.isEmpty() && outputs.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("hb9cv-save-profile")) {
        Text(stringResource(R.string.jpole_copyProject))
    }
    OutlinedButton(onClick = {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(name, report))
        onCopied()
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.yagi_copy)) }
    TextButton(onClick = { openSourceWithFeedback(context, link) }) { Text(stringResource(R.string.hb9cv_source)) }
    TextButton(onClick = { formulasOpen = !formulasOpen }) {
        Text(stringResource(R.string.hb9cv_formulas) + if (formulasOpen) "  ▴" else "  ▾")
    }
    if (formulasOpen) {
        SelectableText(stringResource(R.string.hb9cv_wavelength))
        when {
            uw -> SelectableText(stringResource(R.string.hb9cv_uw_formula))
            profile.id == "radio" -> SelectableText(stringResource(R.string.hb9cv_radio_formula))
            profile.id == "dk7zb" -> SelectableText(stringResource(R.string.hb9cv_dk_formula))
            portable -> SelectableText(stringResource(R.string.hb9cv_portable_formula))
            profile.id == "pd0ddr" -> SelectableText(stringResource(R.string.hb9cv_pd_formula))
            profile.id == "f1lvt" -> SelectableText(stringResource(R.string.hb9cv_f1_formula))
            profile.id == "u5mh" -> SelectableText(stringResource(R.string.hb9cv_u5_formula))
            profile.id == "uw3bj-other" -> SelectableText(stringResource(R.string.hb9cv_piecewise_formula))
            profile.id == "pa0tax" -> SelectableText(stringResource(R.string.hb9cv_tax_formula))
            else -> SelectableText(stringResource(R.string.hb9cv_fixed_formula))
        }
        if (profile.id == "uw3bj-other") SelectableText(stringResource(R.string.hb9cv_uw_formula))
    }
}

private fun hb9cvExtensionVector(profile: Hb9cvProfile): AntennaVector {
    val rearHalf = 140f
    val frontHalf = rearHalf * (checkNotNull(profile.size("s")) / checkNotNull(profile.size("r"))).toFloat()
    val separation = (280 * (profile.size("spacing") ?: checkNotNull(profile.boom)) / checkNotNull(profile.size("r"))).toFloat()
    val folding = profile.id == "f1lvt"
    val light = profile.id == "jo2asq"
    fun p(x: Float, y: Float, z: Float = 0f) = AntennaPoint(x, y, z)
    val rods = mutableListOf<AntennaRod>()
    val joints = mutableListOf<AntennaJoint>()
    fun rod(a: AntennaPoint, b: AntennaPoint, diameter: Float, material: Int = 0) {
        rods += AntennaRod(a, b, diameter, material)
    }
    rod(p(0f, -8f), p(0f, separation + 8f), if (folding || light) 11f else 7f, if (folding || light) 2 else 0)
    for ((y, half) in listOf(0f to rearHalf, separation to frontHalf)) {
        if (folding) rod(p(-42f, y), p(42f, y), 10f, 2)
        for (sign in listOf(-1f, 1f)) {
            val joint = sign * if (folding) 40f else half * .43f
            if (!folding) rod(p(0f, y), p(joint, y), 5f)
            rod(p(joint, y), p(half * sign, y), if (light || folding) 2.6f else 3.8f)
            joints += AntennaJoint(p(joint, y), if (folding) 9f else 5.5f, if (folding) 2 else 0)
        }
    }
    val phasePoints = listOf(p(-rearHalf * .3f, 0f, 6f), p(-rearHalf * .3f, 5f, 6f), p(0f, 5f, 6f),
        p(0f, separation - 5f, 6f), p(frontHalf * .3f, separation - 5f, 6f), p(frontHalf * .3f, separation, 6f))
    phasePoints.zipWithNext { a, b -> rod(a, b, 2.2f, 2) }
    if (profile.id == "u5mh") {
        for (sign in listOf(-1f, 1f)) {
            rod(p(sign * 93f, 0f), p(sign * 93f, separation), 3f, 2)
            rod(p(sign * 93f, 0f), p(0f, separation / 2, 48f), 1.2f)
            rod(p(sign * 93f, separation), p(0f, separation / 2, 48f), 1.2f)
        }
    }
    return AntennaVector(rods, joints)
}

@Composable
private fun hb9cvInput(key: String, label: String, min: Double, max: Double): Pair<Double?, Boolean> {
    var raw by rememberSaveable(key) { mutableStateOf("") }
    val decimal = (PositiveDecimalParser.parse(raw, allowZero = true) as? FieldState.Valid)?.value?.toDouble()
    val value = decimal?.takeIf { it in min..max }
    OutlinedTextField(raw, { if (it.acceptsDecimalEdit(limit = 16)) raw = it }, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag(key), singleLine = true, isError = raw.isNotBlank() && value == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text("$min – $max") })
    return value to (value != null)
}

@Composable
private fun Hb9cvDiagram(profile: Hb9cvProfile, sectional: Boolean) {
    val measure = rememberTextMeasurer()
    val description = stringResource(R.string.hb9cv_diagram)
    val metal = MaterialTheme.colorScheme.secondary
    val phase = MaterialTheme.colorScheme.tertiary
    val rails = profile.id.startsWith("dow")
    Canvas(Modifier.fillMaxWidth().height(175.dp).semantics { contentDescription = description }) {
        val x = center.x
        val yR = size.height * .25f
        val yS = size.height * .73f
        val half = size.width * .33f
        val ratio = ((profile.size("s") ?: 90.0) / (profile.size("r") ?: 100.0)).toFloat()
        fun line(a: Offset,b: Offset, isPhase: Boolean = false, width: Float = 3.dp.toPx()) =
            drawLine(if (isPhase) phase else metal, a, b, width, StrokeCap.Round)
        fun mark(text: String, point: Offset, isPhase: Boolean = false) {
            val layout = measure.measure(text, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp))
            drawText(layout, color = if (isPhase) phase else metal, topLeft = point)
        }
        for (shift in if (rails) listOf(-5.dp.toPx(),5.dp.toPx()) else listOf(0f)) {
            line(Offset(x + shift,yR - 7.dp.toPx()), Offset(x + shift,yS + 7.dp.toPx()))
        }
        line(Offset(x-half,yR),Offset(x+half,yR))
        line(Offset(x-half*ratio,yS),Offset(x+half*ratio,yS))
        if (sectional || profile.id in listOf("dk7zb","dk2tx","jg0sxc","g4jxx","uw3bj","dow-portable")) {
            for ((row, arm) in listOf(yR to half,yS to half*ratio)) for (side in listOf(-1,1))
                drawCircle(metal, 3.dp.toPx(), Offset(x + side * arm * .42f,row))
        }
        val offset = 9.dp.toPx()
        val tapR = half * (profile.size("rTap")?.let { it * 2 / checkNotNull(profile.size("r")) } ?: .38).toFloat()
        val tapS = half * ratio * (profile.size("sTap")?.let { it * 2 / checkNotNull(profile.size("s")) } ?: .38).toFloat()
        if (!rails) {
            val wire = listOf(Offset(x-tapR,yR),Offset(x-tapR,yR+offset),Offset(x+offset,yR+offset),
                Offset(x+offset,yS-offset),Offset(x+tapS,yS-offset),Offset(x+tapS,yS))
            wire.zipWithNext { a,b -> line(a,b,true,2.dp.toPx()) }
        }
        drawCircle(phase,4.dp.toPx(),Offset(x+offset,yS-offset))
        mark("X", Offset(x+offset*2,yS-offset*3),true)
        mark("R", Offset(x-5.dp.toPx(),yR-25.dp.toPx()))
        mark("S", Offset(x-5.dp.toPx(),yS+8.dp.toPx()))
        mark("A", Offset(x+half+7.dp.toPx(),(yR+yS)/2-8.dp.toPx()))
        if (profile.size("rTap") != null) {
            mark("RS",Offset(x-tapR-20.dp.toPx(),yR+13.dp.toPx()),true)
            mark("SS",Offset(x+tapS+4.dp.toPx(),yS-27.dp.toPx()),true)
        }
    }
}
