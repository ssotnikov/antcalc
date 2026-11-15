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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import local.antenna.calculator.R

internal fun haloVector(variant: HaloVariant): AntennaVector {
    fun point(x: Float, z: Float, y: Float = 0f) = AntennaPoint(x, y, z)
    val square = variant != HaloVariant.ROUND
    val radius = if (variant == HaloVariant.CSQ11) 88f else 100f
    val contour = if (square) {
        listOf(point(-12f, -radius), point(-radius, -radius), point(-radius, radius),
            point(radius, radius), point(radius, -radius), point(12f, -radius))
    } else {
        (0..40).map {
            val angle = -PI / 2 - 0.12 - (2 * PI - 0.24) * it / 40
            point((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
        }
    }
    val rods = contour.zipWithNext { first, last -> AntennaRod(first, last, 4.5f, 0) }.toMutableList()
    rods += AntennaRod(point(0f, -radius), point(0f, radius), 6f, 1)
    rods += AntennaRod(point(0f, 0f, -42f), point(0f, 0f), 8f, 1)
    rods += AntennaRod(point(0f, radius, -8f), point(15f, radius), 3.5f, 2)
    rods += AntennaRod(point(20f, radius), point(48f, radius - 12f), 3.5f, 2)
    rods += AntennaRod(point(0f, radius, -8f), point(0f, radius - 12f, -37f), 3f, 1)
    return AntennaVector(rods, listOf(AntennaJoint(point(0f, radius), 11f, 1),
        AntennaJoint(point(18f, radius), 6f, 2)))
}

internal enum class HaloDiagramType(val description: Int) {
    ROUND(R.string.halo_geometry_diagram),
    SQUARE(R.string.halo_geometry_diagram),
    GAMMA(R.string.halo_gamma_diagram),
    ROOF(R.string.halo_roof_diagram),
    SHUNT(R.string.halo_shunt_diagram),
}

@Composable
internal fun HaloDiagram(type: HaloDiagramType) {
    val colors = MaterialTheme.colorScheme
    val wire = colors.secondary
    val amber = if (colors.surface.luminance() < .5f) Color(0xFFF7C879) else Color(0xFF805000)
    val labelColor = colors.onSurface
    val description = stringResource(type.description)
    val feed = stringResource(R.string.halo_feed)
    val cap = stringResource(R.string.halo_roof_size)
    val strap = stringResource(R.string.halo_strap)
    val shield = stringResource(R.string.halo_shield)
    val core = stringResource(R.string.halo_center)
    val roof = stringResource(R.string.halo_roof)
    val terminals = stringResource(R.string.halo_terminals)
    val measure = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = description }) {
        fun position(x: Float, y: Float) = Offset(x * size.width, y * size.height)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = wire, width: Float = 3f) {
            drawLine(color, position(x1, y1), position(x2, y2), width.dp.toPx(), StrokeCap.Round)
        }
        fun label(value: String, x: Float, y: Float, color: Color = labelColor) {
            val layout = measure.measure(value, TextStyle(color = color, fontSize = 12.sp))
            val at = position(x, y)
            drawText(layout, topLeft = Offset((at.x - layout.size.width / 2f).coerceAtLeast(0f),
                at.y - layout.size.height / 2f))
        }
        when (type) {
            HaloDiagramType.ROUND, HaloDiagramType.SQUARE -> {
                val contact = if (type == HaloDiagramType.ROUND) {
                    val r = minOf(size.width * .28f, size.height * .35f)
                    drawArc(wire, -80f, 340f, false, position(.50f, .47f) - Offset(r, r),
                        Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(colors.outline, position(.50f, .47f) - Offset(r, 0f), position(.50f, .47f) + Offset(r, 0f))
                    label("D", .50f, .40f)
                    position(.50f, .47f) + Offset(0f, r)
                } else {
                    line(.46f, .16f, .24f, .16f)
                    line(.24f, .16f, .24f, .82f)
                    line(.24f, .82f, .76f, .82f)
                    line(.76f, .82f, .76f, .16f)
                    line(.76f, .16f, .54f, .16f)
                    line(.19f, .16f, .19f, .82f, colors.outline, 1f)
                    label("S", .14f, .49f)
                    position(.50f, .82f)
                }
                label("g", .50f, .055f, amber)
                drawCircle(amber, 4.dp.toPx(), contact)
                label(feed, .50f, .94f, amber)
            }
            HaloDiagramType.GAMMA -> {
                line(.09f, .24f, .91f, .24f)
                line(.22f, .24f, .22f, .71f, amber, 2f)
                line(.37f, .81f, .37f, .53f, amber, 2f)
                line(.37f, .53f, .48f, .53f, amber, 2f)
                line(.48f, .46f, .48f, .60f, amber, 2f)
                line(.52f, .46f, .52f, .60f, amber, 2f)
                line(.52f, .53f, .79f, .53f, amber, 2f)
                line(.79f, .53f, .79f, .24f, amber, 2f)
                label(strap, .76f, .10f)
                label("C", .50f, .40f, amber)
                label(shield, .18f, .80f)
                label(core, .55f, .92f)
            }
            HaloDiagramType.ROOF -> {
                line(.14f, .22f, .78f, .22f)
                line(.24f, .22f, .24f, .65f, amber, 2f)
                line(.68f, .22f, .68f, .65f, amber, 2f)
                line(.12f, .65f, .88f, .65f, colors.outline, 2f)
                line(.83f, .22f, .83f, .65f, colors.outline, 1f)
                line(.81f, .22f, .85f, .22f, colors.outline, 1f)
                line(.81f, .65f, .85f, .65f, colors.outline, 1f)
                label(cap, .64f, .44f)
                label(roof, .50f, .80f)
            }
            HaloDiagramType.SHUNT -> {
                line(.12f, .27f, .37f, .27f)
                line(.63f, .27f, .88f, .27f)
                line(.37f, .27f, .37f, .72f, amber, 2f)
                line(.63f, .27f, .63f, .72f, amber, 2f)
                val path = Path().apply {
                    val start = position(.37f, .50f)
                    moveTo(start.x, start.y)
                    for (index in 0..3) {
                        val control = position(.37f + .065f * (index + .5f), .35f)
                        val end = position(.37f + .065f * (index + 1), .50f)
                        quadraticTo(control.x, control.y, end.x, end.y)
                    }
                }
                drawPath(path, amber, style = Stroke(2.dp.toPx()))
                label("L", .50f, .31f, amber)
                label(terminals, .50f, .88f)
            }
        }
    }
}
