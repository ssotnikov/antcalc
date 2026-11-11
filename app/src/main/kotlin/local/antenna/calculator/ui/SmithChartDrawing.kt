package local.antenna.calculator.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.IOException
import local.antenna.calculator.core.matching.SmithLoad
import local.antenna.calculator.core.matching.SmithPoint
import kotlin.math.*

private const val WIDTH = 360f
private const val HEIGHT = 340f
private const val CENTER_X = 180f
private const val CENTER_Y = 167f
private const val RADIUS = 132f

internal data class SmithPalette(
    val background: Int, val panel: Int, val ink: Int, val muted: Int, val grid: Int,
    val load: Int, val line: Int, val guide: Int,
)

@Composable
internal fun smithPalette(): SmithPalette {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    return SmithPalette(colors.surface.toArgb(), colors.surfaceContainerLow.toArgb(), colors.onSurface.toArgb(),
        colors.onSurfaceVariant.toArgb(), colors.outlineVariant.toArgb(),
        if (dark) 0xFFFFD25D.toInt() else 0xFF986400.toInt(),
        if (dark) 0xFF55C990.toInt() else 0xFF15734F.toInt(), colors.secondary.toArgb())
}

internal data class SmithDiagram(
    val load: SmithLoad?, val distance: Double, val loadLabel: String,
) {
    val linePoint get() = load?.reflection?.alongLine(distance)
}

@Composable
internal fun SmithChartGraphic(
    diagram: SmithDiagram,
    palette: SmithPalette,
    description: String,
    format: (Double) -> String,
    move: (SmithPoint, Boolean) -> Unit,
) {
    val current by rememberUpdatedState(diagram)
    val moveMarker by rememberUpdatedState(move)
    Canvas(
        Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(WIDTH / HEIGHT)
            .testTag("smith-chart").semantics { contentDescription = description }
            .pointerInput(Unit) {
                fun reflection(position: Offset) = SmithPoint(
                    ((position.x * WIDTH / size.width - CENTER_X) / RADIUS).toDouble(),
                    ((CENTER_Y - position.y * HEIGHT / size.height) / RADIUS).toDouble(),
                )
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val point = reflection(down.position)
                    if (point.magnitude > 1.05) return@awaitEachGesture
                    val diagramAtDown = current
                    val onLine = diagramAtDown.linePoint?.let { line ->
                        val nearLine = hypot(point.real - line.real, point.imaginary - line.imaginary) < 0.11
                        val load = checkNotNull(diagramAtDown.load).reflection
                        val nearLoad = hypot(point.real - load.real, point.imaginary - load.imaginary) < 0.045
                        nearLine && !nearLoad && load.magnitude > 1e-12
                    } ?: false
                    down.consume()
                    if (!onLine) moveMarker(point, false)
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed && change.position != change.previousPosition)
                            moveMarker(reflection(change.position), onLine)
                        change.consume()
                    } while (change.pressed)
                }
            },
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / WIDTH, size.height / HEIGHT)
        SmithDrawing(canvas, palette).draw(diagram, format)
        canvas.restore()
    }
}

@Composable
internal fun SmithAnatomyGraphic(
    palette: SmithPalette, description: String, labels: List<String>, format: (Double) -> String,
) {
    SmithAnatomyCanvas("smith-anatomy-figure", description, palette) { drawAnatomy(labels, format) }
}

@Composable
internal fun SmithGridDetailsGraphic(palette: SmithPalette, description: String, labels: List<String>) {
    SmithAnatomyCanvas("smith-grid-details-figure", description, palette) { drawGridDetails(labels) }
}

@Composable
private fun SmithAnatomyCanvas(
    tag: String, description: String, palette: SmithPalette, draw: SmithDrawing.() -> Unit,
) {
    Canvas(Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(WIDTH / HEIGHT)
        .testTag(tag).semantics { contentDescription = description }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / WIDTH, size.height / HEIGHT)
        SmithDrawing(canvas, palette).draw()
        canvas.restore()
    }
}

internal fun writeSmithPng(
    resolver: ContentResolver, uri: Uri, diagram: SmithDiagram, palette: SmithPalette,
    format: (Double) -> String, title: String, details: List<Pair<String, String>>,
) {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException("Expected document URI")
    val height = 408 + 40 * details.size
    val bitmap = Bitmap.createBitmap(800, 2 * height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(palette.panel)
        canvas.scale(2f, 2f)
        val drawing = SmithDrawing(canvas, palette)
        drawing.label(title, 20f, 30f, 19f, palette.ink, Paint.Align.LEFT)
        canvas.save()
        canvas.translate(20f, 42f)
        drawing.draw(diagram, format)
        canvas.restore()
        details.forEachIndexed { index, (label, value) ->
            val y = 402f + 40f * index
            drawing.label(label, 20f, y, 12f, palette.muted, Paint.Align.LEFT)
            drawing.label(value, 20f, y + 16f, 13f, palette.ink, Paint.Align.LEFT, maxWidth = 360f)
        }
        resolver.openOutputStream(uri, "w")?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IOException("PNG encoding failed")
        } ?: throw IOException("Document unavailable")
    } finally {
        bitmap.recycle()
    }
}

