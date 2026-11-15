package local.antenna.calculator.ui

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import local.antenna.calculator.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AntennaPoint(val x: Float, val y: Float, val z: Float) {
    operator fun minus(other: AntennaPoint) = AntennaPoint(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: AntennaPoint) = AntennaPoint(x + other.x, y + other.y, z + other.z)
    operator fun times(factor: Float) = AntennaPoint(x * factor, y * factor, z * factor)
    val length get() = sqrt(x * x + y * y + z * z)
}

internal data class AntennaRod(val first: AntennaPoint, val last: AntennaPoint, val width: Float, val material: Int)
internal data class AntennaJoint(val point: AntennaPoint, val diameter: Float, val material: Int)
internal data class AntennaVector(val rods: List<AntennaRod>, val joints: List<AntennaJoint>) {
    fun normalize(): AntennaVector {
        val points = rods.flatMap { listOf(it.first, it.last) } + joints.map { it.point }
        fun middle(axis: (AntennaPoint) -> Float) = (points.minOf(axis) + points.maxOf(axis)) / 2
        val center = AntennaPoint(middle { it.x }, middle { it.y }, middle { it.z })
        val radius = points.maxOf { (it - center).length }.coerceAtLeast(1f)
        fun fit(point: AntennaPoint) = (point - center) * (158 / radius)
        return AntennaVector(rods.flatMap { rod ->
            val first = fit(rod.first)
            val last = fit(rod.last)
            val delta = last - first
            val pieces = ceil(delta.length / 27).toInt().coerceAtLeast(1)
            List(pieces) { index ->
                rod.copy(first = first + delta * (index.toFloat() / pieces), last = first + delta * ((index + 1f) / pieces))
            }
        }, joints.map { it.copy(point = fit(it.point)) })
    }
}

internal data class AntennaView(val yaw: Float, val pitch: Float, val roll: Float = 0f) {
    fun projection(): (AntennaPoint) -> AntennaPoint {
        fun radians(degrees: Float) = degrees * (PI.toFloat() / 180f)
        val cy = cos(radians(yaw))
        val sy = sin(radians(yaw))
        val cx = cos(radians(pitch))
        val sx = sin(radians(pitch))
        val cr = cos(radians(roll))
        val sr = sin(radians(roll))
        return { point ->
            val x = point.x * cy + point.z * sy
            val z = -point.x * sy + point.z * cy
            val y = -point.y * cx + z * sx
            AntennaPoint(x * cr - y * sr, x * sr + y * cr, point.y * sx + z * cx)
        }
    }
}

internal fun initialAntennaView(id: String): AntennaView = when {
    id == "yagi-5" -> AntennaView(5f, 76f, 35f)
    id.startsWith("yagi-") -> AntennaView(-8f, 65f, -12f)
    id.startsWith("hb-") -> AntennaView(8f, 69f, 28f)
    id.startsWith("cantenna") -> AntennaView(36f, 22f, -10f)
    id.startsWith("halo-") -> AntennaView(-16f, 32f, -11f)
    id.startsWith("gp-") -> AntennaView(22f, 23f)
    id.startsWith("v-") || id.startsWith("loop-") -> AntennaView(-10f, 9f)
    id.startsWith("jpole") || id.startsWith("slim-") -> AntennaView(-14f, 8f)
    id in setOf("moxon_cb_field", "moxon_cb_field_2") -> AntennaView(24f, 9f)
    id in setOf("moxon_w1cdn", "moxon_baker_v", "moxon_ea2bd") -> AntennaView(-13f, 9f)
    id.startsWith("moxon") -> AntennaView(-13f, 69f, -9f)
    else -> AntennaView(-14f, 18f)
}

internal data class AntennaFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val center get() = Offset((left + right) / 2f, (top + bottom) / 2f)
    fun scale(width: Float, height: Float) = minOf(width / (right - left), height / (bottom - top))
}

private fun AntennaFrame.fit(initial: AntennaFrame, viewport: Size): Float {
    val width = viewport.width * .93f
    val height = viewport.height * .9f
    return minOf(initial.scale(width, height), scale(width, height), viewport.minDimension / 190f)
}

private fun AntennaFrame.boundPan(pan: Offset, viewport: Size, margin: Float, scale: Float): Offset {
    val extentX = (right - left) * scale / 2
    val extentY = (bottom - top) * scale / 2
    val x = viewport.width / 2 + extentX - minOf(margin, extentX)
    val y = viewport.height / 2 + extentY - minOf(margin, extentY)
    return Offset(pan.x.coerceIn(-x, x), pan.y.coerceIn(-y, y))
}

private const val MaxAntennaZoom = 5f

internal fun AntennaVector.frame(): AntennaFrame {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    fun include(point: AntennaPoint, diameter: Float) {
        val margin = diameter / 2 + 2f
        left = minOf(left, point.x - margin)
        top = minOf(top, point.y - margin)
        right = maxOf(right, point.x + margin)
        bottom = maxOf(bottom, point.y + margin)
    }
    for (rod in rods) {
        include(rod.first, rod.width)
        include(rod.last, rod.width)
    }
    for (joint in joints) include(joint.point, joint.diameter)
    return AntennaFrame(left, top, right, bottom)
}

