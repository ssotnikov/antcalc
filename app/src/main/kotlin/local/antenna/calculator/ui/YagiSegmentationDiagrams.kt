package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element

@Composable
internal fun SegmentDiagram(elements: List<Element>, counts: List<Int>, feed: Int) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.seg_diagram)
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val elementNames = elements.indices.map { index ->
        if (elements.size <= 3) stringResource(R.string.seg_element, index + 1) else (index + 1).toString()
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.fillMaxWidth().height(128.dp + lineHeight * 2)
            .semantics { contentDescription = description }) {
            val gap = 5.dp.toPx()
            val names = elementNames.map { measurer.measure(it, style, maxLines = 1) }
            val totals = counts.map { measurer.measure("N = $it", style, maxLines = 1) }
            val yLabel = measurer.measure("+Y", style)
            val xLabel = measurer.measure("+X", style)
            val deltaLabel = measurer.measure("Δ", style)
            val top = names.maxOf { it.size.height } + gap
            val bottom = size.height - totals.maxOf { it.size.height } - gap
            val center = (top + bottom) / 2
            val origin = yLabel.size.width + 14.dp.toPx()
            val end = size.width - 4.dp.toPx()
            val axisYRight = center - xLabel.size.height - gap / 2
            val margin = 14.dp.toPx()
            val span = elements.last().position.takeIf { it > 0 } ?: 1.0
            val maxLength = elements.maxOf { it.length }
            val positions = elements.map { element ->
                if (elements.size == 1) size.width / 2
                else origin + margin + (end - origin - 2 * margin).coerceAtLeast(0f) *
                    (element.position / span).toFloat()
            }

            drawLine(colors.outline, Offset(origin, center), Offset(end, center))
            drawLine(colors.outline, Offset(end - gap, center - gap), Offset(end, center))
            drawLine(colors.outline, Offset(end - gap, center + gap), Offset(end, center))
            drawText(xLabel, colors.onSurfaceVariant, Offset(end - xLabel.size.width, axisYRight))
            val yAxisX = yLabel.size.width / 2f + gap
            val yAxisTop = top + yLabel.size.height
            drawLine(colors.outline, Offset(yAxisX, center - 10.dp.toPx()), Offset(yAxisX, yAxisTop))
            drawLine(colors.outline, Offset(yAxisX - gap, yAxisTop + gap), Offset(yAxisX, yAxisTop))
            drawLine(colors.outline, Offset(yAxisX + gap, yAxisTop + gap), Offset(yAxisX, yAxisTop))
            drawText(yLabel, colors.onSurfaceVariant, Offset(gap, top - gap / 2))

            // Keep the feed label, then add other labels only where their measured bounds fit.
            val occupied = mutableListOf<ClosedFloatingPointRange<Float>>()
            (listOf(feed) + elements.indices.filter { it != feed }).forEach { index ->
                val name = names[index]
                val total = totals[index]
                val width = maxOf(name.size.width, total.size.width).toFloat()
                val labelX = positions[index].coerceIn(width / 2, maxOf(width / 2, size.width - width / 2))
                val bounds = (labelX - width / 2 - gap)..(labelX + width / 2 + gap)
                if (occupied.none { it.start < bounds.endInclusive && bounds.start < it.endInclusive }) {
                    drawText(name, colors.onSurface, Offset(labelX - name.size.width / 2f, 0f))
                    drawText(total, colors.onSurfaceVariant, Offset(labelX - total.size.width / 2f, bottom + gap))
                    occupied += bounds
                }
            }

            var dimensionDrawn = false
            elements.forEachIndexed { index, element ->
                val x = positions[index]
                val length = (bottom - top) * (element.length / maxLength).toFloat()
                val start = center - length / 2
                val step = length / counts[index]
                repeat(counts[index]) { segment ->
                    val y = start + segment * step
                    val active = index == feed && segment == counts[index] / 2
                    val segmentGap = minOf(1.dp.toPx(), step / 4)
                    drawLine(if (active) colors.tertiary else colors.primary, Offset(x, y + segmentGap),
                        Offset(x, y + step - segmentGap), if (active) 4.dp.toPx() else 2.dp.toPx())
                }
                if (index == feed) drawCircle(colors.tertiary, 3.5.dp.toPx(), Offset(x, center))
                val right = positions.getOrNull(index + 1) ?: end
                val dimensionX = x + 9.dp.toPx()
                val labelX = dimensionX + 7.dp.toPx()
                if (!dimensionDrawn && counts[index] > 2 && step >= 4.dp.toPx() &&
                    labelX + deltaLabel.size.width + gap < right && start + step * 2 < axisYRight) {
                    val startY = start + step
                    bracket(dimensionX, startY, startY + step, colors.secondary)
                    drawText(deltaLabel, colors.secondary, Offset(labelX, startY + (step - deltaLabel.size.height) / 2))
                    dimensionDrawn = true
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            DiagramKey(R.string.seg_key_segment, false, Modifier.weight(1f))
            DiagramKey(R.string.seg_key_feed, true, Modifier.weight(1f))
        }
        Text(stringResource(R.string.seg_key_axes), style = style, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun DiagramKey(label: Int, source: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(13.dp)) {
            if (source) drawCircle(colors.tertiary, 3.5.dp.toPx())
            else drawLine(colors.primary, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx())
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private data class GroundCondition(val title: Int, val explanation: Int, val picture: GroundPicture)

private enum class GroundPicture { FREE, PERFECT, FINITE }

@Composable
internal fun GroundConditions() {
    val conditions = listOf(
        GroundCondition(R.string.seg_ground_free, R.string.seg_ground_free_hint, GroundPicture.FREE),
        GroundCondition(R.string.seg_ground_perfect_title, R.string.seg_ground_perfect_hint, GroundPicture.PERFECT),
        GroundCondition(R.string.seg_ground_real, R.string.seg_ground_real_hint, GroundPicture.FINITE),
    )
    val colors = MaterialTheme.colorScheme
    Text(stringResource(R.string.seg_ground_conditions), style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            conditions.forEach { condition ->
                HorizontalDivider()
                if (stacked) {
                    GroundIllustration(condition.picture, Modifier.width(146.dp))
                    GroundExplanation(condition, Modifier.fillMaxWidth())
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GroundIllustration(condition.picture, Modifier.weight(.36f))
                        GroundExplanation(condition, Modifier.weight(.64f))
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.seg_ground_height_hint), style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant)
}

@Composable
private fun GroundExplanation(condition: GroundCondition, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(condition.title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(condition.explanation), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroundIllustration(picture: GroundPicture, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val antenna = stringResource(R.string.seg_ground_antenna)
    val image = stringResource(R.string.seg_ground_image)
    val soil = stringResource(R.string.seg_ground_soil)
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Canvas(modifier.height(56.dp + lineHeight * 2)) {
        val wireY = size.height * .30f
        val surface = size.height * .54f
        val imageY = 2 * surface - wireY
        val start = size.width * .13f
        val end = size.width * .70f
        val middle = (start + end) / 2
        fun label(value: String, x: Float, y: Float) {
            val text = measurer.measure(value, style, maxLines = 1)
            drawText(text, colors.onSurfaceVariant, Offset(x - text.size.width / 2f, y))
        }
        label(antenna, middle, 1.dp.toPx())
        drawLine(colors.primary, Offset(start, wireY), Offset(end, wireY), 2.dp.toPx())
        if (picture == GroundPicture.FREE) return@Canvas
        if (picture == GroundPicture.FINITE) {
            drawRect(colors.surfaceVariant, topLeft = Offset(0f, surface),
                size = Size(size.width, size.height - surface))
            val spacing = 10.dp.toPx()
            repeat((size.width / spacing).toInt()) { index ->
                val x = index * spacing + 2.dp.toPx()
                drawLine(colors.outline, Offset(x, surface + 4.dp.toPx()),
                    Offset(x + 5.dp.toPx(), surface + 9.dp.toPx()))
            }
            label(soil, size.width / 2, surface + 13.dp.toPx())
        } else {
            drawLine(colors.primary, Offset(start, imageY), Offset(end, imageY), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
            label(image, middle, imageY + 3.dp.toPx())
        }
        drawLine(colors.outline, Offset(0f, surface), Offset(size.width, surface), 1.dp.toPx())
        val heightX = size.width * .79f
        bracket(heightX, wireY, surface, colors.secondary)
        val h = measurer.measure("h", style)
        drawText(h, colors.secondary, Offset(heightX + 5.dp.toPx(), (wireY + surface - h.size.height) / 2f))
    }
}

private fun DrawScope.bracket(x: Float, start: Float, end: Float, color: androidx.compose.ui.graphics.Color) {
    val cap = 3.dp.toPx()
    drawLine(color, Offset(x, start), Offset(x, end))
    drawLine(color, Offset(x - cap, start), Offset(x + cap, start))
    drawLine(color, Offset(x - cap, end), Offset(x + cap, end))
}
