package local.antenna.calculator.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CardReorderLayoutTest {
    @Test fun limitsBothEdgesUsingOneThirdOfTheDraggedHeight() {
        val list = layout(
            Item(0, "header", 0, 60),
            Item(1, "first", 68, 100),
            Item(2, "last", 250, 48),
        )
        assertEquals(58, list.reorderOverlayTop(-100f, 90, "first", "last"))
        assertEquals(258, list.reorderOverlayTop(500f, 90, "first", "last"))
        assertEquals(145, list.reorderOverlayTop(145f, 90, "first", "last"))
    }

    @Test fun scrollingCanRevealTheActualListEnd() {
        val beforeEnd = layout(Item(1, "first", 68, 100), Item(2, "middle", 250, 48))
        assertEquals(500, beforeEnd.reorderOverlayTop(500f, 90, "first", "last"))
        val atEnd = layout(Item(3, "last", 250, 48))
        assertEquals(257, atEnd.reorderOverlayTop(500f, 91, "first", "last"))
    }

    private data class Item(
        override val index: Int, override val key: Any,
        override val offset: Int, override val size: Int,
    ) : LazyListItemInfo

    private fun layout(vararg items: Item) = object : LazyListLayoutInfo {
        override val visibleItemsInfo: List<LazyListItemInfo> = items.toList()
        override val viewportStartOffset = -20
        override val viewportEndOffset = 390
        override val totalItemsCount = 4
    }
}
