package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.TrapDipole
import local.antenna.calculator.core.TrapDipole.Band
import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.core.TrapDipole.Quantity
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.state.boundedNumericText

@Composable
internal fun TrapDipoleScreen(
    initial: CalculationSnapshot?,
    onMeasurements: ((Band) -> Unit)?,
    onBandwidth: ((Band) -> Unit)?,
    onSave: ((CalculationSnapshot) -> Unit)?,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val restored = remember(initial) { initial?.let(TrapDraft::restore) }
    var profile by rememberSaveable { mutableStateOf(restored?.first ?: Profile.R2AUK) }
    var band by rememberSaveable { mutableStateOf(restored?.second ?: Band.M20) }
    var drafts by rememberSaveable(stateSaver = TrapDraft.saver) {
        mutableStateOf(Profile.entries.map { restored?.takeIf { saved -> saved.first == it }?.third ?: TrapDraft() })
    }
    val draft = drafts[profile.ordinal]
    fun update(next: TrapDraft) { drafts = drafts.toMutableList().apply { set(profile.ordinal, next) } }
    val snapshot = draft.snapshot(profile, band)
    val author = stringResource(trapAuthors[profile.ordinal])
    val locale = resources.configuration.locales[0]
    val formatter = remember(locale) { NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false } }
    val format: (Double, Int) -> String = { value, digits ->
        formatter.maximumFractionDigits = digits
        formatter.format(value)
    }
    val copied = stringResource(R.string.copied)
    val copyFailed = stringResource(R.string.bandwidth_copy_failed)
    val vector = remember(profile) { trapDipoleVector(profile) }
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberPageScrollState())
            .padding(20.dp).testTag("trap-dipole-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (initial != null && restored == null) {
            HelpText(R.string.trap_revision)
            TrapCopy(initial, context, resources, format, copied, copyFailed, onMessage)
            return@Column
        }
        HelpText(R.string.trap_lead)
        stringResource(R.string.trap_overview).split('\n').forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        HelpText(R.string.trap_scope)
        RotatingAntenna("trap-${profile.id}", author, Modifier.testTag("trap-model"), geometry = vector)
        HelpText(R.string.trap_model_note)
        TrapHeading(R.string.trap_construction)
        var open by remember { mutableStateOf(false) }
        Box {
            OutlinedButton({ open = true }, Modifier.fillMaxWidth().testTag("trap-profile")) {
                Text(author, Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(open, { open = false }) {
                Profile.entries.forEach {
                    DropdownMenuItem({ Text(stringResource(trapAuthors[it.ordinal])) }, { profile = it; open = false },
                        Modifier.testTag("trap-profile-${it.name}"))
                }
            }
        }
        HelpText(trapInstallation[profile.ordinal])
        HelpText(trapMeasurements[profile.ordinal])
        TrapHeading(R.string.trap_general)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Band.entries.forEach {
                FilterChip(band == it, { band = it }, { Text(stringResource(R.string.trap_band, it.meters)) },
                    modifier = Modifier.testTag("trap-band-${it.meters}"))
            }
        }
        TrapDiagram(profile, band)
        Text(stringResource(R.string.trap_active, trapSectionNames.take(band.ordinal + 1).joinToString(" + ")))
        TrapHeading(R.string.trap_cut)
        HelpText(R.string.trap_cut_note)
        TrapField(draft.allowance, R.string.trap_allowance, draft.allowanceMm == null, "trap-allowance") {
            update(draft.copy(allowance = it))
        }
        TrapField(draft.targets[band.ordinal], R.string.trap_target,
            draft.targets[band.ordinal].isNotBlank() && draft.target(band) == null, "trap-target",
            stringResource(R.string.trap_range, format(band.lowerMhz, 3), format(band.upperMhz, 3))) { raw ->
            update(draft.copy(targets = draft.targets.toMutableList().apply { set(band.ordinal, raw) }))
        }
        val digits = if (profile == Profile.AA4LR) 4 else 3
        Text(stringResource(R.string.trap_nominal, format(2 * profile.sections.sum(), digits)))
        draft.allowanceMm?.let(profile::blanks)?.let { blanks ->
            ScrollableTable(Modifier.width(450.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                blanks.forEachIndexed { index, length ->
                    Text(stringResource(R.string.trap_cut_row, trapSectionNames[index], format(profile.sections[index], digits), format(length, 6)))
                }
            }
            Text(stringResource(R.string.trap_total, format(2 * blanks.sum(), 6)), Modifier.testTag("trap-total"),
                style = MaterialTheme.typography.titleMedium)
        }
        TrapHeading(R.string.trap_traps)
        TrapCircuitDiagram(profile.coaxial)
        val invalidTraps = draft.invalidTraps(profile)
        profile.traps.forEachIndexed { index, trap ->
            Text(stringResource(R.string.trap_pair, if (index == 0) 20 else 40), style = MaterialTheme.typography.titleSmall)
            val diameter = format(trap.diameterMm, 2)
            val turns = format(trap.turns, 2)
            Text(if (profile.coaxial) stringResource(R.string.trap_coax, diameter, format(requireNotNull(trap.formerLengthMm), 2), turns)
                else stringResource(R.string.trap_lc_trap, diameter, turns, format(requireNotNull(trap.capacitancePf), 0),
                    format(requireNotNull(trap.measuredMhz), 2)))
            val labels = listOf(if (profile.coaxial) R.string.trap_blank_cable else R.string.trap_blank_wire,
                R.string.trap_strip_near, R.string.trap_strip_far)
            val published = listOf(trap.blankMeters, trap.stripNearMeters, trap.stripFarMeters)
            val fields = labels.indices.take(if (profile.coaxial) 3 else 1)
            fields.forEach { field ->
                published[field]?.let { Text("${stringResource(labels[field])} · ${format(it, 3)}") }
            }
            if (fields.any { published[it] == null }) {
                HelpText(R.string.trap_missing)
                TrapDisclosure(R.string.trap_my_build) {
                    fields.filter { published[it] == null }.forEach { field ->
                        val position = 3 * index + field
                        TrapField(draft.dimensions[position], labels[field], index in invalidTraps, "trap-dimension-$position",
                            stringResource(R.string.trap_range, format(.001, 3), format(30.0, 0))) { raw ->
                            update(draft.copy(dimensions = draft.dimensions.toMutableList().apply { set(position, raw) }))
                        }
                    }
                }
            }
            if (index in invalidTraps) Text(stringResource(R.string.trap_invalid), color = MaterialTheme.colorScheme.error)
            else (published[0] ?: draft.dimension(3 * index))?.let {
                Text(stringResource(R.string.trap_material_total, format(2 * it, 6)))
            }
        }
        if (onSave != null) Button({ snapshot?.let(onSave) }, Modifier.fillMaxWidth().testTag("trap-save"), enabled = snapshot != null) {
            Text(stringResource(R.string.jpole_copyProject))
        }
        TrapCopy(snapshot, context, resources, format, copied, copyFailed, onMessage)
        TrapLcCalculator(format)
        TrapDisclosure(R.string.trap_tuning) {
            HelpText(R.string.trap_tune_help)
            onMeasurements?.let { openMeasurements ->
                TextButton({ openMeasurements(band) }, Modifier.testTag("trap-open-measurements")) { Text(stringResource(R.string.sweep_tool_title)) }
            }
            onBandwidth?.let { openBandwidth ->
                TextButton({ openBandwidth(band) }, Modifier.testTag("trap-open-bandwidth")) { Text(stringResource(R.string.bandwidth_title)) }
            }
        }
        TrapHeading(R.string.trap_sources)
        HelpText(R.string.trap_rights)
        TrapPublicationLinks(profile)
    }
}

