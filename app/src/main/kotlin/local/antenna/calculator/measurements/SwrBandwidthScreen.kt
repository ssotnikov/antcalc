package local.antenna.calculator.measurements

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import local.antenna.calculator.R
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.measurements.*
import local.antenna.calculator.state.boundedNumericText
import local.antenna.calculator.ui.FormulaPlate
import local.antenna.calculator.ui.cardListBounce

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwrBandwidthScreen(
    model: MeasurementViewModel,
    onOpenMeasurements: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, resources) {
        BandwidthPresentation(resources, MeasurementNumberFormat(locale), DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }
    val preferences = remember(context) { context.getSharedPreferences("measured-swr-bandwidth", Context.MODE_PRIVATE) }
    var rawThreshold by rememberSaveable { mutableStateOf(readBandwidthThreshold(preferences.all) ?: "2") }
    var selectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var cursor by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var showFormula by rememberSaveable { mutableStateOf(false) }
    val entries = if (state.showTraces && !state.busy) state.traces.mapIndexedNotNull { slot, trace ->
        trace?.let { slot to it }
    } else emptyList()
    val active = entries.firstOrNull { it.first == selectedSlot } ?: entries.firstOrNull()
    val trace = active?.second
    val threshold = parseBandwidthThreshold(rawThreshold)
    val result = remember(trace?.sweep, threshold) {
        if (threshold == null) null else trace?.sweep?.swrBandwidth(threshold.toDouble())
    }
    val copied = stringResource(R.string.copied)
    val failed = stringResource(R.string.bandwidth_copy_failed)
    val title = stringResource(R.string.bandwidth_title)

    val listState = rememberLazyListState()
    LazyColumn(
        Modifier.widthIn(max = 680.dp).fillMaxSize().cardListBounce(listState.isScrollInProgress).testTag("bandwidth-screen"),
        state = listState,
        overscrollEffect = null,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BandwidthHeading(R.string.bandwidth_source)
            if (entries.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { (slot, _) ->
                    FilterChip(selected = slot == active?.first, onClick = {
                        selectedSlot = slot
                        cursor = Double.NaN
                    }, modifier = Modifier.testTag("bandwidth-trace-$slot"),
                        label = { Text(stringResource(traceLabel(slot))) })
                }
            }
            if (trace != null) {
                Text(trace.name, style = MaterialTheme.typography.titleMedium)
                Text(formatter.traceDetails(trace))
            } else if (!state.busy) {
                Text(stringResource(if (state.error == null) R.string.bandwidth_no_trace else R.string.bandwidth_unreadable))
            }
            OutlinedButton(onOpenMeasurements, enabled = !state.busy, modifier = Modifier.testTag("bandwidth-open-measurements")) {
                Text(stringResource(R.string.bandwidth_open_measurements))
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = rawThreshold,
                onValueChange = { value ->
                    rawThreshold = value.boundedNumericText()
                    parseBandwidthThreshold(rawThreshold)?.let { storeBandwidthThreshold(preferences, it) }
                },
                label = { Text(stringResource(R.string.bandwidth_threshold)) },
                supportingText = { Text(stringResource(R.string.bandwidth_threshold_range)) },
                isError = threshold == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("bandwidth-threshold"),
            )
        }
        if (active != null && trace != null && result != null && threshold != null) {
            item {
                val points = trace.sweep.points
                val selected = cursor.takeIf { it.isFinite() }?.let(trace.sweep::nearestIndex)?.let(points::get)
                val frequency = selected?.hertz ?: result.minimum?.hertz ?: points.first().hertz
                BandwidthHeading(R.string.bandwidth_chart)
                MeasurementChart(listOf(active), SweepMetric.SWR, frequency, active.first, bandwidth = result) {
                    cursor = it.coerceIn(points.first().hertz, points.last().hertz)
                }
                Text(stringResource(R.string.bandwidth_chart_hint), style = MaterialTheme.typography.bodySmall)
            }
            item {
                BandwidthHeading(R.string.bandwidth_intervals)
                result.minimum?.let { Text(formatter.minimum(R.string.bandwidth_sweep_minimum, it)) }
                if (result.bands.isEmpty()) Text(stringResource(R.string.bandwidth_no_bands), Modifier.testTag("bandwidth-empty"))
                if (result.excludedPoints > 0) Text(stringResource(R.string.bandwidth_excluded, result.excludedPoints))
            }
            itemsIndexed(result.bands) { index, band ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("bandwidth-interval-$index")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.bandwidth_interval, index + 1), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary)
                        formatter.bandLines(band).forEach { Text(it) }
                    }
                }
            }
            item {
                BandwidthHeading(R.string.bandwidth_reactance)
                Text(stringResource(R.string.bandwidth_reactance_hint), style = MaterialTheme.typography.bodySmall)
                if (result.reactanceZeros.isEmpty()) Text(stringResource(R.string.bandwidth_no_reactance))
            }
            itemsIndexed(result.reactanceZeros) { _, zero -> Text(formatter.reactance(zero)) }
            item {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                        val report = formatter.report(trace, threshold, result)
                        try {
                            clipboard.setPrimaryClip(ClipData.newPlainText(title, report))
                            onMessage(copied)
                        } catch (_: RuntimeException) {
                            onMessage(failed)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("bandwidth-copy"),
                ) { Text(stringResource(R.string.bandwidth_copy)) }
            }
        }
        item {
            Text(stringResource(R.string.bandwidth_method), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.bandwidth_scope), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showFormula = !showFormula }) {
                Text(stringResource(if (showFormula) R.string.bandwidth_hide_formula else R.string.bandwidth_show_formula))
            }
            if (showFormula) FormulaPlate(
                R.string.sweep_swr, R.drawable.formula_swr, R.string.matching_formula_swr_alt, "bandwidth-formula",
            )
        }
    }
}

