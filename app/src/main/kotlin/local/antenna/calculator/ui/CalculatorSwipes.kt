package local.antenna.calculator.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import local.antenna.calculator.state.Section

/** Switch only on a completed horizontal drag; child editors and vertical scrolling keep priority. */
@Composable
internal fun Modifier.calculatorSwipes(section: Section, onNavigate: (Section) -> Unit): Modifier {
    val navigate by rememberUpdatedState(onNavigate)
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    return pointerInput(section, threshold) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                when {
                    distance <= -threshold && section == Section.SIZING -> navigate(Section.TRIMMING)
                    distance >= threshold && section == Section.TRIMMING -> navigate(Section.SIZING)
                }
                distance = 0f
            },
            onHorizontalDrag = { _, delta -> distance += delta },
        )
    }
}
