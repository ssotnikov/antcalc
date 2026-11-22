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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.matching.matchingFromImpedance
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.nec.OpenNec
import local.antenna.calculator.nec.NecOptimization
import local.antenna.calculator.nec.optimizeNecLengths
import local.antenna.calculator.nec.NecDirectionRange
import local.antenna.calculator.nec.NecImpedanceShift
import local.antenna.calculator.nec.maximumNecShift
import local.antenna.calculator.nec.necDirectionRange
import local.antenna.calculator.nec.worstNecSwr
import local.antenna.calculator.state.boundedNumericText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class NecMeshComparison(val differences: List<Double>, val impedance: NecImpedanceShift)

@Composable
internal fun YagiNecPanel(
    reference: List<Element>?,
    elements: List<Element>,
    targetHz: Long,
    blocked: Int?,
    appliedLengths: List<Double>?,
    sweep: suspend (List<Element>, List<Int>, List<Long>) -> List<OpenNec.Point>? =
        { geometry, segments, frequencies -> OpenNec.sweep(geometry, segments, 1, frequencies) },
    onSegmentation: ((ArrayList<String>) -> Unit)? = null,
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
    var meshExpanded by rememberSaveable { mutableStateOf(false) }
    var meshDifferences by remember(reference, elements, targetHz, lower, upper, limit) {
        mutableStateOf<NecMeshComparison?>(null)
    }
    var direction by remember(reference, elements, targetHz, lower, upper, limit) {
        mutableStateOf<NecDirectionRange?>(null)
    }
    var job by remember { mutableStateOf<Job?>(null) }
    val lowHz = scalingFrequency(lower)
    val highHz = scalingFrequency(upper)
    val swrLimit = scalingDecimal(limit)?.takeIf { it in 1.0..99.0 }
    val grid = if (lowHz != null && highHz != null && highHz - lowHz >= 20) {
        (0..20).map { lowHz + (highHz - lowHz) * it / 20 }
    } else null
    val plan = grid?.last()?.let { OpenNec.segmentation(elements, it) }
    val segments = plan?.takeIf { it.issue == null }?.counts
    val referencePlan = grid?.last()?.let { highest -> reference?.let { OpenNec.segmentation(it, highest) } }
    val referenceSegments = referencePlan?.takeIf { it.issue == null }?.counts
    val finer = segments?.let { OpenNec.finerSegments(elements, it) }
    val canRun = grid != null && segments != null && swrLimit != null &&
        (reference == null || referenceSegments != null)

    LaunchedEffect(reference, elements, targetHz, lower, upper, limit) {
        if (job != null) status = R.string.opennec_inputs_changed
        job?.cancel()
        job = null
        comparison = null
        optimization = null
        meshDifferences = null
        direction = null
    }
    val colors = MaterialTheme.colorScheme
    val actionColors = ButtonDefaults.buttonColors(
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
    )
    if (!expanded) {
        Button(onClick = { expanded = true }, modifier = Modifier.testTag("opennec-expand"),
            colors = actionColors) {
            Text(stringResource(R.string.opennec_check))
        }
        return
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.opennec_panel_title), style = MaterialTheme.typography.titleMedium)
            HelpText(R.string.opennec_conditions, OpenNec.VERSION)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NecInput(lower, R.string.opennec_low, "opennec-low", Modifier.weight(1f)) { lower = it }
                NecInput(upper, R.string.opennec_high, "opennec-high", Modifier.weight(1f)) { upper = it }
            }
            NecInput(limit, R.string.opennec_swr_limit, "opennec-limit", Modifier.fillMaxWidth()) { limit = it }
            if (grid == null || swrLimit == null) HelpText(R.string.opennec_invalid_band)
            else if (segments == null || reference != null && referenceSegments == null) {
                HelpText(R.string.opennec_invalid_model)
                listOfNotNull(plan, referencePlan).filter { it.issue != null }.forEach { SegmentationIssue(it) }
            }
            else {
                val counts = segments.joinToString(" / ")
                Text(stringResource(R.string.opennec_sampling, counts), style = MaterialTheme.typography.bodySmall)
                if (referenceSegments != null && referenceSegments != segments) {
                    Text(stringResource(R.string.opennec_reference_sampling, referenceSegments.joinToString(" / ")),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (segments != null && lowHz != null && highHz != null) {
                PanelButton(meshExpanded, { meshExpanded = !meshExpanded }, Modifier.testTag("opennec-mesh-expand")) {
                    Text(stringResource(R.string.seg_panel))
                }
                if (meshExpanded) {
                    if (reference != null && referenceSegments != null) {
                        Text(stringResource(R.string.opennec_reference), style = MaterialTheme.typography.titleSmall)
                        SegmentTable(reference, referenceSegments, referencePlan.wavelength, 1)
                        if (onSegmentation != null) TextButton({
                            onSegmentation(segmentationSeed(reference, lowHz, highHz, 1))
                        }) { Text(stringResource(R.string.seg_open)) }
                    }
                    Text(stringResource(R.string.opennec_adjusted), style = MaterialTheme.typography.titleSmall)
                    SegmentTable(elements, segments, plan.wavelength, 1)
                    if (onSegmentation != null) TextButton({
                        onSegmentation(segmentationSeed(elements, lowHz, highHz, 1))
                    }, Modifier.testTag("opennec-open-segmentation")) { Text(stringResource(R.string.seg_open)) }
                    if (finer == null) HelpText(R.string.seg_no_finer)
                }
            }
            if (job == null) {
                fun start(optimize: Boolean, refine: Boolean = false) {
                    val frequencies = requireNotNull(grid)
                    val currentSegments = requireNotNull(segments)
                    if (refine) meshDifferences = null else {
                        comparison = null
                        optimization = null
                        direction = null
                    }
                    status = null
                    job = scope.launch(start = CoroutineStart.LAZY) {
                        if (refine) {
                            val coarse = sweep(elements, currentSegments, frequencies)
                            val fine = coarse?.let { sweep(elements, requireNotNull(finer), frequencies) }
                            ensureActive()
                            meshDifferences = if (coarse != null && fine != null) {
                                maximumNecShift(coarse, fine, frequencies)?.let { shift ->
                                    val samples = coarse.zip(fine)
                                    NecMeshComparison(listOf(
                                        samples.maxOf { (a, b) -> abs(a.resistance - b.resistance) },
                                        samples.maxOf { (a, b) -> abs(a.reactance - b.reactance) },
                                        samples.maxOf { (a, b) -> abs(a.swr - b.swr) },
                                        samples.maxOf { (a, b) -> abs(a.forwardDbi - b.forwardDbi) },
                                        samples.maxOf { (a, b) -> abs(a.frontToBackDb - b.frontToBackDb) },
                                    ), shift)
                                }
                            } else null
                            if (meshDifferences == null) status = R.string.opennec_direction_incomplete
                        } else if (optimize) {
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
                    job?.start()
                }
                Button(onClick = { start(false) }, enabled = canRun, modifier = Modifier.testTag("opennec-run"),
                    colors = actionColors) {
                    Text(stringResource(R.string.opennec_run))
                }
                if (meshExpanded && finer != null) {
                    Button(onClick = { start(false, refine = true) }, enabled = canRun,
                        modifier = Modifier.testTag("opennec-mesh-compare"), colors = actionColors) {
                        Text(stringResource(R.string.seg_compare))
                    }
                }
                val initial = optimization?.initial
                val best = optimization?.best
                val baselineGeometry = initial?.elements ?: reference
                val candidateGeometry = best?.elements ?: elements
                val baselinePoints = initial?.points ?: comparison?.first
                val candidatePoints = best?.points ?: comparison?.second
                if (baselineGeometry != null && baselinePoints != null && candidatePoints != null) {
                    val baselineFine = (if (initial != null) segments else referenceSegments)?.let {
                        OpenNec.finerSegments(baselineGeometry, it)
                    }
                    val candidateFine = segments?.let { OpenNec.finerSegments(candidateGeometry, it) }
                    if (baselineFine == null || candidateFine == null) {
                        HelpText(R.string.opennec_direction_incomplete)
                    } else {
                        Button(onClick = {
                            val frequencies = requireNotNull(grid)
                            direction = null
                            status = null
                            job = scope.launch(start = CoroutineStart.LAZY) {
                                val base = sweep(baselineGeometry, baselineFine, frequencies)
                                val candidate = base?.let { sweep(candidateGeometry, candidateFine, frequencies) }
                                ensureActive()
                                direction = if (base != null && candidate != null) {
                                    necDirectionRange(baselinePoints, candidatePoints, base, candidate, frequencies)
                                } else null
                                if (direction == null) status = R.string.opennec_direction_incomplete
                                job = null
                            }
                            job?.start()
                        }, enabled = canRun, modifier = Modifier.testTag("opennec-direction"), colors = actionColors) {
                            Text(stringResource(R.string.opennec_direction_check))
                        }
                    }
                }
                Button(onClick = { start(true) }, enabled = canRun, modifier = Modifier.testTag("opennec-optimize"),
                    colors = actionColors) {
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
            if (meshExpanded && segments != null && finer != null) meshDifferences?.let { changes ->
                Text(stringResource(R.string.seg_change_heading), modifier = Modifier.testTag("opennec-mesh-result"),
                    style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.seg_mesh_pair, segments.joinToString(" / "), finer.joinToString(" / ")))
                val metrics = listOf("R (Ω)", "X (Ω)", stringResource(R.string.seg_swr),
                    stringResource(R.string.seg_gain), stringResource(R.string.seg_fb))
                changes.differences.forEachIndexed { index, change ->
                    Text(stringResource(R.string.seg_change, metrics[index], matchingNumber(context, change, 3)))
                }
                SelectableText(stringResource(R.string.opennec_mesh_impedance,
                    matchingNumber(context, changes.impedance.ohms, 3),
                    matchingNumber(context, changes.impedance.frequencyHz / 1e6, 6)))
            }
            direction?.let { result ->
                if (result.indeterminate) HelpText(R.string.opennec_direction_indeterminate)
                SelectableText(stringResource(R.string.opennec_direction_range,
                    matchingNumber(context, result.low, 4), matchingNumber(context, result.high, 4)))
                HelpText(R.string.opennec_direction_help)
            }
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
    AppTextField(value, { if (it.acceptsDecimalEdit()) change(it.boundedNumericText()) },
        modifier.testTag(tag), label = stringResource(label), singleLine = true,
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
        val worst = worstNecSwr(points)
        SelectableText(stringResource(R.string.opennec_worst_swr, number(worst.swr),
            matchingNumber(context, worst.frequencyHz / 1e6, 6)))
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
    NecSmithPlot(selected.frequencyHz, adjusted, reference, limit, legend)
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
}

@Composable
private fun NecSmithPlot(
    selectedHz: Long,
    adjusted: List<OpenNec.Point>,
    reference: List<OpenNec.Point>?,
    limit: Double,
    legend: Int,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val title = stringResource(R.string.opennec_smith_plot)
    val sweeps = remember(adjusted, reference) {
        listOfNotNull(reference, adjusted).map { series ->
            S11Sweep(50.0, series.map { point ->
                val gamma = checkNotNull(matchingFromImpedance(point.resistance, point.reactance, 50.0).complex)
                S11Point(point.frequencyHz.toDouble(), gamma.real, gamma.imaginary)
            })
        }
    }
    val lineColors = if (reference == null) listOf(colors.primary)
        else listOf(colors.onSurfaceVariant, colors.primary)
    Text(title, style = MaterialTheme.typography.titleSmall)
    SmithComparisonGraphic(sweeps, lineColors, selectedHz.toDouble(), limit, title,
        format = { matchingNumber(context, it, 1) }, modifier = Modifier.testTag("opennec-smith-plot"))
    if (reference != null) HelpText(legend)
    else HelpText(R.string.opennec_smith_single_trace)
    Text(stringResource(R.string.opennec_smith_markers, matchingNumber(context, selectedHz / 1e6, 6)),
        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    val boundaryText = if (limit == 1.0) stringResource(R.string.opennec_smith_center_only)
        else stringResource(R.string.opennec_smith_boundary, matchingNumber(context, limit, 3))
    Text(boundaryText, style = MaterialTheme.typography.bodySmall, color = Color(smithPalette().load))
    HelpText(R.string.opennec_smith_grid)
}
