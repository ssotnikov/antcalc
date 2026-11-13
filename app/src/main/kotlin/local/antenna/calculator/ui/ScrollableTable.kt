package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun ScrollableTable(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(scrollState).then(modifier), verticalArrangement = verticalArrangement, content = content)
        if (scrollState.viewportSize > 0 && scrollState.maxValue in 1 until Int.MAX_VALUE) {
            TableScrollbar(scrollState)
        }
    }
}

@Composable
private fun TableScrollbar(scrollState: ScrollState) {
    val minimumThumb = with(LocalDensity.current) { 24.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = MaterialTheme.colorScheme.secondary
    val dragState = rememberDraggableState { delta ->
        val travel = thumbTravel(scrollState, minimumThumb)
        if (travel > 0f) scrollState.dispatchRawDelta(delta * scrollState.maxValue / travel)
    }

    Canvas(Modifier.fillMaxWidth().height(32.dp)
        .draggable(dragState, Orientation.Horizontal, reverseDirection = rtl)
        .testTag("table-horizontal-scrollbar")) {
        val travel = thumbTravel(scrollState, minimumThumb)
        val progress = scrollState.value.toFloat() / scrollState.maxValue.coerceAtLeast(1)
        val thumbX = travel * if (rtl) 1f - progress else progress
        val thickness = 6.dp.toPx()
        val y = (size.height - thickness) / 2
        val radius = CornerRadius(thickness / 2)
        drawRoundRect(trackColor, Offset(0f, y), Size(size.width, thickness), radius)
        drawRoundRect(thumbColor, Offset(thumbX, y), Size(size.width - travel, thickness), radius)
    }
}

private fun thumbTravel(scrollState: ScrollState, minimumThumb: Float): Float {
    val viewport = scrollState.viewportSize.toFloat()
    val thumb = viewport * (viewport / (viewport + scrollState.maxValue))
    return (viewport - maxOf(thumb, minimumThumb)).coerceAtLeast(0f)
}
