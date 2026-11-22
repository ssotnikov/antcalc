package local.antenna.calculator.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

internal fun LazyListLayoutInfo.reorderCardAt(
    pointerY: Float, positions: Map<String, Int>,
): LazyListItemInfo? {
    val y = pointerY + viewportStartOffset
    return visibleItemsInfo.firstOrNull { it.key in positions && y >= it.offset && y < it.offset + it.size }
}

internal fun LazyListLayoutInfo.reorderTarget(
    key: String, cardTop: Float, cardHeight: Int, positions: Map<String, Int>,
): String {
    val origin = positions.getValue(key)
    val center = cardTop + cardHeight / 2f + viewportStartOffset
    var target = key
    for (item in visibleItemsInfo) {
        val itemKey = item.key as? String ?: continue
        val index = positions[itemKey] ?: continue
        val middle = item.offset + item.size / 2f
        if (index < origin && center <= middle) return itemKey
        if (index > origin && center >= middle) target = itemKey
    }
    return target
}

internal fun LazyListLayoutInfo.reorderOverlayTop(
    desiredTop: Float, cardHeight: Int, firstKey: Any, lastKey: Any,
): Int {
    // Lazy item offsets include start padding in the viewport's coordinate system.
    val first = visibleItemsInfo.firstOrNull { it.key == firstKey }
    val last = visibleItemsInfo.firstOrNull { it.key == lastKey }
    val overhang = cardHeight / 3
    var top = desiredTop.roundToInt()
    if (first != null) {
        top = maxOf(top, first.offset - viewportStartOffset - overhang)
    }
    if (last != null) {
        top = minOf(top, last.offset + last.size - viewportStartOffset - cardHeight + overhang)
    }
    return top
}

@Composable
internal fun CardReorderEdgeScroll(
    list: LazyListState, draggingKey: Any?, orderKey: List<*>, pointerY: () -> Float?,
) {
    val density = LocalDensity.current
    val edge = with(density) { 54.dp.toPx() }
    val maxStep = with(density) { 12.dp.toPx() }
    val position by rememberUpdatedState(pointerY)
    LaunchedEffect(list, draggingKey, orderKey, edge, maxStep) {
        if (draggingKey == null) return@LaunchedEffect
        snapshotFlow {
            val y = position() ?: return@snapshotFlow 0f
            val height = list.layoutInfo.viewportSize.height
            when {
                y < edge -> -maxStep * ((edge - y) / edge).coerceIn(0f, 1f)
                y > height - edge -> maxStep * ((y - height + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
        }.collectLatest { step ->
            if (step == 0f) return@collectLatest
            do { withFrameNanos {} } while (list.scrollBy(step) != 0f)
        }
    }
}
