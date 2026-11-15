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
    val hasCuts get() = data.has("cuts")
    val boom get() = listOf(780, 260, 800, 1350, 1286, 300, 300, 111).getOrNull(number)
}

@Composable
internal fun Hb9cvScreen(onSave: ((CalculationSnapshot, String) -> Unit)?, onCopied: () -> Unit) {
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
    val profile = profiles[selected]
    val number = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; isGroupingUsed = false } }
    val mm: (Double) -> String = { number.format(it) + profile.local(" мм", " mm") }
    val state = rememberSaveableStateHolder()
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
        .padding(20.dp).testTag("hb9cv-screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RotatingAntenna("hb-$selected", profile.text("name"))
        Text(profile.text("description"), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.yagi_profile), style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("hb9cv-profile")) {
                Text(profile.text("name"))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                profiles.forEach { item ->
                    DropdownMenuItem(text = { Text(item.text("name")) }, onClick = { selected = item.number; expanded = false })
                }
            }
        }
        state.SaveableStateProvider(selected) {
            Hb9cvDetails(profile, mm, onSave, onCopied)
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
    val rows = profile.rows("cuts").toMutableList()
    val inputs = mutableListOf<SnapshotValue>()
    val outputs = mutableListOf<SnapshotValue>()
    val missing = mutableListOf<String>()
    var sectional by rememberSaveable { mutableStateOf(false) }
    var formula by rememberSaveable { mutableStateOf(false) }
    var formulasOpen by rememberSaveable { mutableStateOf(false) }
    val r = profile.size("r")
    val s = profile.size("s")
    val spacing = profile.size("spacing")
    fun metric(key: String, value: Double) = SnapshotValue(key, value / 1000, SnapshotUnit.METERS)
    fun add(key: String, label: String, value: Double) {
        rows += label to mm(value)
        outputs += metric(key, value)
    }
    @Composable fun input(key: String, label: String, min: Double, max: Double, unit: SnapshotUnit? = SnapshotUnit.METERS): Double? {
        val (value, valid) = Hb9cvInput(key, label, min, max)
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
    dimensions.forEach { (label,value) -> Text("$label  $value") }
    profile.size("f")?.let { inputs += SnapshotValue("frequency", it * 1e6, SnapshotUnit.HERTZ) }
    profile.boom?.let { outputs += metric("hb9cv_boom", it.toDouble()) }

    if (portable) {
        input("hb9cv_tip", stringResource(R.string.hb9cv_tip), 0.0, 50.0)?.let { tip ->
            add("hb9cv_cut_r", profile.local("Рез каждой штанги R", "Cut length of each R rod"), 505.5 - tip)
            add("hb9cv_cut_s", profile.local("Рез каждой штанги S", "Cut length of each S rod"), 465.5 - tip)
        }
    }
    if (!profile.hasCuts && spacing != null && r != null && s != null) {
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
    if (inputs.isEmpty()) (outputs.firstOrNull()?.let { inputs += it.copy(key = "hb9cv_reference") })
    val cuts = stringResource(R.string.yagi_cuts)
    val link = profile.data.getString("url")
    val report = buildString {
        appendLine(name)
        dimensions.forEach { (label,value) -> appendLine("$label  $value") }
        rows.forEach { (label,value) -> appendLine("$label  $value") }
        if (missing.isNotEmpty()) appendLine(missing.joinToString())
        append(link)
    }
    Text(cuts, style = MaterialTheme.typography.titleMedium)
    rows.forEach { (label,value) -> Text("$label  $value") }
    if (missing.isNotEmpty()) Text(stringResource(R.string.hb9cv_missing, missing.joinToString()), color = MaterialTheme.colorScheme.error)
    if (onSave != null) Button(onClick = {
        onSave(CalculationSnapshot("hb9cv_${profile.number}", 1, inputs.toList(), outputs.toList()), report)
    }, enabled = missing.isEmpty() && outputs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.jpole_copyProject)) }
    OutlinedButton(onClick = {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(name, report))
        onCopied()
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.yagi_copy)) }
    TextButton(onClick = { openSourceWithFeedback(context, link) }) { Text(stringResource(R.string.hb9cv_source)) }
    TextButton(onClick = { formulasOpen = !formulasOpen }) {
        Text(stringResource(R.string.hb9cv_formulas) + if (formulasOpen) "  ▴" else "  ▾")
    }
    if (formulasOpen) {
        Text(stringResource(R.string.hb9cv_wavelength))
        when {
            uw -> Text(stringResource(R.string.hb9cv_uw_formula))
            profile.id == "radio" -> Text(stringResource(R.string.hb9cv_radio_formula))
            profile.id == "dk7zb" -> Text(stringResource(R.string.hb9cv_dk_formula))
            portable -> Text(stringResource(R.string.hb9cv_portable_formula))
            profile.id == "pd0ddr" -> Text(stringResource(R.string.hb9cv_pd_formula))
            else -> Text(stringResource(R.string.hb9cv_fixed_formula))
        }
    }
}

@Composable
private fun Hb9cvInput(key: String, label: String, min: Double, max: Double): Pair<Double?, Boolean> {
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
