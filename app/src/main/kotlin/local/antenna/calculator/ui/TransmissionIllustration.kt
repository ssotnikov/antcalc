package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

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
    Surface(Modifier.fillMaxWidth(), color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(upper, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                Text(lower, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = if (style == TransmissionIllustration.REFLECTION) colors.tertiary else colors.onSurface)
            }
            Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                val left = size.width * .055f
                val right = size.width - left
                val center = size.height / 2
                val width = right - left
                val fractionValue = fraction?.coerceIn(0.0, 1.0)?.toFloat()
                fun wave(cycles: Float, amplitude: Float, y: Float, phase: Float = 0f) = Path().apply {
                    for (index in 0..160) {
                        val t = index / 160f
                        val x = left + t * width
                        val value = y - amplitude * sin((2 * PI * cycles * t + phase)).toFloat()
                        if (index == 0) moveTo(x, value) else lineTo(x, value)
                    }
                }
                fun cable() {
                    drawRoundRect(colors.outline, Offset(left, center - 7.dp.toPx()), Size(width, 14.dp.toPx()), CornerRadius(4.dp.toPx()))
                    drawRoundRect(colors.surfaceVariant, Offset(left, center - 5.dp.toPx()), Size(width, 10.dp.toPx()), CornerRadius(3.dp.toPx()))
                    for (x in listOf(left, right - 17.dp.toPx()))
                        drawRoundRect(colors.outlineVariant, Offset(x, center - 10.dp.toPx()), Size(17.dp.toPx(), 20.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
                when (style) {
                    TransmissionIllustration.WAVELENGTH -> {
                        drawPath(wave(1.4f, size.height * .13f, size.height * .24f), colors.onSurfaceVariant,
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx()))))
                        val velocity = fractionValue?.coerceAtLeast(.12f)
                        if (velocity != null) drawPath(wave((1.4f / velocity).coerceAtMost(12f), size.height * .13f,
                            size.height * .65f), colors.primary, style = Stroke(2.dp.toPx()))
                        drawLine(colors.outlineVariant, Offset(left, size.height * .88f), Offset(right, size.height * .88f), 1.dp.toPx())
                    }
                    TransmissionIllustration.POWER -> {
                        cable()
                        fractionValue?.let { power ->
                            val input = 4.dp.toPx()
                            val output = input * power
                            val edge = left + 17.dp.toPx()
                            val end = right - 17.dp.toPx()
                            val fill = Path().apply {
                                moveTo(edge, center - input); lineTo(end, center - output)
                                lineTo(end, center + output); lineTo(edge, center + input); close()
                            }
                            drawPath(fill, colors.primary)
                            val y = size.height * .87f
                            drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), 4.dp.toPx())
                            if (power > 0f) drawLine(colors.primary, Offset(left, y), Offset(left + width * power, y), 4.dp.toPx())
                        }
                    }
                    TransmissionIllustration.REFLECTION -> {
                        cable()
                        drawPath(wave(3.2f, size.height * .12f, size.height * .2f), colors.primary, style = Stroke(2.dp.toPx()))
                        fractionValue?.let { gamma ->
                            val y = size.height * .8f
                            if (gamma > 0f) drawPath(wave(3.2f, size.height * .12f * gamma, y, PI.toFloat()),
                                colors.tertiary, style = Stroke(2.dp.toPx()))
                            else drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), 1.dp.toPx())
                        }
                    }
                }
            }
            detail?.let { SelectableText(it) }
            Text(stringResource(caption), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
