package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.trimming.AdjustmentDirection

@Composable
fun AntennaDiagram(dipole: Boolean, modifier: Modifier = Modifier) {
    val conductor = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val description = stringResource(if (dipole) R.string.diagram_dipole else R.string.diagram_monopole)
    Canvas(modifier.fillMaxWidth().height(if (dipole) 112.dp else 108.dp).semantics { contentDescription = description }) {
        val stroke = 4.dp.toPx()
        val x = size.width / 2
        val tick = 4.dp.toPx()
        if (dipole) {
            val y = size.height * .50f
            val arm = measurer.measure("Lₐ", labelStyle)
            val dimensionY = arm.size.height + tick * 2
            for ((start, end) in listOf(size.width * .08f to x - 8.dp.toPx(), x + 8.dp.toPx() to size.width * .92f)) {
                drawLine(conductor, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                drawLine(guide, Offset(start, dimensionY), Offset(end, dimensionY), 1.dp.toPx())
                for (edge in listOf(start, end)) drawLine(guide, Offset(edge, dimensionY - tick), Offset(edge, dimensionY + tick), 1.dp.toPx())
                drawText(arm, topLeft = Offset((start + end - arm.size.width) / 2, 0f))
            }
            val total = measurer.measure("Lᵈ = 2Lₐ", labelStyle)
            val totalTop = size.height - total.size.height
            drawLine(guide, Offset(x, y + 6.dp.toPx()), Offset(x, totalTop - tick), 2.dp.toPx())
            drawText(total, topLeft = Offset(x - total.size.width / 2, totalTop))
        } else {
            val base = size.height * .72f
            val tip = 8.dp.toPx()
            val dimensionX = x + 24.dp.toPx()
            drawLine(conductor, Offset(x, tip), Offset(x, base), stroke, StrokeCap.Round)
            drawLine(guide, Offset(dimensionX, tip), Offset(dimensionX, base), 1.dp.toPx())
            for (edge in listOf(tip, base)) drawLine(guide, Offset(dimensionX - tick, edge), Offset(dimensionX + tick, edge), 1.dp.toPx())
            val label = measurer.measure("Lₛ", labelStyle)
            drawText(label, topLeft = Offset(dimensionX + tick * 2, (tip + base - label.size.height) / 2))
            drawCircle(conductor, 5.dp.toPx(), Offset(x, base))
            drawLine(guide, Offset(size.width * .15f, base), Offset(size.width * .85f, base), 2.dp.toPx())
            drawLine(conductor, Offset(x - 8.dp.toPx(), base + 6.dp.toPx()), Offset(size.width * .27f, size.height - 4.dp.toPx()), 2.dp.toPx())
            drawLine(conductor, Offset(x + 8.dp.toPx(), base + 6.dp.toPx()), Offset(size.width * .73f, size.height - 4.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
internal fun TuningDiagram(direction: AdjustmentDirection) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface)
    val shorten = direction == AdjustmentDirection.SHORTEN
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tuning_diagram_title), style = MaterialTheme.typography.titleMedium)
        val swr = stringResource(R.string.matching_swr)
        Canvas(Modifier.fillMaxWidth().height(134.dp)) {
            val left = size.width * .12f
            val right = size.width * .92f
            val baseline = size.height * .72f
            val top = size.height * .26f
            val measured = size.width * if (shorten) .36f else .66f
            val target = size.width * if (shorten) .66f else .36f
            val bottom = baseline - 8.dp.toPx()
            val stroke = 1.dp.toPx()
            drawLine(colors.outline, Offset(left, top), Offset(left, baseline), stroke)
            drawLine(colors.outline, Offset(left, baseline), Offset(right, baseline), stroke)
            val curve = Path().apply {
                moveTo(left, top)
                cubicTo(left + (measured - left) * .62f, top, measured - 14.dp.toPx(), bottom, measured, bottom)
                cubicTo(measured + 14.dp.toPx(), bottom, right - (right - measured) * .62f, top, right, top)
            }
            drawPath(curve, colors.primary, style = Stroke(2.dp.toPx()))
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            for ((x, symbol) in listOf(measured to "fₘ", target to "fₜ")) {
                drawLine(colors.outline, Offset(x, top), Offset(x, baseline), stroke, pathEffect = dash)
                val text = measurer.measure(symbol, label)
                drawText(text, topLeft = Offset(x - text.size.width / 2, baseline + 4.dp.toPx()))
            }
            val arrowY = top - 12.dp.toPx()
            val head = if (shorten) -6.dp.toPx() else 6.dp.toPx()
            drawLine(colors.tertiary, Offset(measured, arrowY), Offset(target, arrowY), 2.dp.toPx())
            drawLine(colors.tertiary, Offset(target + head, arrowY - 4.dp.toPx()), Offset(target, arrowY), 2.dp.toPx())
            drawLine(colors.tertiary, Offset(target + head, arrowY + 4.dp.toPx()), Offset(target, arrowY), 2.dp.toPx())
            drawText(measurer.measure(swr, label), topLeft = Offset(0f, 0f))
            drawText(measurer.measure("f", label), topLeft = Offset(right, baseline + 4.dp.toPx()))
        }
        HelpText(if (shorten) R.string.tuning_diagram_shorten else R.string.tuning_diagram_lengthen)
        HelpText(R.string.tuning_diagram_note)
    }
}
