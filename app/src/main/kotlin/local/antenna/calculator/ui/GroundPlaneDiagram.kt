package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.core.units.LengthUnit

@Composable
internal fun GroundPlaneDiagram(layout: GroundPlaneResult.Layout, unit: LengthUnit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.secondary)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val angle = layout.angleTenths / 10f
    val description = stringResource(
        R.string.ground_plane_side_alt,
        groundPlaneLength(context, layout.whipMeters, unit),
        groundPlaneLength(context, layout.radialMeters, unit),
        groundPlaneLength(context, layout.baseMeters, unit),
        groundPlaneLength(context, layout.endHeightMeters, unit),
        groundPlaneLength(context, layout.diameterMeters, unit),
        localizedNumber(context, angle.toString().removeSuffix(".0")),
    )

    Column {
        Text(stringResource(R.string.ground_plane_side), style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().height((232 + 36 * fontScale).dp)
            .testTag("ground-plane-diagram").semantics { contentDescription = description }) {
            val groundY = size.height - (36 * fontScale).dp.toPx()
            val centerX = size.width / 2
            val scale = minOf(
                (groundY - 22.dp.toPx()) / layout.tipHeightMeters,
                size.width * 0.29 / layout.radiusMeters,
            )
            fun y(height: Double) = groundY - (height * scale).toFloat()
            val tip = Offset(centerX, y(layout.tipHeightMeters))
            val feed = Offset(centerX, y(layout.baseMeters))
            val radius = (layout.radiusMeters * scale).toFloat()
            val left = Offset(centerX - radius, y(layout.endHeightMeters))
            val right = Offset(centerX + radius, left.y)
            val guide = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            val stroke = 1.dp.toPx()
            val tick = 3.dp.toPx()

            fun guideLine(start: Offset, end: Offset) =
                drawLine(colors.outline, start, end, stroke, pathEffect = guide)

            fun label(symbol: String, center: Offset) {
                val text = measurer.measure(symbol, labelStyle)
                val x = (center.x - text.size.width / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                val y = (center.y - text.size.height / 2f).coerceIn(0f, (size.height - text.size.height).coerceAtLeast(0f))
                drawText(text, topLeft = Offset(x, y))
            }

            fun verticalDimension(point: Offset, railX: Float, symbol: String, labelX: Float) {
                guideLine(point, Offset(railX, point.y))
                drawLine(colors.secondary, Offset(railX, point.y), Offset(railX, groundY), stroke)
                for (end in listOf(point.y, groundY)) {
                    drawLine(colors.secondary, Offset(railX - tick, end), Offset(railX + tick, end), stroke)
                }
                label(symbol, Offset(labelX, minOf((point.y + groundY) / 2, groundY - (10 * fontScale).dp.toPx())))
            }

            drawLine(colors.outline, Offset(0f, groundY), Offset(size.width, groundY), stroke)
            drawLine(colors.outline, Offset(centerX, groundY), feed, 2.dp.toPx())
            verticalDimension(feed, size.width * 0.12f, "H", size.width * 0.05f)
            verticalDimension(right, size.width * 0.88f, "h", size.width * 0.95f)

            val diameterY = groundY + 10.dp.toPx()
            for (end in listOf(left, right)) {
                guideLine(end, Offset(end.x, diameterY))
                drawLine(colors.secondary, Offset(end.x, diameterY - tick), Offset(end.x, diameterY + tick), stroke)
            }
            drawLine(colors.secondary, Offset(left.x, diameterY), Offset(right.x, diameterY), stroke)
            label("D", Offset(centerX, diameterY + (11 * fontScale).dp.toPx()))

            val radians = Math.toRadians(angle.toDouble())
            val slope = Offset(cos(radians).toFloat(), sin(radians).toFloat())
            val arcRadius = (20 * fontScale).dp.toPx().coerceAtMost(size.width * .16f)
            guideLine(feed, feed + Offset(arcRadius + 6.dp.toPx(), 0f))
            if (angle > 0f) {
                if ((right - feed).getDistance() < arcRadius) guideLine(right, feed + slope * arcRadius)
                drawArc(colors.secondary, 0f, angle, false,
                    topLeft = feed - Offset(arcRadius, arcRadius), size = Size(2 * arcRadius, 2 * arcRadius),
                    style = Stroke(stroke))
            }
            label("β", feed + Offset(arcRadius + (11 * fontScale).dp.toPx(), (8 * fontScale).dp.toPx()))

            drawLine(colors.primary, feed, tip, 3.dp.toPx())
            drawLine(colors.primary, left, feed, 2.dp.toPx())
            drawLine(colors.primary, feed, right, 2.dp.toPx())
            drawCircle(colors.primary, 4.dp.toPx(), feed)
            label("Lₛ", Offset(centerX + (16 * fontScale).dp.toPx(), minOf((tip.y + feed.y) / 2, feed.y - 9.dp.toPx())))
            val radialMidpoint = (left + feed) / 2f
            val radialLabelX = radialMidpoint.x - (19 * fontScale).dp.toPx()
            val radialLabelY = maxOf(radialMidpoint.y, feed.y + (12 * fontScale).dp.toPx())
            label("Lᵣ", Offset(radialLabelX, radialLabelY))
        }
    }
}