private fun AntennaVector.project(view: AntennaView): AntennaVector {
    val project = view.projection()
    return AntennaVector(rods.map { it.copy(first = project(it.first), last = project(it.last)) },
        joints.map { it.copy(point = project(it.point)) })
}

private val antennaPalette = listOf(
    listOf(Color(0xff627f96), Color(0xffa5bfd2), Color(0xffe4f0f7)),
    listOf(Color(0xff293f50), Color(0xff476778), Color(0xff83a2b1)),
    listOf(Color(0xff936638), Color(0xffd6a266), Color(0xffffe0a7)),
    listOf(Color(0xff344f6e), Color(0xff648eb2), Color(0xffb1d1ec)),
    listOf(Color(0xff587583), Color(0xffadc6ca), Color(0xffe4e9dd)),
    listOf(Color(0xff59877e), Color(0xff93d2c2), Color(0xffd4f0e6)),
)

private fun JSONArray.point(index: Int) = AntennaPoint(getDouble(index).toFloat(), getDouble(index + 1).toFloat(), getDouble(index + 2).toFloat())

// Each model stores a list of rods (two XYZ points) and a list of joints (one XYZ point).
// The remaining pair is the drawing diameter and material index.
internal fun readAntennaVector(data: JSONObject, id: String): AntennaVector {
    val model = data.getJSONArray(id)
    val rods = model.getJSONArray(0)
    val joints = model.getJSONArray(1)
    return AntennaVector(
        List(rods.length()) { index -> rods.getJSONArray(index).run { AntennaRod(point(0), point(3), getDouble(6).toFloat(), getInt(7)) } },
        List(joints.length()) { index -> joints.getJSONArray(index).run { AntennaJoint(point(0), getDouble(3).toFloat(), getInt(4)) } },
    )
}

