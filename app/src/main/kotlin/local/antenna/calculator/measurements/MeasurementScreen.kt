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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.measurements.S11Point

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
internal fun MeasurementScreen(
    model: MeasurementViewModel, onSaveToProject: (() -> Unit)?, onMessage: (String) -> Unit,
) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val format = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 9; isGroupingUsed = false } }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var active by rememberSaveable { mutableIntStateOf(0) }
    var cursor by rememberSaveable { mutableIntStateOf(-1) }
    val tablePageA = rememberSaveable { mutableIntStateOf(-1) }
    val tablePageB = rememberSaveable { mutableIntStateOf(-1) }
    var metric by rememberSaveable { mutableStateOf(SweepMetric.SWR) }
    val appName = stringResource(R.string.app_name)
    val copied = stringResource(R.string.copied)
    val displayedTraces = if (state.showTraces) state.traces else listOf(null, null)
    val entries = displayedTraces.mapIndexedNotNull { index, trace -> trace?.let { index to it } }
    val activeSlot = active.takeIf { displayedTraces[it] != null } ?: entries.firstOrNull()?.first ?: 0
    val tablePage = if (activeSlot == 0) tablePageA else tablePageB
    val activeTrace = displayedTraces[activeSlot]
    val points = activeTrace?.sweep?.points.orEmpty()
    val index = if (points.isEmpty()) 0 else cursor.coerceIn(points.indices)
    val frequency = points.getOrNull(index)?.hertz
    LaunchedEffect(activeTrace?.sweep) {
        if (points.isNotEmpty() && cursor < 0) {
            cursor = points.indices.minByOrNull { points[it].swr ?: Double.POSITIVE_INFINITY } ?: 0
            tablePage.intValue = cursor / PAGE_SIZE
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

    val display = remember(locale) { MeasurementNumberFormat(locale) }
    fun frequencyText(hertz: Double) = resources.getString(R.string.sweep_mhz, number(hertz / 1e6))
    fun row(label: Int, value: String) = "${resources.getString(label)} · $value"
    fun pointText(point: S11Point, referenceOhms: Double): String {
        val impedance = point.impedance(referenceOhms)
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
            MeasurementChart(entries, metric, frequency, activeSlot) { hertz ->
                cursor = activeTrace.sweep.nearestIndex(hertz.coerceIn(points.first().hertz, points.last().hertz)) ?: index
                tablePage.intValue = cursor / PAGE_SIZE
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FrequencyStep(forward = false, enabled = index > 0) {
                    cursor = index - 1
                    tablePage.intValue = cursor / PAGE_SIZE
                }
                Text(frequencyText(frequency), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                FrequencyStep(forward = true, enabled = index < points.lastIndex) {
                    cursor = index + 1
                    tablePage.intValue = cursor / PAGE_SIZE
                }
            }
            comparisonNotes.forEach { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            MeasurementSummaries(entries, selected, display, ::frequencyText)
            if (onSaveToProject != null) Button(onClick = onSaveToProject, modifier = Modifier.fillMaxWidth().testTag("sweep-save-project")) {
                Text(stringResource(R.string.sweep_save))
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                val content = entries.mapIndexed { i, (slotIndex, trace) ->
                    val heading = resources.getString(traceLabel(slotIndex)) + " · " + trace.name
                    val data = selected[i]?.let { pointText(it, trace.sweep.referenceOhms) }
                        ?: resources.getString(R.string.sweep_outside)
                    "$heading\n$data"
                }.joinToString("\n\n")
                clipboard.setPrimaryClip(ClipData.newPlainText(appName, content))
                onMessage(copied)
            }, modifier = Modifier.fillMaxWidth().testTag("sweep-copy-frequency")) {
                Text(stringResource(R.string.sweep_copy))
            }
            val lastPage = points.lastIndex / PAGE_SIZE
            val visiblePage = tablePage.intValue.takeIf { it >= 0 }?.coerceAtMost(lastPage) ?: (index / PAGE_SIZE)
            var expanded by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("sweep-table-toggle")) {
                Text(stringResource(if (expanded) R.string.sweep_table_hide else R.string.sweep_table_show))
            }
            if (expanded) {
                if (entries.size == 2) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { (slotIndex, trace) ->
                        FilterChip(selected = slotIndex == activeSlot, onClick = {
                            active = slotIndex
                            cursor = trace.sweep.nearestIndex(frequency.coerceIn(trace.sweep.points.first().hertz, trace.sweep.points.last().hertz)) ?: 0
                        }, modifier = Modifier.testTag("sweep-table-trace-$slotIndex"), label = { Text(stringResource(traceLabel(slotIndex)), color = measurementTraceColor(slotIndex)) })
                    }
                }
                StepControls(stringResource(R.string.sweep_table_page, visiblePage + 1, lastPage + 1), visiblePage > 0, visiblePage < lastPage) {
                    tablePage.intValue = visiblePage + it
                }
                val start = visiblePage * PAGE_SIZE
                MeasurementTable(
                    points.subList(start, minOf(start + PAGE_SIZE, points.size)),
                    activeTrace.sweep.referenceOhms, index - start, display,
                ) { cursor = start + it }
            }
        }
        Text(stringResource(R.string.sweep_help), style = MaterialTheme.typography.bodySmall)
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
private fun FrequencyStep(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        Icon(
            painterResource(R.drawable.ic_back),
            contentDescription = stringResource(if (forward) R.string.sweep_next_frequency else R.string.sweep_previous_frequency),
            modifier = Modifier.size(24.dp).rotate(if (forward) 180f else 0f),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
        )
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
