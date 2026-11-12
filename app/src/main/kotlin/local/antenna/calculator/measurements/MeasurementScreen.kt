package local.antenna.calculator.measurements

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.journal.JournalViewModel

internal enum class SweepMetric(val title: Int, val axis: String) {
    SWR(R.string.sweep_swr, ""), MAGNITUDE(R.string.sweep_magnitude, "|S11|"),
    DECIBELS(R.string.sweep_db, "dB"), IMPEDANCE(R.string.sweep_impedance, "Ω");

    fun value(trace: MeasurementTrace, point: S11Point, reactance: Boolean = false): Double? = when (this) {
        SWR -> point.swr
        MAGNITUDE -> point.magnitude
        DECIBELS -> point.decibels
        IMPEDANCE -> point.impedance(trace.sweep.referenceOhms)?.let { if (reactance) it.second else it.first }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeasurementScreen(model: MeasurementViewModel, journal: JournalViewModel?, onMessage: (String) -> Unit) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val format = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 9; isGroupingUsed = false } }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var active by rememberSaveable { mutableIntStateOf(0) }
    var cursor by rememberSaveable { mutableIntStateOf(0) }
    var tablePage by rememberSaveable { mutableIntStateOf(0) }
    var metric by rememberSaveable { mutableStateOf(SweepMetric.SWR) }
    var projectsOpen by rememberSaveable { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val copied = stringResource(R.string.copied)
    val saved = stringResource(R.string.project_transfer_saved)
    val displayedTraces = if (state.showTraces) state.traces else listOf(null, null)
    val entries = displayedTraces.mapIndexedNotNull { index, trace -> trace?.let { index to it } }
    val activeSlot = active.takeIf { displayedTraces[it] != null } ?: entries.firstOrNull()?.first ?: 0
    val activeTrace = displayedTraces[activeSlot]
    val points = activeTrace?.sweep?.points.orEmpty()
    val index = if (points.isEmpty()) 0 else cursor.coerceIn(points.indices)
    val frequency = points.getOrNull(index)?.hertz
    LaunchedEffect(activeTrace?.sweep) {
        if (points.isNotEmpty() && cursor == 0) {
            cursor = points.indices.minByOrNull { points[it].swr ?: Double.POSITIVE_INFINITY } ?: 0
            tablePage = cursor / PAGE_SIZE
        }
    }
    val selected = entries.map { (_, trace) ->
        frequency?.let(trace.sweep::nearestIndex)?.let(trace.sweep.points::get)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.import(slot, context.contentResolver, uri)
    }

    fun open(slotIndex: Int) {
        slot = slotIndex
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            model.pickerUnavailable()
        } catch (_: SecurityException) {
            model.pickerUnavailable()
        }
    }

    fun number(value: Double?): String = when (value) {
        null -> resources.getString(R.string.sweep_unavailable)
        Double.POSITIVE_INFINITY -> "∞"
        Double.NEGATIVE_INFINITY -> "−∞"
        else -> if (value.isNaN()) resources.getString(R.string.sweep_unavailable)
            else if (kotlin.math.abs(value) >= 1e12 || (value != 0.0 && kotlin.math.abs(value) < 1e-9)) "%.6g".format(locale, value)
            else format.format(value)
    }

    fun frequencyText(hertz: Double) = resources.getString(R.string.sweep_mhz, number(hertz / 1e6))
    fun row(label: Int, value: String) = "${resources.getString(label)} · $value"
    fun pointText(trace: MeasurementTrace, point: S11Point?): String {
        if (point == null) return resources.getString(R.string.sweep_outside)
        val impedance = point.impedance(trace.sweep.referenceOhms)
        return buildList {
            add(frequencyText(point.hertz))
            add(row(R.string.sweep_swr, number(point.swr)))
            add(row(R.string.sweep_magnitude, number(point.magnitude)))
            add(row(R.string.sweep_db, number(point.decibels)))
            add(row(R.string.sweep_phase, number(point.phaseDegrees) + "°"))
            if (point.isOpen) add(resources.getString(R.string.sweep_open_load)) else {
                add("R · ${number(impedance?.first)} Ω")
                add("X · ${number(impedance?.second)} Ω")
            }
            if (point.swr == null) add(resources.getString(R.string.sweep_nonpassive))
        }.joinToString("\n")
    }

    val comparisonNotes = buildList {
        if (entries.size == 2) {
            val first = entries[0].second
            val second = entries[1].second
            if (first.sweep.referenceOhms != second.sweep.referenceOhms) add(R.string.sweep_reference_mismatch)
            if (first.plane != second.plane || first.plane == SweepPlane.UNKNOWN) add(R.string.sweep_plane_mismatch)
            if (selected.all { it != null } && selected[0]?.hertz != selected[1]?.hertz) add(R.string.sweep_grid_mismatch)
        }
    }

    val scroll = rememberScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(scroll).padding(20.dp).testTag("measurement-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.sweep_files), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        displayedTraces.forEachIndexed { slotIndex, trace ->
            if (trace != null) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(traceLabel(slotIndex)), color = MaterialTheme.colorScheme.secondary)
                        Text(trace.name, style = MaterialTheme.typography.titleMedium)
                        Text("Z₀ · ${number(trace.sweep.referenceOhms)} Ω")
                        Text(stringResource(R.string.sweep_range, frequencyText(trace.sweep.points.first().hertz),
                            frequencyText(trace.sweep.points.last().hertz), trace.sweep.points.size))
                        PlanePicker(trace.plane, !state.busy) { model.setPlane(slotIndex, it) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { open(slotIndex) }, enabled = !state.busy,
                                modifier = Modifier.testTag("open-sweep-$slotIndex")) { Text(stringResource(R.string.sweep_replace)) }
                            TextButton(onClick = { model.remove(slotIndex) }, enabled = !state.busy) { Text(stringResource(R.string.sweep_remove)) }
                        }
                    }
                }
            } else if (slotIndex == 0 || displayedTraces[0] != null) {
                OutlinedButton(onClick = { open(slotIndex) }, enabled = !state.busy && state.error?.problem != MeasurementProblem.WORKSPACE,
                    modifier = Modifier.testTag("open-sweep-$slotIndex")) {
                    Text(stringResource(if (slotIndex == 0) R.string.sweep_import_a else R.string.sweep_import_b))
                }
            }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.importing) TextButton(onClick = model::cancelImport) { Text(stringResource(R.string.sweep_cancel)) }
        }
        state.error?.let {
            val message = stringResource(it.problem.label())
            Text(if (it.line > 0) stringResource(R.string.sweep_error_line, it.line, message) else message,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sweep-error"))
        }
        if (!state.busy && activeTrace != null && frequency != null) {
            Text(stringResource(R.string.sweep_graph), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SweepMetric.entries.forEach { option ->
                    FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(stringResource(option.title)) })
                }
            }
            MeasurementChart(entries, metric, frequency) { hertz ->
                cursor = activeTrace.sweep.nearestIndex(hertz.coerceIn(points.first().hertz, points.last().hertz)) ?: index
                tablePage = cursor / PAGE_SIZE
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.forEach { (slotIndex, _) ->
                    Text(stringResource(traceLabel(slotIndex)),
                        color = measurementTraceColor(slotIndex), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(stringResource(R.string.sweep_graph_legend), style = MaterialTheme.typography.bodySmall)
            if (metric == SweepMetric.IMPEDANCE) Text(stringResource(R.string.sweep_rx_legend), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.sweep_selected), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
            if (entries.size == 2) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { (slotIndex, trace) ->
                    FilterChip(selected = slotIndex == activeSlot, onClick = {
                        active = slotIndex
                        cursor = trace.sweep.nearestIndex(frequency.coerceIn(trace.sweep.points.first().hertz, trace.sweep.points.last().hertz)) ?: 0
                        tablePage = cursor / PAGE_SIZE
                    }, label = { Text(stringResource(traceLabel(slotIndex))) })
                }
            }
            StepControls(frequencyText(frequency), index > 0, index < points.lastIndex) { delta ->
                cursor = index + delta
                tablePage = cursor / PAGE_SIZE
            }
            comparisonNotes.forEach { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            entries.forEachIndexed { i, (slotIndex, trace) ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(traceLabel(slotIndex)), color = MaterialTheme.colorScheme.secondary)
                        Text(pointText(trace, selected[i]))
                    }
                }
            }
            val lastPage = points.lastIndex / PAGE_SIZE
            val visiblePage = tablePage.coerceIn(0, lastPage)
            var expanded by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.sweep_table_hide else R.string.sweep_table_show))
            }
            if (expanded) {
                StepControls(stringResource(R.string.sweep_table_page, visiblePage + 1, lastPage + 1), visiblePage > 0, visiblePage < lastPage) {
                    tablePage = visiblePage + it
                }
                val start = visiblePage * PAGE_SIZE
                points.subList(start, minOf(start + PAGE_SIZE, points.size)).forEachIndexed { offset, point ->
                    OutlinedButton(onClick = { cursor = start + offset }, modifier = Modifier.fillMaxWidth()) {
                        Text(pointText(activeTrace, point), Modifier.fillMaxWidth())
                    }
                }
            }
            if (journal != null) Button(onClick = { projectsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sweep_save))
            }
            OutlinedButton(onClick = {
                val content = buildString {
                    appendLine(resources.getString(R.string.sweep_title))
                    entries.forEachIndexed { i, (slotIndex, trace) ->
                        appendLine()
                        appendLine(resources.getString(traceLabel(slotIndex)) + " · " + trace.name)
                        appendLine("Z₀ · ${number(trace.sweep.referenceOhms)} Ω")
                        appendLine(resources.getString(trace.plane.label()))
                        appendLine(pointText(trace, selected[i]))
                    }
                    comparisonNotes.forEach { appendLine(resources.getString(it)) }
                    append(resources.getString(R.string.sweep_help))
                }
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(appName, content))
                onMessage(copied)
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sweep_copy)) }
        }
        Text(stringResource(R.string.sweep_help), style = MaterialTheme.typography.bodySmall)
    }

    if (projectsOpen && journal != null && !state.busy && entries.isNotEmpty()) {
        val projectState by journal.state.collectAsState()
        AlertDialog(
            onDismissRequest = { if (!projectState.busy) projectsOpen = false },
            title = { Text(stringResource(R.string.sweep_save)) },
            text = {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    if (projectState.projects.isEmpty()) Text(stringResource(R.string.sweep_no_projects))
                    projectState.problem?.let {
                        Text(stringResource(R.string.sweep_error_write), color = MaterialTheme.colorScheme.error)
                    }
                    projectState.projects.forEach { project ->
                        TextButton(enabled = !projectState.busy, onClick = {
                            journal.saveSweeps(project.id, entries.map { it.second }) {
                                projectsOpen = false
                                onMessage(saved)
                            }
                        }) { Text(project.title) }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !projectState.busy, onClick = { projectsOpen = false }) { Text(stringResource(R.string.sweep_cancel)) } },
        )
    }
}

