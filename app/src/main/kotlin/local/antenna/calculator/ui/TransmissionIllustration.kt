package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import local.antenna.calculator.R

internal enum class TransmissionIllustration { WAVELENGTH, POWER, REFLECTION }

@Composable
internal fun TransmissionDiagram(
    style: TransmissionIllustration,
    upper: String,
    lower: String,
    detail: String?,
    fraction: Double?,
    caption: Int,
) {
    val colors = MaterialTheme.colorScheme
    val warm = if (colors.surface.luminance() < .5f) Color(0xFFF3BA80) else Color(0xFF824410)
    val ratio = fraction?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat()
    Surface(Modifier.fillMaxWidth(), color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(upper, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                Text(lower, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface, textAlign = TextAlign.End)
            }
            when (style) {
                TransmissionIllustration.WAVELENGTH -> {
                    Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                        val (left, right) = diagramEdges()
                        drawPath(wave(left, right, 1.4f, size.height * .13f, size.height * .24f), colors.onSurfaceVariant,
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx()))))
                        ratio?.coerceAtLeast(.12f)?.let { velocity ->
                            drawPath(wave(left, right, (1.4f / velocity).coerceAtMost(12f), size.height * .13f, size.height * .65f),
                                colors.primary, style = Stroke(2.dp.toPx()))
                        }
                        val baseline = size.height * .88f
                        drawLine(colors.outlineVariant, Offset(left, baseline), Offset(right, baseline), 1.dp.toPx())
                    }
                }
                TransmissionIllustration.POWER -> {
                    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                        val (left, right) = diagramEdges()
                        val center = 27.dp.toPx()
                        val shoulder = 36.dp.toPx().coerceAtMost((right - left) / 5)
                        drawCoax(left, right, center, 13.dp.toPx(), shoulder, ratio)
                        if (ratio != null && ratio < 1f) {
                            for (position in listOf(.33f, .5f, .67f)) {
                                val x = left + (right - left) * position
                                drawArrow(Offset(x, 46.dp.toPx()), Offset(x, 62.dp.toPx()), warm)
                            }
                        }
                    }
                    SelectableText(detail ?: stringResource(R.string.hero_feed_loss),
                        modifier = Modifier.align(Alignment.CenterHorizontally), color = warm)
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val (left, right) = diagramEdges()
                        val y = size.height / 2
                        drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), 4.dp.toPx(), StrokeCap.Round)
                        if (ratio != null && ratio > 0f) {
                            drawLine(colors.primary, Offset(left, y), Offset(left + (right - left) * ratio, y), 4.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
                TransmissionIllustration.REFLECTION -> {
                    Text(stringResource(R.string.hero_incident), Modifier.align(Alignment.CenterHorizontally),
                        color = colors.primary, style = MaterialTheme.typography.bodySmall)
                    Canvas(Modifier.fillMaxWidth().height(82.dp)) {
                        val (left, right) = diagramEdges()
                        val start = left + 15.dp.toPx()
                        val end = right - 15.dp.toPx()
                        val amplitude = 9.dp.toPx()
                        val incidentY = 12.dp.toPx()
                        val reflectedY = size.height - incidentY
                        drawTravellingWave(start, end, incidentY, amplitude, colors.primary, colors.outlineVariant)
                        drawCoax(left, right, size.height / 2, 5.dp.toPx(), 20.dp.toPx())
                        if (ratio != null && ratio > 0f) {
                            drawTravellingWave(end, start, reflectedY, amplitude * ratio, warm, colors.outlineVariant)
                        }
                    }
                    Text(stringResource(if (ratio == 0f) R.string.hero_no_reflection else R.string.hero_reflected),
                        Modifier.align(Alignment.CenterHorizontally), color = warm, style = MaterialTheme.typography.bodySmall)
                    SelectableText(stringResource(R.string.hero_reflect_formula),
                        Modifier.align(Alignment.CenterHorizontally), color = colors.primary,
                        style = MaterialTheme.typography.titleSmall)
                    detail?.let { SelectableText(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Text(stringResource(caption), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun DrawScope.diagramEdges() = Pair(size.width * .055f, size.width * .945f)

private fun wave(start: Float, end: Float, cycles: Float, amplitude: Float, y: Float) = Path().apply {
    for (index in 0..120) {
        val t = index / 120f
        val x = start + t * (end - start)
        val value = y - amplitude * sin(2 * PI * cycles * t).toFloat()
        if (index == 0) moveTo(x, value) else lineTo(x, value)
    }
}

private fun DrawScope.drawTravellingWave(start: Float, end: Float, y: Float, amplitude: Float, color: Color, axis: Color) {
    drawLine(axis, Offset(start, y), Offset(end, y), 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
    drawPath(wave(start, end, 3f, amplitude, y), color, style = Stroke(2.dp.toPx()))
    drawArrow(Offset(end - (end - start) * .025f, y + amplitude * .45f), Offset(end, y), color)
}

private fun DrawScope.drawArrow(from: Offset, tip: Offset, color: Color) {
    val delta = tip - from
    val direction = delta / delta.getDistance()
    val normal = Offset(-direction.y, direction.x)
    val base = tip - direction * 5.dp.toPx()
    val half = normal * 2.8.dp.toPx()
    drawLine(color, from, tip, 1.dp.toPx())
    drawPath(Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + half.x, base.y + half.y)
        lineTo(base.x - half.x, base.y - half.y)
        close()
    }, color)
}

private fun DrawScope.drawCoax(
    left: Float,
    right: Float,
    center: Float,
    radius: Float,
    shoulder: Float,
    power: Float? = null,
) {
    val bodyTop = center - radius
    val bodySize = Size(right - left, radius * 2)
    val rounding = CornerRadius(4.dp.toPx())
    val jacketEdge = Color(0xFF758E9C)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF57707D), Color(0xFF314653), Color(0xFF1A2A34)),
        bodyTop, center + radius), Offset(left, bodyTop), bodySize, rounding)
    drawRoundRect(jacketEdge, Offset(left, bodyTop), bodySize, rounding, style = Stroke(1.dp.toPx()))

    if (power != null) {
        val start = left + shoulder
        val width = right - shoulder - start
        val stripRadius = radius * .73f
        val strip = Path()
        for (index in 0..32) {
            val progress = index / 32f
            val x = start + width * progress
            val y = center - stripRadius * power.pow(progress)
            if (index == 0) strip.moveTo(x, y) else strip.lineTo(x, y)
        }
        for (index in 32 downTo 0) {
            val progress = index / 32f
            strip.lineTo(start + width * progress, center + stripRadius * power.pow(progress))
        }
        strip.close()
        drawPath(strip, Color(0xFF9ADFD0))
    }

    val connectorRadius = radius + 3.dp.toPx()
    val top = center - connectorRadius
    val bottom = center + connectorRadius
    val metal = Brush.verticalGradient(listOf(Color(0xFFA4B7C1), Color(0xFF5B7381), Color(0xFF3E5664), Color(0xFF8298A4)),
        top, bottom)
    for ((edge, direction) in listOf(left to 1f, right to -1f)) {
        val bevel = shoulder * .18f
        val neck = edge + direction * shoulder
        val connector = Path().apply {
            moveTo(edge, top + bevel)
            lineTo(edge + direction * bevel, top)
            lineTo(neck, top)
            lineTo(neck, bottom)
            lineTo(edge + direction * bevel, bottom)
            lineTo(edge, bottom - bevel)
            close()
        }
        drawPath(connector, metal)
        drawPath(connector, jacketEdge, style = Stroke(1.dp.toPx()))
        val inset = connectorRadius * .7f
        for (index in 0..4) {
            val x = edge + shoulder * (.28f + index * .12f) * direction
            drawLine(Color(0xFF344B58), Offset(x, center - inset), Offset(x, center + inset), 1.dp.toPx())
        }
    }
}
