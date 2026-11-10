package local.antenna.calculator.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun Modifier.cardListBounce(scrollInProgress: Boolean): Modifier {
    val limit = with(LocalDensity.current) { 32.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val displacement by animateFloatAsState(
        pull, animationSpec = spring(dampingRatio = 1f, stiffness = 700f), label = "card-list-bounce",
    )
    LaunchedEffect(scrollInProgress) {
        if (!scrollInProgress) pull = 0f
    }
    val bounce = remember(limit) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                pull = (pull + available.y * 0.3f).coerceIn(-limit, limit)
                return Offset(0f, available.y)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pull * available.y >= 0f) return Offset.Zero
                // Release the edge displacement before normal scrolling resumes in the opposite direction.
                val remaining = abs(pull) / 0.3f
                val consumed = available.y.coerceIn(-remaining, remaining)
                pull += consumed * 0.3f
                return Offset(0f, consumed)
            }
        }
    }
    return clipToBounds().nestedScroll(bounce)
        .offset { IntOffset(0, displacement.roundToInt()) }
}