private const val PAGE_SIZE = 12
internal fun traceLabel(index: Int) = if (index == 0) R.string.sweep_trace_a else R.string.sweep_trace_b

private fun MeasurementProblem.label() = when (this) {
    MeasurementProblem.FORMAT -> R.string.sweep_error_format
    MeasurementProblem.FREQUENCY -> R.string.sweep_error_frequency
    MeasurementProblem.LIMIT -> R.string.sweep_error_limit
    MeasurementProblem.READ -> R.string.sweep_error_read
    MeasurementProblem.WRITE -> R.string.sweep_error_write
    MeasurementProblem.WORKSPACE -> R.string.sweep_error_workspace
    MeasurementProblem.MISSING -> R.string.sweep_error_missing
}

internal fun SweepPlane.label() = when (this) {
    SweepPlane.UNKNOWN -> R.string.sweep_plane_unknown
    SweepPlane.FEED_POINT -> R.string.sweep_plane_feed
    SweepPlane.TRANSCEIVER -> R.string.sweep_plane_radio
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanePicker(plane: SweepPlane, enabled: Boolean, choose: (SweepPlane) -> Unit) {
    Text(stringResource(R.string.sweep_plane))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SweepPlane.entries.forEach { option ->
            FilterChip(selected = option == plane, enabled = enabled, onClick = { choose(option) },
                label = { Text(stringResource(option.label())) })
        }
    }
}

@Composable
private fun StepControls(label: String, previous: Boolean, next: Boolean, step: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedButton(onClick = { step(-1) }, enabled = previous) { Text(stringResource(R.string.sweep_previous)) }
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { step(1) }, enabled = next) { Text(stringResource(R.string.sweep_next)) }
    }
}