@Composable
private fun TrapCopy(
    snapshot: CalculationSnapshot?,
    context: android.content.Context,
    resources: android.content.res.Resources,
    format: (Double, Int) -> String,
    copied: String,
    failed: String,
    onMessage: (String) -> Unit,
) {
    val title = stringResource(R.string.trap_title)
    OutlinedButton(onClick = {
        val value = snapshot ?: return@OutlinedButton
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
        val report = buildString {
            appendLine(title)
            (value.inputs + value.outputs).forEach { metric ->
                appendLine(trapMetricLabel(resources, metric.key) + " · " + (trapMetricDisplay(resources, metric, format)
                    ?: format(metric.value, 9)))
            }
        }
        val result = runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(title, report)) }
        onMessage(if (result.isSuccess) copied else failed)
    }, Modifier.fillMaxWidth().testTag("trap-copy"), enabled = snapshot != null) { Text(stringResource(R.string.halo_copy)) }
}

@Composable
private fun TrapDiagram(profile: Profile, band: Band) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline
    val trap = MaterialTheme.colorScheme.tertiary
    val segments = listOf(2, 1, 0, 0, 1, 2)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val half = .46f
        val total = profile.sections.sum()
        val t20 = half * (profile.sections[0] / total).toFloat()
        val t40 = half * (profile.sections.take(2).sum() / total).toFloat()
        val marks = listOf(.5f - half, .5f - t40, .5f - t20, .488f, .512f, .5f + t20, .5f + t40, .5f + half)
        BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
            val width = maxWidth
            val labels = listOf(
                "C" to (marks[0] + marks[1]) / 2, "T40" to marks[1], "B" to (marks[1] + marks[2]) / 2,
                "T20" to marks[2], "A" to (marks[2] + marks[3]) / 2, "A" to (marks[4] + marks[5]) / 2,
                "T20" to marks[5], "B" to (marks[5] + marks[6]) / 2, "T40" to marks[6], "C" to (marks[6] + marks[7]) / 2,
            )
            labels.forEach { (label, x) ->
                val trapLabel = label.startsWith("T")
                Box(Modifier.offset(x = width * x - 17.dp, y = if (trapLabel) 0.dp else 14.dp).width(34.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = if (trapLabel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(46.dp).testTag("trap-diagram")) {
            val y = size.height * .3f
            val endpoints = listOf(0 to 1, 1 to 2, 2 to 3, 4 to 5, 5 to 6, 6 to 7)
            endpoints.forEachIndexed { index, (start, end) ->
                val engaged = segments[index] <= band.ordinal
                drawLine(if (engaged) active else inactive, Offset(size.width * marks[start], y), Offset(size.width * marks[end], y),
                    if (engaged) 3.dp.toPx() else 1.5.dp.toPx(),
                    pathEffect = if (engaged) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            }
            for (at in listOf(marks[1], marks[2], marks[5], marks[6])) drawCircle(trap, 4.dp.toPx(), Offset(size.width * at, y))
            for (at in listOf(marks[3], marks[4])) drawLine(active, Offset(size.width * at, y), Offset(size.width * at, size.height * .82f), 2.dp.toPx())
        }
        Text(stringResource(R.string.trap_feed), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TrapCircuitDiagram(coaxial: Boolean) {
    val conductor = MaterialTheme.colorScheme.secondary
    val accent = MaterialTheme.colorScheme.tertiary
    val description = stringResource(if (coaxial) R.string.trap_coax_wiring else R.string.trap_lc_wiring)
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val y = size.height * .30f
        val left = size.width * .23f
        val right = size.width * .77f
        val span = (right - left) / 4
        val bottom = size.height * .80f
        val center = size.width / 2
        val gap = 5.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(conductor, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())
        line(size.width * .08f, y, left, y)
        line(right, y, size.width * .92f, y)
        repeat(4) { index ->
            drawArc(accent, 180f, 180f, false,
                topLeft = Offset(left + span * index, y - size.height * .15f),
                size = Size(span, size.height * .30f), style = Stroke(2.dp.toPx()))
        }
        line(left, y, left, bottom)
        line(left, bottom, center - gap, bottom)
        line(center + gap, bottom, right, bottom)
        line(right, bottom, right, y)
        for (plate in listOf(center - gap, center + gap)) {
            drawLine(accent, Offset(plate, bottom - 8.dp.toPx()), Offset(plate, bottom + 8.dp.toPx()), 2.dp.toPx())
        }
    }
    Text(description, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TrapLcCalculator(format: (Double, Int) -> String) {
    var unknown by rememberSaveable { mutableStateOf(Quantity.INDUCTANCE) }
    var frequency by rememberSaveable { mutableStateOf("7.100") }
    var inductance by rememberSaveable { mutableStateOf("") }
    var capacitance by rememberSaveable { mutableStateOf("100") }
    var lcOpen by rememberSaveable { mutableStateOf(false) }
    TrapDisclosure(R.string.trap_lc, lcOpen, { lcOpen = it }) {
        HelpText(R.string.trap_lc_help)
        Text("f₀ = 1 / (2π√LC)")
        val labels = listOf(R.string.trap_f, R.string.trap_l, R.string.trap_c)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quantity.entries.forEach {
                FilterChip(unknown == it, { unknown = it }, { Text(listOf("f₀", "L", "C")[it.ordinal]) })
            }
        }
        val values = listOf(frequency, inductance, capacitance)
        val parsed = Quantity.entries.map { trapNumber(values[it.ordinal], it.limits) }
        Quantity.entries.filter { it != unknown }.forEach {
            TrapField(values[it.ordinal], labels[it.ordinal], values[it.ordinal].isNotBlank() && parsed[it.ordinal] == null,
                "trap-lc-${it.name}", stringResource(R.string.trap_range, format(it.limits.start, 3), format(it.limits.endInclusive, 0))) { raw ->
                when (it) {
                    Quantity.FREQUENCY -> frequency = raw
                    Quantity.INDUCTANCE -> inductance = raw
                    Quantity.CAPACITANCE -> capacitance = raw
                }
            }
        }
        val answer = TrapDipole.solve(unknown, parsed[0], parsed[1], parsed[2])
        if (answer == null) HelpText(R.string.trap_lc_invalid)
        else Text("${stringResource(labels[unknown.ordinal])} = ${format(answer, 3)}", Modifier.testTag("trap-lc-result"),
            style = MaterialTheme.typography.titleMedium)
    }
    TrapDisclosure(R.string.trap_wheeler) {
        HelpText(R.string.trap_wheeler_help)
        Text("L (µH) ≈ N²D² / (18D + 40ℓ)")
        var former by rememberSaveable { mutableStateOf("49.8") }
        var wire by rememberSaveable { mutableStateOf("1") }
        var winding by rememberSaveable { mutableStateOf("106.68") }
        var turns by rememberSaveable { mutableStateOf("16") }
        val a = trapNumber(former, 1.0..300.0)
        val b = trapNumber(wire, .1..10.0)
        val c = trapNumber(winding, 1.0..1000.0)
        val n = trapNumber(turns, 1.0..100.0)
        TrapField(former, R.string.trap_former, a == null, "trap-former") { former = it }
        TrapField(wire, R.string.trap_wire_mm, b == null, "trap-wire") { wire = it }
        TrapField(winding, R.string.trap_winding, c == null, "trap-winding") { winding = it }
        TrapField(turns, R.string.trap_turns, n == null, "trap-turns") { turns = it }
        val result = if (a != null && b != null && c != null && n != null) TrapDipole.wheeler(a, b, c, n) else null
        if (result == null) HelpText(R.string.trap_coil_invalid)
        else {
            Text("${stringResource(R.string.trap_l)} ≈ ${format(result, 3)}", Modifier.testTag("trap-coil-result"))
            if (result in Quantity.INDUCTANCE.limits) TextButton({
                inductance = format(result, 9)
                unknown = Quantity.FREQUENCY
                lcOpen = true
            }) { Text(stringResource(R.string.trap_use_l)) }
        }
    }
}

@Composable
private fun TrapField(raw: String, label: Int, invalid: Boolean, tag: String, hint: String? = null, update: (String) -> Unit) {
    OutlinedTextField(raw, { if (it.acceptsDecimalEdit()) update(it.boundedNumericText()) },
        Modifier.fillMaxWidth().testTag(tag), label = { Text(stringResource(label)) }, isError = invalid,
        supportingText = hint?.let { { Text(it) } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun TrapHeading(label: Int) {
    Text(stringResource(label), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun TrapDisclosure(label: Int, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TrapDisclosure(label, open, { open = it }, content)
}

@Composable
private fun TrapDisclosure(label: Int, open: Boolean, onOpen: (Boolean) -> Unit, content: @Composable () -> Unit) {
    TextButton({ onOpen(!open) }, Modifier.testTag("trap-section-$label")) { Text(stringResource(label) + if (open) " ▴" else " ▾") }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
