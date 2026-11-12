package local.antenna.calculator.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.measurements.MeasurementTrace
import local.antenna.calculator.measurements.SweepPlane
import local.antenna.calculator.measurements.measurementTraceColor
import local.antenna.calculator.measurements.traceLabel

@Composable
internal fun JournalCalculationComparisonScreen(
    projectTitle: String, calculation: JournalRecord, manual: JournalRecord,
    presentation: JournalPresentation, copy: (String) -> Unit,
) {
    val length = compareSizingLength(calculation, manual)
    val calculationDetails = presentation.recordDetails(calculation)
    val manualDetails = presentation.recordDetails(manual)
    val resources = LocalResources.current
    val project = stringResource(R.string.journal_report_project) to projectTitle
    val name = stringResource(
        if (calculation.antennaType == AntennaType.MONOPOLE) R.string.radiator_length else R.string.dipole_total,
    )
    val row = length?.let {
        fun cm(meters: Double) = resources.getString(R.string.value_unit, presentation.number(meters * 100, 2),
            resources.getString(R.string.cm))
        ComparisonRow(name, cm(it.estimatedMeters), cm(it.measuredMeters),
            signed(it.measuredMeters - it.estimatedMeters, cm(it.measuredMeters - it.estimatedMeters)))
    }
    val left = stringResource(R.string.journal_compare_calculated)
    val right = stringResource(R.string.journal_compare_measured)
    val deltaLabel = stringResource(R.string.journal_compare_difference)
    val note = stringResource(when {
        calculation.antennaType != manual.antennaType -> R.string.journal_compare_types
        row != null -> R.string.journal_compare_length_rule
        else -> R.string.journal_compare_no_shared_geometry
    })
    JournalColumn(Modifier.testTag("journal-geometry-comparison")) {
        JournalHeading(projectTitle)
        JournalHeading(stringResource(R.string.journal_compare_sizing_title), MaterialTheme.colorScheme.secondary)
        if (row != null) ComparisonGrid(left, right, listOf(row))
        Text(note, style = MaterialTheme.typography.bodySmall)
        var expanded by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.journal_compare_both_details)) }
        if (expanded) {
            JournalHeading(calculation.title)
            JournalDetails(calculationDetails)
            JournalHeading(manual.title)
            JournalDetails(manualDetails)
        }
        Button(onClick = {
            val results = row?.let { listOf(it.name to "${it.first} · ${it.second} · $deltaLabel ${it.delta}") }.orEmpty()
            copy(listOf(
                presentation.formatFields(listOf(project) + results), note,
                "$left · ${calculation.title}\n${presentation.formatFields(calculationDetails)}",
                "$right · ${manual.title}\n${presentation.formatFields(manualDetails)}",
            ).joinToString("\n\n"))
        }, modifier = Modifier.testTag("journal-copy-comparison")) { Text(stringResource(R.string.journal_copy)) }
    }
}