@Composable
internal fun RotatingAntenna(
    id: String, description: String, modifier: Modifier = Modifier,
    geometry: AntennaVector? = null, interactive: Boolean = true,
    baseHeight: Dp = 180.dp,
) {
    val assets = LocalContext.current.assets
    val stored = remember(assets) { lazy { assets.open("antennas/models.json").bufferedReader().use { JSONObject(it.readText()) } } }
    val model = remember(id, geometry, stored) { (geometry ?: readAntennaVector(stored.value, id)).normalize() }
    val initial = remember(id) { initialAntennaView(id) }
    val initialFrame = remember(model, initial) { model.project(initial).frame() }
    key(id) {
        var view by remember { mutableStateOf(initial) }
        var expanded by remember { mutableStateOf(false) }
        val turn: (Float, Float) -> Unit = { dx, dy ->
            view = view.copy(yaw = (view.yaw + dx) % 360f, pitch = (view.pitch + dy).coerceIn(-89f, 89f))
        }
        val reset = { view = initial }
        val height = if (interactive) baseHeight * 1.2f else baseHeight
        Box(modifier.fillMaxWidth().height(height)) {
            AntennaSurface(model, description, view, initialFrame, turn, reset, { expanded = true }, false, Modifier.fillMaxSize(), interactive)
        }
        if (expanded) {
            Dialog(
                onDismissRequest = { expanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                val background = MaterialTheme.colorScheme.surface
                SideEffect {
                    window?.let {
                        it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                        WindowCompat.getInsetsController(it, it.decorView).apply {
                            isAppearanceLightNavigationBars = background.luminance() > .5f
                            hide(WindowInsetsCompat.Type.statusBars())
                            show(WindowInsetsCompat.Type.navigationBars())
                        }
                    }
                }
                AntennaSurface(model, description, view, initialFrame, turn, reset, { expanded = false }, true,
                    Modifier.fillMaxSize().background(background).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun AntennaSurface(
    model: AntennaVector, description: String, view: AntennaView, initialFrame: AntennaFrame,
    turn: (Float, Float) -> Unit, reset: () -> Unit, toggle: () -> Unit, expanded: Boolean, modifier: Modifier,
    interactive: Boolean = true,
) {
    val currentTurn by rememberUpdatedState(turn)
    val currentToggle by rememberUpdatedState(toggle)
    val projected = remember(model, view) { model.project(view) }
    val frame = remember(projected) { projected.frame() }
    val currentFrame by rememberUpdatedState(frame)
    val currentInitialFrame by rememberUpdatedState(initialFrame)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val zoomLabel = if (expanded) stringResource(R.string.photo_zoom_level, (zoom * 100).roundToInt()) else null
    val toggleLabel = stringResource(if (expanded) R.string.antenna_view_close else R.string.antenna_view_expand)
    val actions = listOf(
        CustomAccessibilityAction(stringResource(R.string.antenna_view_left)) { turn(-15f, 0f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_right)) { turn(15f, 0f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_up)) { turn(0f, 12f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_down)) { turn(0f, -12f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_reset)) {
            reset()
            zoom = 1f
            pan = Offset.Zero
            true
        },
    )
    val interaction = if (interactive) Modifier.testTag(if (expanded) "antenna-full-view" else "antenna-rotating-view")
        .semantics {
            onClick(toggleLabel) { toggle(); true }
            customActions = actions
            if (zoomLabel != null) stateDescription = zoomLabel
        }
        .pointerInput(expanded) {
            var tapTime: Long? = null
            var tapPosition = Offset.Zero
            val slop = viewConfiguration.touchSlop
            val sensitivity = .68f / density
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture
                down.consume()
                var previous = down.position
                var dragged = false
                var transformed = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (expanded && (pressed >= 2 || transformed)) {
                        tapTime = null
                        transformed = true
                        if (event.changes.any { it.isConsumed }) break
                        if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                            val viewport = Size(size.width.toFloat(), size.height.toFloat())
                            val visibleFrame = currentFrame
                            val fit = visibleFrame.fit(currentInitialFrame, viewport)
                            val margin = 24.dp.toPx()
                            val nextZoom = (zoom * event.calculateZoom()).coerceIn(1f, MaxAntennaZoom)
                            val anchor = event.calculateCentroid(useCurrent = false) -
                                Offset(viewport.width / 2, viewport.height / 2)
                            val currentPan = visibleFrame.boundPan(pan, viewport, margin, fit * zoom)
                            val moved = anchor - (anchor - currentPan) * (nextZoom / zoom) + event.calculatePan()
                            pan = visibleFrame.boundPan(moved, viewport, margin, fit * nextZoom)
                            zoom = nextZoom
                        }
                        event.changes.forEach { it.consume() }
                        if (pressed == 0) break
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed || pressed > 1) {
                        tapTime = null
                        break
                    }
                    change.consume()
                    if (!change.pressed) {
                        if (!dragged && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                            val previousTime = tapTime
                            if (previousTime != null && down.uptimeMillis - previousTime in
                                viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                                (down.position - tapPosition).getDistance() <= slop * 5) {
                                tapTime = null
                                currentToggle()
                            } else {
                                tapTime = change.uptimeMillis
                                tapPosition = down.position
                            }
                        } else tapTime = null
                        break
                    }
                    if (!dragged && (change.position - down.position).getDistance() <= slop) continue
                    dragged = true
                    val delta = change.position - previous
                    previous = change.position
                    currentTurn(delta.x * sensitivity, -delta.y * sensitivity)
                }
            }
        } else Modifier
    val canvasModifier = if (expanded) modifier.clipToBounds() else modifier
    Canvas(canvasModifier.semantics { contentDescription = description }.then(interaction)) {
        val scale = frame.fit(initialFrame, size) * zoom
        val offset = if (expanded) frame.boundPan(pan, size, 24.dp.toPx(), scale) else Offset.Zero
        val origin = center + offset - frame.center * scale
        fun screen(point: AntennaPoint) = origin + Offset(point.x, point.y) * scale
        val rods = projected.rods
        val joints = projected.joints
        val order = (rods.indices.map { it to (rods[it].first.z + rods[it].last.z) / 2 } +
            joints.indices.map { (it + rods.size) to joints[it].point.z + 1 }).sortedBy { it.second }
        for ((index, _) in order) {
            if (index < rods.size) {
                val rod = rods[index]
                val first = screen(rod.first)
                val last = screen(rod.last)
                val (edge, base, light) = antennaPalette[rod.material]
                val along = last - first
                val length = along.getDistance()
                if (length < .5f) {
                    drawCircle(edge, (rod.width + 1.4f) * scale / 2, first)
                    drawCircle(base, rod.width * scale / 2, first)
                } else {
                    // Overlap the flat ends by a fraction of a pixel to hide depth-sort seams.
                    val overlap = along * (.45f / length)
                    val start = first - overlap
                    val end = last + overlap
                    drawLine(edge, start, end, (rod.width + 1.4f) * scale, StrokeCap.Butt)
                    drawLine(base, start, end, rod.width * scale, StrokeCap.Butt)
                    val across = Offset(along.y, -along.x) / length
                    val gleam = across * (rod.width * .22f * scale) * if (across.y > 0) -1f else 1f
                    drawLine(light, start + gleam, end + gleam, (rod.width * .17f * scale).coerceAtLeast(.65.dp.toPx()), StrokeCap.Butt)
                }
            } else {
                val joint = joints[index - rods.size]
                val (edge, base, light) = antennaPalette[joint.material]
                val at = screen(joint.point)
                drawCircle(edge, (joint.diameter / 2 + 1) * scale, at)
                drawCircle(base, joint.diameter / 2 * scale, at)
                drawCircle(light, joint.diameter * .16f * scale, at - Offset(scale, scale))
            }
        }
    }
}
