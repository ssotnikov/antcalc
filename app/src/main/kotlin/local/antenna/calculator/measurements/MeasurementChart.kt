package local.antenna.calculator.measurements

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.abs
import local.antenna.calculator.R

@Composable
internal fun MeasurementChart(
    traces: List<Pair<Int, MeasurementTrace>>,
    metric: SweepMetric,
    selectedHz: Double,
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
    val formatter = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3; isGroupingUsed = false } }
    Canvas(Modifier.fillMaxWidth().height(230.dp).semantics { contentDescription = label }
        .pointerInput(first, last, select) {
            detectTapGestures { tap ->
                val left = 52.dp.toPx()
                val width = size.width - left - 12.dp.toPx()
                if (width > 0) select(first + (last - first) * ((tap.x - left) / width).coerceIn(0f, 1f))
            }
        }) {
        val left = 52.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas
        val pad = if (bounds.minimum == bounds.maximum) 0.10 else (bounds.maximum - bounds.minimum) * 0.05
        val low = bounds.minimum - pad
        val high = bounds.maximum + pad
        fun x(hz: Double) = if (last == first) (left + right) / 2 else left + ((hz - first) / (last - first)).toFloat() * (right - left)
        fun y(value: Double) = bottom - (((value / bounds.scale - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()) * (bottom - top)
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
        drawContext.canvas.nativeCanvas.drawText(formatter.format(first / 1e6), left, bottom + 18.dp.toPx(), paint)
        if (last != first) {
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(formatter.format(last / 1e6), right, bottom + 18.dp.toPx(), paint)
        }
        val cursor = x(selectedHz)
        drawLine(colors.outline, Offset(cursor, top), Offset(cursor, bottom), strokeWidth = 1.dp.toPx())
        series.forEach { data ->
            val color = traceColors[data.slot]
            val path = Path()
            var started = false
            data.points.forEach { (hz, value) ->
                if (value != null && value.isFinite()) {
                    val x = x(hz)
                    val y = y(value)
                    if (started) path.lineTo(x, y) else path.moveTo(x, y)
                    started = true
                } else started = false
            }
            val effect = if (data.reactance) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = effect))
            val sweep = traces.first { it.first == data.slot }.second.sweep
            val index = sweep.nearestIndex(selectedHz)
            if (index != null) {
                val (hz, value) = data.points[index]
                if (value != null && value.isFinite()) drawCircle(color, 4.dp.toPx(), Offset(x(hz), y(value)))
            }
        }
    }
}

@Composable
internal fun measurementTraceColor(slot: Int) =
    if (slot == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

private data class Series(val slot: Int, val reactance: Boolean, val points: List<Pair<Double, Double?>>)
private data class Bounds(val minimum: Double, val maximum: Double, val scale: Double)
