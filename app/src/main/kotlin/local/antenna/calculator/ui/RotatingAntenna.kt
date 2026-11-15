package local.antenna.calculator.ui

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import local.antenna.calculator.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
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
    id: String, description: String, modifier: Modifier = Modifier.fillMaxWidth().height(180.dp),
    geometry: AntennaVector? = null, interactive: Boolean = true,
) {
    val assets = LocalContext.current.assets
    val stored = remember(assets) { lazy { assets.open("antennas/models.json").bufferedReader().use { JSONObject(it.readText()) } } }
    val model = remember(id, geometry, stored) { (geometry ?: readAntennaVector(stored.value, id)).normalize() }
    val profileStates = rememberSaveableStateHolder()
    profileStates.SaveableStateProvider(id) {
        var yaw by rememberSaveable { mutableFloatStateOf(-24f) }
        var pitch by rememberSaveable { mutableFloatStateOf(26f) }
        var expanded by rememberSaveable { mutableStateOf(false) }
        val turn: (Float, Float) -> Unit = { dx, dy ->
            yaw = (yaw + dx) % 360f
            pitch = (pitch + dy).coerceIn(-89f, 89f)
        }
        val reset = { yaw = -24f; pitch = 26f }
        Box(modifier) {
            AntennaSurface(model, description, yaw, pitch, turn, reset, { expanded = true }, false, Modifier.fillMaxSize(), interactive)
        }
        if (expanded) {
            Dialog(
                onDismissRequest = { expanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                SideEffect {
                    window?.let {
                        it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                        WindowCompat.getInsetsController(it, it.decorView).apply {
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            hide(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }
                AntennaSurface(model, description, yaw, pitch, turn, reset, { expanded = false }, true,
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
        }
    }
}

@Composable
private fun AntennaSurface(
    model: AntennaVector, description: String, yaw: Float, pitch: Float,
    turn: (Float, Float) -> Unit, reset: () -> Unit, toggle: () -> Unit, expanded: Boolean, modifier: Modifier,
    interactive: Boolean = true,
) {
    val currentTurn by rememberUpdatedState(turn)
    val currentToggle by rememberUpdatedState(toggle)
    val toggleLabel = stringResource(if (expanded) R.string.antenna_view_close else R.string.antenna_view_expand)
    val actions = listOf(
        CustomAccessibilityAction(stringResource(R.string.antenna_view_left)) { turn(-15f, 0f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_right)) { turn(15f, 0f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_up)) { turn(0f, 12f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_down)) { turn(0f, -12f); true },
        CustomAccessibilityAction(stringResource(R.string.antenna_view_reset)) { reset(); true },
    )
    val interaction = if (interactive) Modifier.testTag(if (expanded) "antenna-full-view" else "antenna-rotating-view")
        .semantics {
            onClick(toggleLabel) { toggle(); true }
            customActions = actions
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
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
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
    Canvas(modifier.semantics { contentDescription = description }.then(interaction)) {
        val angleY = yaw * (PI.toFloat() / 180)
        val angleX = pitch * (PI.toFloat() / 180)
        val cy = cos(angleY)
        val sy = sin(angleY)
        val cx = cos(angleX)
        val sx = sin(angleX)
        val scale = minOf(size.width / 390, size.height / 350)
        fun project(point: AntennaPoint): AntennaPoint {
            val z = -point.x * sy + point.z * cy
            return AntennaPoint(point.x * cy + point.z * sy, -(point.y * cx - z * sx), point.y * sx + z * cx)
        }
        fun screen(point: AntennaPoint) = center + Offset(point.x, point.y) * scale
        val rods = model.rods.map { it.copy(first = project(it.first), last = project(it.last)) }
        val joints = model.joints.map { it.copy(point = project(it.point)) }
        val order = (rods.indices.map { it to (rods[it].first.z + rods[it].last.z) / 2 } +
            joints.indices.map { (it + rods.size) to joints[it].point.z + 1 }).sortedBy { it.second }
        for ((index, _) in order) {
            if (index < rods.size) {
                val rod = rods[index]
                val first = screen(rod.first)
                val last = screen(rod.last)
                val (edge, base, light) = antennaPalette[rod.material]
                drawLine(edge, first, last, (rod.width + 1.4f) * scale, StrokeCap.Round)
                drawLine(base, first, last, rod.width * scale, StrokeCap.Round)
                val gleam = Offset(0f, -scale)
                drawLine(light, first + gleam, last + gleam, (rod.width * .19f).coerceAtLeast(.8f) * scale, StrokeCap.Round)
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
