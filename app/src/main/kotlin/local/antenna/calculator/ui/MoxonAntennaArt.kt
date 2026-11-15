package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.MoxonDimensions

@Composable
internal fun MoxonAntennaArt(
    view: MoxonView,
    dimensions: MoxonDimensions?,
    modifier: Modifier = Modifier,
    schematic: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val driven = colors.primary
    val reflector = colors.secondary
    val feed = colors.tertiary
    val frame = colors.outline
    val description = stringResource(if (schematic) R.string.moxon_geometry_note else R.string.moxon_image)
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp)
    Canvas(modifier.fillMaxWidth().height(if (schematic) 206.dp else 154.dp)
        .semantics { contentDescription = description }
        .testTag(if (schematic) "moxon-schematic" else "moxon-art-${view.name}")) {
        val scale = minOf(size.width / 650f, size.height / 280f)
        val center = Offset(size.width / 2, size.height / 2)
        val upright = !schematic && view in verticalViews
        fun point(x: Float, y: Float): Offset = center + when {
            schematic -> Offset(x, y) * scale
            upright -> Offset(.19f * x - 1.42f * y, -.47f * x - .08f * y) * scale
            else -> Offset(x - .55f * y, .16f * x + .66f * y) * scale
        }
        fun segment(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), width * scale, StrokeCap.Round)
        fun trace(color: Color, width: Float, vararg points: Pair<Float, Float>, rounded: Boolean = false) {
            val vertices = points.map { (x, y) -> point(x, y) }
            val path = Path().apply {
                moveTo(vertices.first().x, vertices.first().y)
                if (rounded) for (index in 1 until vertices.lastIndex) {
                    val previous = vertices[index - 1]
                    val current = vertices[index]
                    val next = vertices[index + 1]
                    val start = current + (previous - current) * .075f
                    val end = current + (next - current) * .075f
                    lineTo(start.x, start.y)
                    quadraticTo(current.x, current.y, end.x, end.y)
                } else vertices.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(vertices.last().x, vertices.last().y)
            }
            drawPath(path, color, style = Stroke(width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fun mark(text: String, x: Float, y: Float) {
            val layout = measurer.measure(text, style = style)
            val location = point(x, y)
            drawText(layout, topLeft = location - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        if (view == MoxonView.HYBRID && !schematic) {
            fun hybridPoint(position: Double, transverse: Double) =
                point(position.toFloat() * .55f - 257f, transverse.toFloat() * .57f)
            fun wire(x1: Double, y1: Double, x2: Double, y2: Double, color: Color, width: Float = 7f) {
                drawLine(color, hybridPoint(x1, y1), hybridPoint(x2, y2), width * scale, StrokeCap.Round)
            }
            wire(-24.0, 0.0, 957.0, 0.0, frame, 13f)
            wire(0.0, -350.0, 0.0, 350.0, reflector)
            for (tip in listOf(-350.0, 350.0)) {
                val feedY = if (tip > 0) 13.0 else -13.0
                wire(0.0, tip, 150.0, tip, reflector)
                wire(271.0, tip, 165.0, tip, driven)
                wire(271.0, feedY, 271.0, tip, driven)
                drawCircle(feed, 5.5f * scale, hybridPoint(271.0, feedY))
            }
            moxonSatelliteYagi.forEachIndexed { i, (position, length) ->
                wire(position, -length / 2, position, length / 2, if (i == 0) feed else reflector)
            }
            return@Canvas
        }
        val width = 203f
        val depth = dimensions?.let { (width * 2 * it.depthMm / it.widthMm).toFloat().coerceIn(102f, 174f) } ?: 143f
        val front = -depth / 2
        val rear = depth / 2
        val total = dimensions?.let { it.drivenTailMm + it.gapMm + it.reflectorTailMm }
        val driveEnd = front + (dimensions?.let { (it.drivenTailMm / checkNotNull(total)).toFloat() } ?: .33f) * depth
        val reflectEnd = rear - (dimensions?.let { (it.reflectorTailMm / checkNotNull(total)).toFloat() } ?: .46f) * depth
        val sides = listOf(-width, width)
        if (!schematic) when (view) {
            MoxonView.PVC_FRAME, MoxonView.PRINTED_VERTICAL -> {
                val edge = width - 9f
                trace(frame, 13f, -edge to front, -edge to rear, edge to rear, edge to front, -edge to front, rounded = true)
                segment(0f, front - 11f, 0f, rear + 19f, frame, 11f)
                if (view == MoxonView.PRINTED_VERTICAL) {
                    segment(-edge, 0f, edge, 0f, frame, 10f)
                    trace(feed, 4f, -edge to 0f, 0f to 0f, 0f to front)
                }
            }
            MoxonView.WIRE_LIGHT, MoxonView.PRINT_HUB, MoxonView.YAMA_HUB -> {
                val inset = if (view == MoxonView.YAMA_HUB) 30f else 0f
                for (x in sides) for (y in listOf(front, rear)) {
                    val end = x - if (x > 0) inset else -inset
                    segment(0f, 0f, end, y, frame, 7f)
                    if (inset > 0) segment(end, y, x, y, feed, 3f)
                }
                drawCircle(frame, (if (view == MoxonView.PRINT_HUB) 16 else 10) * scale, center)
                if (view == MoxonView.WIRE_LIGHT) segment(0f, 0f, 0f, front, frame, 8f)
            }
            MoxonView.CB_PENNANT, MoxonView.CB_VERTICAL, MoxonView.MAST_VERTICAL -> {
                segment(-width - 35f, 0f, width + 25f, 0f, frame, 10f)
                if (view == MoxonView.MAST_VERTICAL) {
                    for (y in listOf(front, rear)) trace(frame, 3f, (width + 25f) to 0f, width to y, -width to y)
                } else {
                    for (x in sides) segment(x, front - 8f, x, rear + 8f, frame, 7f)
                    if (view == MoxonView.CB_PENNANT) segment(33f, 0f, 33f, front - 7f, frame, 9f)
                    trace(feed, 3f, 0f to front, 0f to (front + 14f), -35f to -11f, (-width - 28f) to -11f)
                }
            }
            MoxonView.HOUSING -> {
                val location = point(0f, 0f)
                drawRoundRect(frame, location - Offset(54f, 28f) * scale, Size(108f * scale, 56f * scale))
                drawRoundRect(colors.surface, location - Offset(31f, 13f) * scale, Size(62f * scale, 26f * scale))
            }
            MoxonView.ROD_A, MoxonView.ROD_B, MoxonView.FLAT_STOCK, MoxonView.PVC_BOOM,
            MoxonView.STEPPED_H, MoxonView.STEPPED_V, MoxonView.PORTABLE_TUBE ->
                segment(0f, front - 12f, 0f, rear + 15f, frame, 14f)
            MoxonView.GENERIC, MoxonView.WIRE_OUTLINE, MoxonView.HYBRID -> Unit
        }
        val rounded = !schematic && (view == MoxonView.ROD_A || view == MoxonView.ROD_B || view == MoxonView.PORTABLE_TUBE)
        val thickness = when {
            schematic -> 7f
            view == MoxonView.FLAT_STOCK -> 13f
            rounded || view == MoxonView.STEPPED_H || view == MoxonView.STEPPED_V -> 9f
            else -> 5f
        }
        for (x in sides) {
            if (!schematic) segment(x, driveEnd, x, reflectEnd, frame, 3f)
            trace(driven, thickness, (if (x < 0) -10f else 10f) to front, x to front, x to driveEnd, rounded = rounded)
        }
        trace(reflector, thickness, -width to reflectEnd, -width to rear, width to rear, width to reflectEnd, rounded = rounded)
        if (!schematic && view in steppedViews) {
            for (sign in listOf(-1f, 1f)) {
                segment(sign * 22f, front, sign * 92f, front, driven, 14f)
                segment(sign * 22f, rear, sign * 92f, rear, reflector, 14f)
                if (view == MoxonView.PORTABLE_TUBE) for (y in listOf(front, rear)) {
                    drawCircle(frame, 5f * scale, point(sign * 152f, y))
                }
            }
        }
        for (x in listOf(-10f, 10f)) drawCircle(feed, 5f * scale, point(x, front))
        if (schematic) {
            fun bracket(x: Float, y1: Float, y2: Float, text: String, tone: Color) {
                segment(x, y1, x, y2, tone, 2f)
                for (y in listOf(y1, y2)) segment(x - 5f, y, x + 5f, y, tone, 2f)
                mark(text, x + if (x < 0) -23f else 23f, (y1 + y2) / 2)
            }
            bracket(-225f, front, driveEnd, "B", frame)
            bracket(-225f, driveEnd, reflectEnd, "C", feed)
            bracket(-225f, reflectEnd, rear, "D", frame)
            bracket(227f, front, rear, "E", frame)
            val bottom = rear + 26f
            segment(-width, bottom, width, bottom, frame, 2f)
            for (x in sides) segment(x, bottom - 5f, x, bottom + 5f, frame, 2f)
            mark("A", 0f, bottom + 20f)
            trace(feed, 3f, 0f to (front - 8f), 0f to (front - 35f))
            trace(feed, 3f, -7f to (front - 27f), 0f to (front - 35f), 7f to (front - 27f))
        }
    }
}

private val verticalViews = setOf(
    MoxonView.PVC_FRAME, MoxonView.PRINTED_VERTICAL, MoxonView.STEPPED_V,
    MoxonView.CB_PENNANT, MoxonView.CB_VERTICAL, MoxonView.MAST_VERTICAL,
)
private val steppedViews = setOf(MoxonView.STEPPED_H, MoxonView.STEPPED_V, MoxonView.PORTABLE_TUBE)
