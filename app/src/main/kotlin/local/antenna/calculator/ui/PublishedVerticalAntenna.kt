package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val Ok1ikeJPoleModel = "j_pole_ok1ike"

internal data class PublishedVerticalAntenna(
    val heightMm: Float,
    val lowerEdgeMm: Float,
    val upperEdgeMm: Float,
    val feedMm: Float,
    val spacingMm: Float,
    val closedTop: Boolean,
) {
    fun wireSections(): List<Pair<AntennaPoint, AntennaPoint>> {
        fun end(side: Int, height: Float) = AntennaPoint(side * spacingMm / 2, height, 0f)
        val leftBottom = end(-1, 0f)
        val rightBottom = end(1, 0f)
        val leftTop = end(-1, heightMm)
        val rightTop = end(1, heightMm)
        return buildList {
            add(leftBottom to leftTop)
            add(leftBottom to rightBottom)
            add(rightBottom to end(1, lowerEdgeMm))
            add(end(1, upperEdgeMm) to rightTop)
            if (closedTop) add(leftTop to rightTop)
        }
    }

    fun vector(): AntennaVector {
        val feed = AntennaPoint(0f, feedMm, 0f)
        val coax = AntennaRod(feed, feed.copy(x = 48f, y = -29f, z = 18f), 5f, 1)
        val contacts = listOf(-1, 1).map { side ->
            AntennaJoint(feed.copy(x = side * spacingMm / 2), 6f, 2)
        }
        return AntennaVector(wireSections().map { (a, b) -> AntennaRod(a, b, 4f, 0) } + coax, contacts)
    }
}

internal val ok1ikeVertical = PublishedVerticalAntenna(472.5f, 139.5f, 144.5f, 9.3f, 8f, closedTop = false)
internal val sv1jrfVertical = PublishedVerticalAntenna(503f, 166f, 172f, 17f, 15f, closedTop = true)

@Composable
internal fun PublishedVerticalDiagram(
    layout: PublishedVerticalAntenna, description: String, tag: String = "published-vertical-diagram",
) {
    val metal = MaterialTheme.colorScheme.secondary
    val feed = MaterialTheme.colorScheme.tertiary
    val labels = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(194.dp).testTag(tag)
        .semantics { contentDescription = description }) {
        fun y(height: Float) = size.height * (.86f - .76f * height / layout.heightMm)
        val gapCenter = y((layout.lowerEdgeMm + layout.upperEdgeMm) / 2)
        val halfGap = ((y(layout.lowerEdgeMm) - y(layout.upperEdgeMm)) / 2).coerceAtLeast(5.dp.toPx())
        fun position(p: AntennaPoint): Offset = Offset(
            size.width * .44f + p.x / layout.spacingMm * size.width * .22f,
            when (p.y) {
                layout.lowerEdgeMm -> gapCenter + halfGap
                layout.upperEdgeMm -> gapCenter - halfGap
                else -> y(p.y)
            },
        )
        val sections = layout.wireSections()
        sections.forEach { (a, b) -> drawLine(metal, position(a), position(b), 3.dp.toPx(), StrokeCap.Round) }
        for (side in listOf(-1, 1)) {
            val contact = position(AntennaPoint(side * layout.spacingMm / 2, layout.feedMm, 0f))
            drawCircle(feed, 4.dp.toPx(), contact)
            drawLine(feed, contact, contact + Offset(side * size.width * .1f, 0f), 2.dp.toPx())
        }
        if (layout.closedTop) {
            val style = TextStyle(color = metal, fontSize = 13.sp)
            val marks = listOf(
                "A" to Offset(size.width * .23f, y(layout.heightMm / 2)),
                "B" to Offset(size.width * .60f, y((layout.heightMm + layout.upperEdgeMm) / 2)),
                "C" to Offset(size.width * .60f, y(layout.lowerEdgeMm / 2)),
                "D" to Offset(size.width * .71f, y(layout.feedMm)),
                "E" to Offset(size.width * .60f, gapCenter - 8.dp.toPx()),
                "F" to Offset(size.width * .41f, 0f),
            )
            marks.forEach { (mark, at) -> drawText(labels.measure(mark, style), topLeft = at) }
        }
    }
}
