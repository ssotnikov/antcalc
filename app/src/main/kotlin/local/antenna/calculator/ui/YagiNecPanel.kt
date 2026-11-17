package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.nec.OpenNec
import local.antenna.calculator.nec.NecOptimization
import local.antenna.calculator.nec.optimizeNecLengths
import local.antenna.calculator.state.boundedNumericText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun YagiNecPanel(
    reference: List<Element>?,
    elements: List<Element>,
    targetHz: Long,
    blocked: Int?,
    appliedLengths: List<Double>?,
    sweep: suspend (List<Element>, List<Int>, List<Long>) -> List<OpenNec.Point>? =
        { geometry, segments, frequencies -> OpenNec.sweep(geometry, segments, 1, frequencies) },
    onApply: (List<Element>) -> Unit,
) {
    if (blocked != null) {
        HelpText(blocked)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var lower by rememberSaveable(targetHz) { mutableStateOf(mhzInput(maxOf(100_000L, targetHz - targetHz / 100))) }
    var upper by rememberSaveable(targetHz) { mutableStateOf(mhzInput(minOf(6_000_000_000L, targetHz + targetHz / 100))) }
    var limit by rememberSaveable { mutableStateOf("2") }
    var status by remember { mutableStateOf<Int?>(null) }
    var comparison by remember(reference, elements, targetHz, lower, upper, limit) {
        mutableStateOf<Pair<List<OpenNec.Point>?, List<OpenNec.Point>>?>(null)
    }
    var optimization by remember(reference, elements, targetHz, lower, upper, limit) {
        mutableStateOf<NecOptimization?>(null)
    }
    var job by remember { mutableStateOf<Job?>(null) }
    val lowHz = scalingFrequency(lower)
    val highHz = scalingFrequency(upper)
    val swrLimit = scalingDecimal(limit)?.takeIf { it in 1.0..99.0 }
    val grid = if (lowHz != null && highHz != null && highHz - lowHz >= 20) {
        (0..20).map { lowHz + (highHz - lowHz) * it / 20 }
    } else null
    val segments = grid?.last()?.let { OpenNec.segmentCounts(elements, it) }
    val referenceSegments = grid?.last()?.let { highest -> reference?.let { OpenNec.segmentCounts(it, highest) } }
    val canRun = grid != null && segments != null && swrLimit != null &&
        (reference == null || referenceSegments != null)

    LaunchedEffect(reference, elements, targetHz, lower, upper, limit) {
        if (job != null) status = R.string.opennec_inputs_changed
        job?.cancel()
        job = null
        comparison = null
        optimization = null
    }
    if (!expanded) {
        TextButton({ expanded = true }, Modifier.testTag("opennec-expand")) {
            Text(stringResource(R.string.opennec_check))
        }
        return
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.opennec_check), style = MaterialTheme.typography.titleMedium)
            HelpText(R.string.opennec_conditions)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NecInput(lower, R.string.opennec_low, "opennec-low", Modifier.weight(1f)) { lower = it }
                NecInput(upper, R.string.opennec_high, "opennec-high", Modifier.weight(1f)) { upper = it }
            }
            NecInput(limit, R.string.opennec_swr_limit, "opennec-limit", Modifier.fillMaxWidth()) { limit = it }
            if (grid == null || swrLimit == null) HelpText(R.string.opennec_invalid_band)
            else if (segments == null || reference != null && referenceSegments == null) HelpText(R.string.opennec_invalid_model)
            else {
                val counts = segments.joinToString(" / ")
                Text(stringResource(R.string.opennec_sampling, counts), style = MaterialTheme.typography.bodySmall)
                if (referenceSegments != null && referenceSegments != segments) {
                    Text(stringResource(R.string.opennec_reference_sampling, referenceSegments.joinToString(" / ")),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (job == null) {
                fun start(optimize: Boolean) {
                    val frequencies = requireNotNull(grid)
                    val currentSegments = requireNotNull(segments)
                    comparison = null
                    optimization = null
                    status = null
                    job = scope.launch {
                        if (optimize) {
                            optimization = optimizeNecLengths(elements, requireNotNull(swrLimit)) { candidate ->
                                sweep(candidate, currentSegments, frequencies)
                            }
                            if (optimization == null) status = R.string.opennec_failed
                        } else {
                            val base = reference?.let { sweep(it, requireNotNull(referenceSegments), frequencies) }
                            val adjusted = if (reference == null || base != null) {
                                sweep(elements, currentSegments, frequencies)
                            } else null
                            if (adjusted == null) status = R.string.opennec_failed
                            else comparison = base to adjusted
                        }
                        job = null
                    }
                }
                Button(onClick = { start(false) }, enabled = canRun, modifier = Modifier.testTag("opennec-run")) {
                    Text(stringResource(R.string.opennec_run))
                }
                TextButton(onClick = { start(true) }, enabled = canRun, modifier = Modifier.testTag("opennec-optimize")) {
                    Text(stringResource(R.string.opennec_optimize))
                }
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton({
                    job?.cancel()
                    job = null
                    status = R.string.opennec_cancelled
                }, Modifier.testTag("opennec-cancel")) {
                    Text(stringResource(R.string.opennec_cancel))
                }
            }
            status?.let { HelpText(it) }
            comparison?.let { (base, adjusted) ->
                val threshold = requireNotNull(swrLimit)
                val closest = adjusted.indices.minBy { abs(adjusted[it].frequencyHz - targetHz) }
                val selected = adjusted[closest]
                val refPoint = base?.get(closest)
                Text(stringResource(R.string.opennec_at_frequency, matchingNumber(context, selected.frequencyHz / 1e6, 6)),
                    Modifier.testTag("opennec-result"))
                if (base != null) NecMetrics(R.string.opennec_reference, base, closest, threshold)
                NecMetrics(R.string.opennec_adjusted, adjusted, closest, threshold)
                NecPlots(selected, refPoint, adjusted, base, threshold)
            }
            optimization?.let { outcome ->
                val base = outcome.initial.points
                val best = outcome.best.points
                val selected = best.indices.minBy { abs(best[it].frequencyHz - targetHz) }
                Text(stringResource(if (outcome.reachedGoal) R.string.opennec_goal_reached else R.string.opennec_goal_missed,
                    matchingNumber(context, outcome.best.worstSwr, 3)), Modifier.testTag("opennec-optimization"))
                NecMetrics(R.string.opennec_adjusted, base, selected, outcome.goal)
                NecMetrics(R.string.opennec_proposed, best, selected, outcome.goal)
                outcome.best.elements.forEachIndexed { index, element ->
                    Text(stringResource(R.string.opennec_length_proposal, scalingElementLabel(index),
                        matchingNumber(context, outcome.initial.elements[index].length * 1000, 3),
                        matchingNumber(context, element.length * 1000, 3)))
                }
                NecPlots(best[selected], base[selected], best, base, outcome.goal, R.string.opennec_optimization_legend)
                if (appliedLengths == outcome.best.elements.map { it.length }) {
                    Text(stringResource(R.string.opennec_applied, OpenNec.VERSION), color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = { onApply(outcome.best.elements) },
                        enabled = outcome.best.elements != outcome.initial.elements, modifier = Modifier.testTag("opennec-apply")) {
                        Text(stringResource(R.string.opennec_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun NecInput(value: String, label: Int, tag: String, modifier: Modifier, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.acceptsDecimalEdit()) change(it.boundedNumericText()) },
        modifier.testTag(tag), label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

private fun mhzInput(hertz: Long): String = java.math.BigDecimal.valueOf(hertz, 6).stripTrailingZeros().toPlainString()

@Composable
private fun NecMetrics(title: Int, points: List<OpenNec.Point>, selected: Int, limit: Double) {
    val context = LocalContext.current
    val point = points[selected]
    fun number(value: Double) = matchingNumber(context, value, 3)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        SelectableText(stringResource(R.string.opennec_impedance, number(point.resistance), number(point.reactance)))
        SelectableText(stringResource(R.string.opennec_metrics, number(point.swr),
            number(point.forwardDbi), number(point.frontToBackDb)))
        val bands = buildList {
            points.forEachIndexed { index, current ->
                if (current.swr <= limit) {
                    if (index == 0 || points[index - 1].swr > limit) add(current.frequencyHz to current.frequencyHz)
                    else this[lastIndex] = last().first to current.frequencyHz
                }
            }
        }
        val ranges = bands.joinToString { (first, last) ->
            val start = matchingNumber(context, first / 1e6, 6)
            if (first == last) start else "$start–" + matchingNumber(context, last / 1e6, 6)
        }
        SelectableText(if (ranges.isEmpty()) stringResource(R.string.opennec_no_band)
            else stringResource(R.string.opennec_band_result, number(limit), ranges))
    }
}

@Composable
private fun NecPlots(
    selected: OpenNec.Point,
    referencePoint: OpenNec.Point?,
    adjusted: List<OpenNec.Point>,
    reference: List<OpenNec.Point>?,
    limit: Double,
    legend: Int = R.string.opennec_plot_legend,
) {
    val colors = MaterialTheme.colorScheme
    val swrTitle = stringResource(R.string.opennec_swr_plot)
    val azimuthTitle = stringResource(R.string.opennec_azimuth_plot)
    Text(swrTitle, style = MaterialTheme.typography.titleSmall)
    Canvas(Modifier.fillMaxWidth().height(135.dp).testTag("opennec-swr-plot")
        .semantics { contentDescription = swrTitle }) {
        val ceiling = maxOf(limit, adjusted.maxOf { it.swr }, reference?.maxOf { it.swr } ?: 1.0, 1.1)
        fun y(swr: Double) = size.height * (1.0 - (swr - 1.0) / (ceiling - 1.0)).toFloat()
        fun line(points: List<OpenNec.Point>, color: Color) {
            val dx = size.width / points.lastIndex
            points.zipWithNext().forEachIndexed { i, (a, b) ->
                drawLine(color, Offset(i * dx, y(a.swr)), Offset((i + 1) * dx, y(b.swr)), 2.dp.toPx())
            }
        }
        drawLine(colors.outline, Offset(0f, y(limit)), Offset(size.width, y(limit)))
        reference?.let { line(it, colors.onSurfaceVariant) }
        line(adjusted, colors.primary)
    }
    Text(azimuthTitle, style = MaterialTheme.typography.titleSmall)
    Canvas(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = azimuthTitle }) {
        val radius = size.minDimension * .46f
        val peak = maxOf(selected.azimuthDbi.max(), referencePoint?.azimuthDbi?.max() ?: -1000.0)
        drawCircle(colors.outline, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawLine(colors.outline, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
        fun line(point: OpenNec.Point, color: Color) {
            point.azimuthDbi.mapIndexed { index, gain ->
                val angle = index * OpenNec.AZIMUTH_STEP * PI / 180.0
                val r = radius * ((gain - peak + 40.0) / 40.0).coerceIn(0.0, 1.0)
                center + Offset((r * cos(angle)).toFloat(), (-r * sin(angle)).toFloat())
            }.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 2.dp.toPx()) }
        }
        referencePoint?.let { line(it, colors.onSurfaceVariant) }
        line(selected, colors.primary)
    }
    if (reference != null) HelpText(legend)
}
