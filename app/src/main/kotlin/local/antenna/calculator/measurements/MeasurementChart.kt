package local.antenna.calculator.measurements

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.abs
import local.antenna.calculator.R
import local.antenna.calculator.core.measurements.BandEdgeKind
import local.antenna.calculator.core.measurements.SwrBandwidth

internal const val BandwidthFillAlpha = 0.28f

@Composable
internal fun MeasurementChart(
    traces: List<Pair<Int, MeasurementTrace>>,
    metric: SweepMetric,
    selectedHz: Double,
    activeSlot: Int,
    bandwidth: SwrBandwidth? = null,
    select: (Double) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val traceColors = listOf(measurementTraceColor(0), measurementTraceColor(1))
    val locale = LocalConfiguration.current.locales[0]
    val label = stringResource(R.string.sweep_graph_description, stringResource(metric.title))
    val first = traces.minOf { it.second.sweep.points.first().hertz }
    val last = traces.maxOf { it.second.sweep.points.last().hertz }
    val series = remember(traces, metric) {
        traces.flatMap { (slot, trace) ->
            val components = if (metric == SweepMetric.IMPEDANCE) listOf(false, true) else listOf(false)
            components.map { reactance ->
                Series(slot, reactance, trace.sweep.points.map { it.hertz to metric.value(trace, it, reactance) })
            }
        }
    }
    val bounds = remember(series, metric) {
        val values = series.flatMap { it.points }.mapNotNull { it.second?.takeIf(Double::isFinite) }
        val minimum = if (metric == SweepMetric.SWR) 1.0 else values.minOrNull() ?: 0.0
        val maximum = values.maxOrNull() ?: minimum
        val scale = maxOf(1.0, abs(minimum), abs(maximum))
        Bounds(minimum / scale, maximum / scale, scale)
    }
    val estimatedEdges = remember(bandwidth) {
        bandwidth?.bands.orEmpty().flatMap { band -> listOf(band.lower, band.upper) }
            .filter { it.kind == BandEdgeKind.ESTIMATED }.map { it.hertz }
    }
    val formatter = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3; isGroupingUsed = false } }
    val frequencyFormat = remember(locale) { MeasurementNumberFormat(locale) }
    val axisFrequency = frequencyFormat.megahertz(selectedHz)
    val selectedFrequency = stringResource(R.string.sweep_mhz, axisFrequency)
    val selectFrequency by rememberUpdatedState(select)
    var dragY by remember(first, last, activeSlot) { mutableStateOf<Float?>(null) }

    Canvas(Modifier.fillMaxWidth().height(230.dp).testTag("sweep-chart")
        .semantics {
            contentDescription = label
            stateDescription = selectedFrequency
        }
        .pointerInput(first, last, activeSlot) {
            detectTapGestures { tap ->
                val left = PlotLeft.toPx()
                val width = size.width - left - PlotRight.toPx()
                if (width > 0) selectFrequency(plotFrequency(tap.x, left, width, first, last))
            }
        }
        .pointerInput(first, last, activeSlot) {
            val left = PlotLeft.toPx()
            fun move(position: Offset) {
                val width = size.width - left - PlotRight.toPx()
                if (width <= 0) return
                dragY = position.y
                selectFrequency(plotFrequency(position.x, left, width, first, last))
            }
            detectHorizontalDragGestures(
                onDragStart = ::move,
                onDragEnd = { dragY = null },
                onDragCancel = { dragY = null },
                onHorizontalDrag = { change, _ -> move(change.position) },
            )
        }) {
        val left = PlotLeft.toPx()
        val right = size.width - PlotRight.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas
        val pad = if (bounds.minimum == bounds.maximum) 0.10 else (bounds.maximum - bounds.minimum) * 0.05
        val low = bounds.minimum - pad
        val high = bounds.maximum + pad
        fun x(hz: Double) = if (last == first) (left + right) / 2 else left + ((hz - first) / (last - first)).toFloat() * (right - left)
        fun y(value: Double) = bottom - (((value / bounds.scale - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()) * (bottom - top)
        bandwidth?.bands?.forEach { band ->
            val start = x(band.lower.hertz)
            val end = x(band.upper.hertz)
            if (end > start) {
                drawRect(colors.primary.copy(alpha = BandwidthFillAlpha), Offset(start, top), Size(end - start, bottom - top))
            }
            val edgeStyle = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawLine(colors.primary, Offset(start, top), Offset(start, bottom), 1.5.dp.toPx(), pathEffect = edgeStyle)
            if (end > start) {
                drawLine(colors.primary, Offset(end, top), Offset(end, bottom), 1.5.dp.toPx(), pathEffect = edgeStyle)
            }
        }
        bandwidth?.threshold?.takeIf { it / bounds.scale in low..high }?.let {
            val thresholdY = y(it)
            drawLine(colors.primary, Offset(left, thresholdY), Offset(right, thresholdY),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onSurfaceVariant.toArgb()
            textSize = 11.dp.toPx()
        }
        for (step in 0..3) {
            val lineY = bottom - (bottom - top) * step / 3
            val axisValue = (low + (high - low) * step / 3) * bounds.scale
            drawLine(colors.outlineVariant, Offset(left, lineY), Offset(right, lineY))
            paint.textAlign = Paint.Align.RIGHT
            val text = if (abs(axisValue) < 1e5) formatter.format(axisValue) else "%.1e".format(locale, axisValue)
            drawContext.canvas.nativeCanvas.drawText(text, left - 5.dp.toPx(), lineY + 4.dp.toPx(), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        if (metric.axis.isNotEmpty()) drawContext.canvas.nativeCanvas.drawText(metric.axis, left + 5.dp.toPx(), top + 12.dp.toPx(), paint)
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val cursor = x(selectedHz)
        val halfLabel = paint.measureText(axisFrequency) / 2
        val labelX = cursor.coerceIn(halfLabel, maxOf(halfLabel, size.width - halfLabel))
        val axisY = bottom + 18.dp.toPx()
        val gap = 5.dp.toPx()
        val firstLabel = frequencyFormat.megahertz(first)
        val lastLabel = frequencyFormat.megahertz(last)
        if (first != selectedHz && left + paint.measureText(firstLabel) + gap < labelX - halfLabel) {
            nativeCanvas.drawText(firstLabel, left, axisY, paint)
        }
        if (last != selectedHz && right - paint.measureText(lastLabel) - gap > labelX + halfLabel) {
            paint.textAlign = Paint.Align.RIGHT
            nativeCanvas.drawText(lastLabel, right, axisY, paint)
        }
        paint.textAlign = Paint.Align.CENTER
        paint.color = colors.primary.toArgb()
        nativeCanvas.drawText(axisFrequency, labelX, axisY, paint)
        drawLine(colors.outline, Offset(cursor, top), Offset(cursor, bottom), strokeWidth = 1.dp.toPx())
        drawLine(colors.primary, Offset(cursor, bottom), Offset(cursor, bottom + 4.dp.toPx()))
        val pointerY = dragY
        var tooltipAnchor: Offset? = null
        series.forEach { data ->
            val color = traceColors[data.slot]
            val path = Path()
            var started = false
            var edgeIndex = 0
            data.points.forEach { (hz, value) ->
                val visibleValue = value != null && (value.isFinite() || bandwidth != null && value == Double.POSITIVE_INFINITY)
                if (visibleValue) {
                    while (edgeIndex < estimatedEdges.size && estimatedEdges[edgeIndex] <= hz) {
                        if (started && bandwidth != null) {
                            path.lineTo(x(estimatedEdges[edgeIndex]), y(bandwidth.threshold))
                        }
                        edgeIndex++
                    }
                    if (started) path.lineTo(x(hz), y(value)) else path.moveTo(x(hz), y(value))
                    started = true
                } else started = false
            }
            val effect = if (data.reactance) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = effect))
            val sweep = traces.first { it.first == data.slot }.second.sweep
            val index = sweep.nearestIndex(selectedHz)
            if (index != null) {
                val (hz, value) = data.points[index]
                if (value != null && value.isFinite()) {
                    val marker = Offset(x(hz), y(value))
                    drawCircle(color, 4.dp.toPx(), marker)
                    if (pointerY != null && data.slot == activeSlot &&
                        tooltipAnchor?.let { abs(marker.y - pointerY) < abs(it.y - pointerY) } != false
                    ) tooltipAnchor = marker
                }
            }
        }
        if (pointerY != null) {
            val anchor = tooltipAnchor ?: Offset(cursor, pointerY.coerceIn(top, bottom))
            paint.textAlign = Paint.Align.LEFT
            paint.color = colors.onSurface.toArgb()
            val textWidth = paint.measureText(selectedFrequency)
            val padding = 6.dp.toPx()
            val bubbleWidth = textWidth + 2 * padding
            val bubbleHeight = paint.descent() - paint.ascent() + 2 * padding
            val preferredX = if (anchor.x + gap + bubbleWidth <= size.width) anchor.x + gap
                else anchor.x - gap - bubbleWidth
            val bubbleX = preferredX.coerceIn(0f, maxOf(0f, size.width - bubbleWidth))
            val bubbleY = (anchor.y - bubbleHeight - gap).coerceIn(0f, maxOf(0f, bottom - bubbleHeight))
            drawRoundRect(colors.surfaceContainer, Offset(bubbleX, bubbleY), Size(bubbleWidth, bubbleHeight),
                CornerRadius(5.dp.toPx()))
            nativeCanvas.drawText(selectedFrequency, bubbleX + padding, bubbleY + padding - paint.ascent(), paint)
        }
    }
}

@Composable
internal fun measurementTraceColor(slot: Int) =
    when {
        slot == 0 -> MaterialTheme.colorScheme.primary
        MaterialTheme.colorScheme.surface.luminance() < 0.5f -> Color(0xFFF4C28F)
        else -> Color(0xFF935114)
    }

private data class Series(val slot: Int, val reactance: Boolean, val points: List<Pair<Double, Double?>>)
private data class Bounds(val minimum: Double, val maximum: Double, val scale: Double)

private val PlotLeft = 52.dp
private val PlotRight = 12.dp

private fun plotFrequency(x: Float, left: Float, width: Float, first: Double, last: Double): Double =
    first + (last - first) * ((x - left) / width).coerceIn(0f, 1f)