private sealed interface SweepLoad {
    data object Loading : SweepLoad
    data object Missing : SweepLoad
    data object ReadError : SweepLoad
    data class Ready(val trace: MeasurementTrace) : SweepLoad
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JournalSweepComparisonScreen(
    projectTitle: String, record: JournalRecord, sweep: SweepComparison, presentation: JournalPresentation,
    read: suspend (SweepAttachment) -> MeasurementTrace?, copy: (String) -> Unit,
) {
    val manual = checkNotNull(record.measurement)
    var index by rememberSaveable(sweep) { mutableIntStateOf(0) }
    val ref = sweep.traces[index.coerceIn(sweep.traces.indices)]
    val load by key(ref) {
        produceState<SweepLoad>(SweepLoad.Loading) {
            value = try {
                read(ref)?.let { SweepLoad.Ready(it) } ?: SweepLoad.Missing
            } catch (_: IOException) {
                SweepLoad.ReadError
            } catch (_: SecurityException) {
                SweepLoad.ReadError
            }
        }
    }
    val resources = LocalResources.current
    fun ohms(value: Double) = resources.getString(R.string.sweep_ohms, presentation.number(value, 2))
    fun number(value: Double?) = when (value) {
        null -> "—"
        Double.POSITIVE_INFINITY -> "∞"
        else -> presentation.number(value, 2)
    }
    val projectLabel = stringResource(R.string.journal_report_project)
    val manualLabel = stringResource(R.string.journal_manual_short)
    val selectedTraceLabel = stringResource(traceLabel(index))
    val testFrequency = stringResource(R.string.journal_test_frequency_label)
    val reference = stringResource(R.string.journal_reference)
    val connection = stringResource(R.string.journal_point)
    val absent = stringResource(R.string.journal_comparison_missing)
    val manualInfo = listOf(
        testFrequency to (manual.testFrequency?.let { presentation.frequency(it.hertz) } ?: absent),
        connection to stringResource(manual.point.journalLabel()),
        reference to (manual.referenceOhms?.let(::ohms) ?: absent),
    )
    JournalColumn(Modifier.testTag("journal-sweep-comparison")) {
        JournalHeading(projectTitle)
        JournalHeading(stringResource(R.string.journal_sweep_compare_title), MaterialTheme.colorScheme.secondary)
        if (sweep.traces.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sweep.traces.indices.forEach { slot ->
                FilterChip(selected = index == slot, onClick = { index = slot },
                    label = { Text(stringResource(traceLabel(slot)), color = measurementTraceColor(slot)) },
                    modifier = Modifier.testTag("journal-compare-trace-$slot"))
            }
        }
        Text(ref.name, style = MaterialTheme.typography.titleSmall)
        when (val result = load) {
            SweepLoad.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            SweepLoad.Missing -> Text(stringResource(R.string.sweep_error_missing), color = MaterialTheme.colorScheme.error)
            SweepLoad.ReadError -> Text(stringResource(R.string.sweep_error_read), color = MaterialTheme.colorScheme.error)
            is SweepLoad.Ready -> {
                val trace = result.trace
                val plane = when (trace.plane) {
                    SweepPlane.FEED_POINT -> MeasurementPoint.FEED_POINT
                    SweepPlane.TRANSCEIVER -> MeasurementPoint.TRANSCEIVER
                    SweepPlane.UNKNOWN -> MeasurementPoint.UNSPECIFIED
                }
                val comparison = compareManualToSweep(manual, trace.sweep, plane)
                val traceInfo = listOf(
                    stringResource(R.string.sweep_frequency) to (comparison.point?.let { presentation.frequency(it.hertz) } ?: absent),
                    stringResource(R.string.sweep_plane) to stringResource(plane.journalLabel()),
                    reference to ohms(trace.sweep.referenceOhms),
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 320.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComparisonSourceCard("$manualLabel · ${record.title}", manualInfo, Modifier.fillMaxWidth())
                            ComparisonSourceCard("$selectedTraceLabel · S1P", traceInfo, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComparisonSourceCard("$manualLabel · ${record.title}", manualInfo, Modifier.weight(1f))
                            ComparisonSourceCard("$selectedTraceLabel · S1P", traceInfo, Modifier.weight(1f))
                        }
                    }
                }
                val notices = comparison.issues.map { stringResource(it.label()) }
                notices.forEach { notice ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Text(notice, Modifier.fillMaxWidth().padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                fun row(label: Int, first: Double?, second: Double?, change: Double?) =
                    ComparisonRow(resources.getString(label), number(first), number(second),
                        change?.let { signed(it, number(it)) } ?: "—")
                val rows = listOf(
                    row(R.string.matching_swr, manual.swr, comparison.point?.swr, comparison.swrDelta),
                    row(R.string.sweep_r_column, manual.resistanceOhms, comparison.resistanceOhms, comparison.resistanceDelta),
                    row(R.string.sweep_x_column, manual.reactanceOhms, comparison.reactanceOhms, comparison.reactanceDelta),
                )
                ComparisonGrid(manualLabel, "S1P", rows)
                val rule = stringResource(R.string.journal_sweep_delta_rule)
                val difference = stringResource(R.string.journal_compare_difference)
                Text(rule, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    copy(listOf(
                        presentation.formatFields(listOf(projectLabel to projectTitle)),
                        "$manualLabel · ${record.title}\n${presentation.formatFields(manualInfo)}",
                        "$selectedTraceLabel · ${ref.name}\n${presentation.formatFields(traceInfo)}",
                        presentation.formatFields(rows.map { it.name to "${it.first} · ${it.second} · $difference ${it.delta}" }),
                        (notices + rule).joinToString("\n"),
                    ).joinToString("\n\n"))
                }, modifier = Modifier.testTag("journal-copy-comparison")) { Text(stringResource(R.string.journal_copy)) }
            }
        }
    }
}

@Composable
private fun ComparisonSourceCard(title: String, fields: List<Pair<String, String>>, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            JournalDetails(fields)
        }
    }
}

private data class ComparisonRow(val name: String, val first: String, val second: String, val delta: String)

@Composable
private fun ComparisonGrid(first: String, second: String, rows: List<ComparisonRow>) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("journal-comparable-fields")) {
        ComparisonGridRow(ComparisonRow(stringResource(R.string.journal_compare_parameter), first, second,
            stringResource(R.string.journal_compare_difference)), header = true)
        for (row in rows) {
            HorizontalDivider()
            ComparisonGridRow(row, header = false)
        }
    }
}

@Composable
private fun ComparisonGridRow(row: ComparisonRow, header: Boolean) {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
        val color = if (header) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        Text(row.name, Modifier.weight(1.35f), style = style, color = color)
        Text(row.first, Modifier.weight(1f), style = style, color = color)
        Text(row.second, Modifier.weight(1f), style = style, color = color)
        Text(row.delta, Modifier.weight(1f), style = style, color = color)
    }
}

private fun signed(value: Double, formatted: String) = if (value > 0 && formatted.substringBefore(" ") != "0") "+$formatted" else formatted

private fun SweepComparisonIssue.label() = when (this) {
    SweepComparisonIssue.FREQUENCY_UNKNOWN -> R.string.journal_sweep_frequency_unknown
    SweepComparisonIssue.FREQUENCY_UNSAMPLED -> R.string.journal_sweep_frequency_unsampled
    SweepComparisonIssue.PLANE_MISMATCH -> R.string.journal_compare_points
    SweepComparisonIssue.REFERENCE_UNKNOWN -> R.string.journal_sweep_reference_unknown
    SweepComparisonIssue.REFERENCE_MISMATCH -> R.string.journal_sweep_reference_mismatch
    SweepComparisonIssue.INVALID_POINT -> R.string.sweep_nonpassive
}