@Composable
private fun BandwidthHeading(title: Int) {
    Text(stringResource(title), Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleLarge)
}

private class BandwidthPresentation(
    private val resources: Resources,
    private val format: MeasurementNumberFormat,
    private val decimalSeparator: Char,
) {
    private fun text(id: Int, vararg args: Any): String = resources.getString(id, *args)
    private fun frequency(hertz: Double) = text(R.string.sweep_mhz, format.megahertz(hertz))

    fun traceDetails(trace: MeasurementTrace) = text(R.string.bandwidth_trace_details,
        format.measurement(trace.sweep.referenceOhms), text(trace.plane.label()),
        frequency(trace.sweep.points.first().hertz), frequency(trace.sweep.points.last().hertz), trace.sweep.points.size)

    fun minimum(label: Int, minimum: SwrMinimum) = text(label, format.measurement(minimum.swr), frequency(minimum.hertz))

    private fun edge(label: Int, edge: BandEdge) = text(label, frequency(edge.hertz), text(when (edge.kind) {
        BandEdgeKind.MEASURED -> R.string.bandwidth_edge_measured
        BandEdgeKind.ESTIMATED -> R.string.bandwidth_edge_estimated
        BandEdgeKind.SWEEP_LIMIT -> R.string.bandwidth_edge_sweep
        BandEdgeKind.INVALID_NEIGHBOR -> R.string.bandwidth_edge_gap
    }))

    fun bandLines(band: SwrBand): List<String> {
        val widthLabel = when {
            band.widthHertz == 0.0 -> R.string.bandwidth_single_point
            listOf(band.lower, band.upper).any { it.kind == BandEdgeKind.SWEEP_LIMIT || it.kind == BandEdgeKind.INVALID_NEIGHBOR } ->
                R.string.bandwidth_partial_width
            listOf(band.lower, band.upper).any { it.kind == BandEdgeKind.ESTIMATED } -> R.string.bandwidth_estimated_width
            else -> R.string.bandwidth_width
        }
        return listOf(edge(R.string.bandwidth_lower, band.lower), edge(R.string.bandwidth_upper, band.upper),
            text(widthLabel, frequency(band.widthHertz)), minimum(R.string.bandwidth_band_minimum, band.minimum))
    }

    fun reactance(zero: ReactanceZero) = when {
        zero.estimated -> text(R.string.bandwidth_zero_estimated, frequency(zero.firstHertz))
        zero.firstHertz == zero.lastHertz -> text(R.string.bandwidth_zero_measured, frequency(zero.firstHertz))
        else -> text(R.string.bandwidth_zero_range, frequency(zero.firstHertz), frequency(zero.lastHertz))
    }

    fun report(trace: MeasurementTrace, threshold: BigDecimal, result: SwrBandwidth) = buildString {
        appendLine(text(R.string.bandwidth_title))
        appendLine(trace.name)
        appendLine(traceDetails(trace))
        appendLine(text(R.string.bandwidth_threshold_report, threshold.toPlainString().replace('.', decimalSeparator)))
        appendLine()
        result.minimum?.let { appendLine(minimum(R.string.bandwidth_sweep_minimum, it)) }
        if (result.bands.isEmpty()) appendLine(text(R.string.bandwidth_no_bands))
        result.bands.forEachIndexed { index, band ->
            appendLine(text(R.string.bandwidth_interval, index + 1))
            bandLines(band).forEach { appendLine(it) }
        }
        if (result.excludedPoints > 0) appendLine(text(R.string.bandwidth_excluded, result.excludedPoints))
        appendLine()
        appendLine(text(R.string.bandwidth_reactance))
        if (result.reactanceZeros.isEmpty()) appendLine(text(R.string.bandwidth_no_reactance))
        result.reactanceZeros.forEach { appendLine(reactance(it)) }
        appendLine(text(R.string.bandwidth_reactance_hint))
        appendLine(text(R.string.bandwidth_method))
        append(text(R.string.bandwidth_scope))
    }
}

private fun parseBandwidthThreshold(raw: String): BigDecimal? {
    if (raw.length > PositiveDecimalParser.MAX_CHARACTERS) return null
    val value = (PositiveDecimalParser.parse(raw) as? FieldState.Valid)?.value ?: return null
    return value.takeIf { it >= BigDecimal.ONE && it <= BigDecimal.valueOf(1000) }
}

private fun readBandwidthThreshold(values: Map<String, *>): String? {
    if (values["schema"] != 1) return null
    val raw = values["threshold"] as? String ?: return null
    return parseBandwidthThreshold(raw)?.toPlainString()
}

private fun storeBandwidthThreshold(preferences: SharedPreferences, value: BigDecimal) {
    val existing = preferences.all
    if (existing.isEmpty() || readBandwidthThreshold(existing) != null) {
        preferences.edit {
            putInt("schema", 1)
            putString("threshold", value.toPlainString())
        }
    }
}
