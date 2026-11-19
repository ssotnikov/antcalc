package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import local.antenna.calculator.R

private data class ToolCardDrag(val id: String, val grabOffset: Float, val height: Int, val y: Float)

@Composable
internal fun ReorderableToolCards(
    listId: String,
    cardIds: List<String>,
    card: @Composable (id: String, modifier: Modifier) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("tool_card_order", Context.MODE_PRIVATE) }
    var order by remember(preferences, listId, cardIds) {
        val saved = preferences.getString(listId, "").orEmpty().split(',')
        mutableStateOf((saved.filter { it in cardIds } + cardIds).distinct())
    }
    fun move(from: String, to: String) {
        if (from == to) return
        val target = order.indexOf(to)
        if (target < 0 || from !in order) return
        order = order.toMutableList().apply { remove(from); add(target, from) }
        preferences.edit().putString(listId, order.joinToString(",")).apply()
    }

    val scroll = rememberPageLazyListState()
    val margin = 20.dp
    val spacing = 16.dp
    val density = LocalDensity.current
    val marginPx = with(density) { margin.toPx() }
    val spacingPx = with(density) { spacing.roundToPx() }
    val moveUp = stringResource(R.string.journal_project_move_up)
    val moveDown = stringResource(R.string.journal_project_move_down)
    var drag by remember(order) { mutableStateOf<ToolCardDrag?>(null) }

    val positions = order.withIndex().associate { (index, id) -> id to index }
    fun targetAt(current: ToolCardDrag): String = scroll.layoutInfo.reorderTarget(
        current.id, current.y - current.grabOffset, current.height, positions,
    )

    CardReorderEdgeScroll(scroll, drag?.id, order) { drag?.y }

    Box(Modifier.widthIn(max = 680.dp).fillMaxSize().clipToBounds()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag(listId).cardListBounce(scroll.isScrollInProgress)
                .pointerInput(order, marginPx) {
                    if (order.size < 2) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x !in marginPx..(size.width - marginPx)) return@awaitEachGesture
                        val layout = scroll.layoutInfo
                        val y = down.position.y + layout.viewportStartOffset
                        val item = layout.reorderCardAt(down.position.y, positions) ?: return@awaitEachGesture
                        val id = item.key as String
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val initial = ToolCardDrag(id, y - item.offset, item.size, held.position.y)
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == held.id } ?: break
                                if (change.isConsumed) break
                                change.consume()
                                if (change.position.y != held.position.y || drag != null) {
                                    drag = initial.copy(y = change.position.y)
                                }
                                if (!change.pressed) {
                                    drag?.let { move(id, targetAt(it)) }
                                    break
                                }
                            }
                        } finally {
                            drag = null
                        }
                    }
                },
            state = scroll,
            contentPadding = PaddingValues(margin),
            verticalArrangement = Arrangement.spacedBy(spacing),
            overscrollEffect = null,
        ) {
            item {
                Text(stringResource(R.string.journal_project_reorder_hint),
                    style = MaterialTheme.typography.bodySmall, color = inputExampleColor())
            }
            items(order, key = { it }) { id ->
                val index = order.indexOf(id)
                val current = drag
                val from = current?.let { order.indexOf(it.id) } ?: -1
                val to = current?.let { order.indexOf(targetAt(it)) } ?: -1
                val shift = when {
                    current == null -> 0
                    index > from && index <= to -> -(current.height + spacingPx)
                    index < from && index >= to -> current.height + spacingPx
                    else -> 0
                }
                val actions = Modifier.semantics {
                    customActions = buildList {
                        order.getOrNull(index - 1)?.let { target ->
                            add(CustomAccessibilityAction(moveUp) { move(id, target); true })
                        }
                        order.getOrNull(index + 1)?.let { target ->
                            add(CustomAccessibilityAction(moveDown) { move(id, target); true })
                        }
                    }
                }
                Box(Modifier.animateItem().offset { IntOffset(0, shift) }
                    .alpha(if (current?.id == id) 0f else 1f)) { card(id, actions) }
            }
        }
        drag?.let { current ->
            Box(Modifier.fillMaxWidth().padding(horizontal = margin)
                .offset { IntOffset(0, (current.y - current.grabOffset).roundToInt()) }
                .clearAndSetSemantics {}) { card(current.id, Modifier) }
        }
    }
}