private class SmithDrawing(private val canvas: AndroidCanvas, private val palette: SmithPalette) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun label(
        text: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, maxWidth: Float = Float.POSITIVE_INFINITY,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        val width = paint.measureText(text)
        if (width > maxWidth) paint.textSize *= maxWidth / width
        canvas.drawText(text, x, y, paint)
    }

    fun draw(diagram: SmithDiagram, format: (Double) -> String) {
        grid(format, labels = true)
        val load = diagram.load ?: return
        val point = position(load.reflection)
        val line = position(checkNotNull(diagram.linePoint))
        if (load.matching.magnitude > 0.0) {
            dashedCircle(RADIUS * load.matching.magnitude.toFloat(), palette.load)
            val center = Offset(CENTER_X, CENTER_Y)
            arrow(listOf(center, line), palette.line)
            arrow(listOf(center, point), palette.load)
        }
        val greenRadius = if ((line - point).getDistance() < 8f) 8.8f else 6.1f
        circle(line.x, line.y, greenRadius + 0.8f, palette.background)
        circle(line.x, line.y, greenRadius, palette.line)
        circle(point.x, point.y, 6f, palette.background)
        circle(point.x, point.y, 5.1f, palette.load)
        val align = if (point.x > 223f) Paint.Align.RIGHT else Paint.Align.LEFT
        val x = if (align == Paint.Align.RIGHT) point.x - 10 else point.x + 10
        label(diagram.loadLabel, x.coerceIn(63f, 298f), maxOf(29f, point.y - 10f), 11f, palette.ink, align)
    }

    fun drawAnatomy(labels: List<String>, format: (Double) -> String) {
        grid(format, labels = false)
        fun sample(from: Double, to: Double, point: (Double) -> SmithPoint) =
            (0..32).map { position(point(from + (to - from) * it / 32)) }
        fun normalized(r: Double, x: Double) = SmithLoad(50.0, r * 50, x * 50).reflection
        arrow(sample(0.0, 1.12) { normalized(0.5, it) }, palette.line)
        arrow(sample(0.0, -1.12) { normalized(0.5, it) }, palette.line)
        arrow(sample(0.18, 2.5) { normalized(it, 0.5) }, palette.load)
        arrow(sample(0.18, 2.5) { normalized(it, -0.5) }, palette.load)
        label(labels[0], 180f, 23f, 11f, palette.guide)
        label(labels[1], 180f, 322f, 11f, palette.guide)
        label(labels[2], 26f, 171f, 10f, palette.muted)
        label(labels[3], 334f, 171f, 10f, palette.muted)
        label(labels[4], 80f, 103f, 10f, palette.line)
        label(labels[5], 79f, 232f, 10f, palette.line)
        label(labels[6], 231f, 100f, 10f, palette.load)
        label(labels[6], 231f, 235f, 10f, palette.load)
        label("Z₀", CENTER_X, 181f, 10f, palette.muted)
    }

    fun drawGridDetails(labels: List<String>) {
        grid(labels = false, shadeHalves = true)
        dashedCircle(RADIUS * 0.36f, palette.load)
        circle(CENTER_X, CENTER_Y, 3.5f, palette.ink)
        label("Z = Z₀ · Γ = 0", CENTER_X + 10f, CENTER_Y - 9f, 11f, palette.ink, Paint.Align.LEFT)
        label("|Γ| = const", 206f, 112f, 11f, palette.load, Paint.Align.LEFT)
        label("r = const", 81f, 128f, 11f, palette.line)
        label("x = const", 239f, 246f, 11f, palette.guide)
        label(labels[0], CENTER_X, 23f, 11f, palette.ink)
        label(labels[1], CENTER_X, 322f, 11f, palette.ink)
        label("180°", 26f, 171f, 10f, palette.muted)
        label("0°", 326f, 183f, 10f, palette.muted)
        drawReactanceDirections()
    }

    private fun drawReactanceDirections() {
        val x = 341f
        val axis = Offset(x, CENTER_Y)
        arrow(listOf(axis, Offset(x, 99f)), palette.guide)
        arrow(listOf(axis, Offset(x, 235f)), palette.load)

        paint.color = palette.guide
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawPath(Path().apply {
            moveTo(x, 64f)
            lineTo(x, 69f)
            for (y in listOf(69f, 75f, 81f)) {
                cubicTo(x + 9f, y, x + 9f, y + 6f, x, y + 6f)
            }
            lineTo(x, 92f)
        }, paint)
        label("+j", x, 58f, 11f, palette.guide)

        line(Offset(x, 242f), Offset(x, 249f), palette.load, 1.5f)
        line(Offset(x - 7f, 249f), Offset(x + 7f, 249f), palette.load, 1.5f)
        line(Offset(x - 7f, 254f), Offset(x + 7f, 254f), palette.load, 1.5f)
        line(Offset(x, 254f), Offset(x, 261f), palette.load, 1.5f)
        label("−j", x, 277f, 11f, palette.load)
    }

    private fun dashedCircle(radius: Float, color: Int) {
        paint.pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
        circle(CENTER_X, CENTER_Y, radius, color, 1.7f)
        paint.pathEffect = null
    }

    private fun grid(format: (Double) -> String = { it.toString() }, labels: Boolean, shadeHalves: Boolean = false) {
        circle(CENTER_X, CENTER_Y, RADIUS, palette.background)
        if (shadeHalves) {
            val bounds = RectF(CENTER_X - RADIUS, CENTER_Y - RADIUS, CENTER_X + RADIUS, CENTER_Y + RADIUS)
            paint.style = Paint.Style.FILL
            paint.color = (palette.guide and 0x00FFFFFF) or 0x20000000
            canvas.drawArc(bounds, 180f, 180f, true, paint)
            paint.color = (palette.load and 0x00FFFFFF) or 0x20000000
            canvas.drawArc(bounds, 0f, 180f, true, paint)
        }
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(CENTER_X, CENTER_Y, RADIUS, Path.Direction.CW) })
        val scales = listOf(.2, .5, 1.0, 2.0, 5.0)
        for (r in scales) {
            val highlighted = shadeHalves && r == 0.5
            val color = when {
                highlighted -> palette.line
                r == 1.0 -> palette.muted
                else -> palette.grid
            }
            circle(CENTER_X + (RADIUS * r / (1 + r)).toFloat(), CENTER_Y,
                (RADIUS / (1 + r)).toFloat(), color, if (highlighted) 2.1f else 0.85f)
        }
        for (x in scales + scales.map { -it }) {
            val highlighted = shadeHalves && x == -0.5
            circle(CENTER_X + RADIUS, CENTER_Y - (RADIUS / x).toFloat(),
                (RADIUS / abs(x)).toFloat(), if (highlighted) palette.guide else palette.grid,
                if (highlighted) 2.1f else 0.85f)
        }
        line(Offset(CENTER_X - RADIUS, CENTER_Y), Offset(CENTER_X + RADIUS, CENTER_Y), palette.muted, 0.85f)
        canvas.restore()
        circle(CENTER_X, CENTER_Y, RADIUS, palette.muted, 1.4f)
        if (labels) {
            label("0", CENTER_X - RADIUS - 15f, CENTER_Y + 4f, 12f, palette.ink)
            label("∞", CENTER_X + RADIUS + 15f, CENTER_Y + 4f, 12f, palette.ink)
            for (r in scales) label(format(r), CENTER_X + (RADIUS * (r - 1) / (r + 1)).toFloat(),
                CENTER_Y + 13f, 10.5f, palette.muted)
            for (x in scales + scales.map { -it }) {
                val p = SmithLoad(50.0, 0.0, x * 50).reflection
                label((if (x < 0) "−j" else "+j") + format(abs(x)), CENTER_X + (149 * p.real).toFloat(),
                    CENTER_Y - (149 * p.imaginary).toFloat() + 4f, 10.5f, palette.muted)
            }
        }
    }

    private fun circle(x: Float, y: Float, radius: Float, color: Int, stroke: Float = 0f) {
        paint.color = color
        paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun line(start: Offset, end: Offset, color: Int, stroke: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)
    }

    private fun arrow(points: List<Offset>, color: Int) {
        points.zipWithNext { start, end -> line(start, end, color, 2.1f) }
        val end = points.last()
        val segment = end - points[points.lastIndex - 1]
        val distance = segment.getDistance()
        if (distance == 0f) return
        val direction = segment / distance
        val head = minOf(9f, (end - points.first()).getDistance() / 2f)
        val base = end - direction * head
        val perpendicular = Offset(-direction.y, direction.x) * (head * 4f / 9f)
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawPath(Path().apply {
            moveTo(end.x, end.y)
            lineTo(base.x + perpendicular.x, base.y + perpendicular.y)
            lineTo(base.x - perpendicular.x, base.y - perpendicular.y)
            close()
        }, paint)
    }

    private fun position(point: SmithPoint) =
        Offset(CENTER_X + (RADIUS * point.real).toFloat(), CENTER_Y - (RADIUS * point.imaginary).toFloat())
}
