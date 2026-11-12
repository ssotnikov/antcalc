package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun start(height: Int = 500, densityScale: Float = 1f, onOpen: () -> Unit = {}) {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density * densityScale, currentDensity.fontScale)) {
                MaterialTheme {
                    Box(Modifier.size(320.dp, height.dp)) { ToolsScreen(onOpen, onOpen, onOpen, onOpen) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun shortCatalogMovesBothWaysAndReturnsAfterReleaseOrCancellation() {
        start(height = 850, densityScale = 0.75f)
        val range = compose.onNodeWithTag("tools-cards").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), 0f)
        val viewport = compose.onNodeWithTag("tools-viewport")
        val cards = compose.onNodeWithTag("open-matching")
        val initial = cards.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        for (direction in listOf(1f, -1f)) {
            // Start below the cards: the unused viewport must respond too.
            viewport.performTouchInput {
                down(Offset(centerX, height - 180f))
                moveBy(Offset(0f, direction * 120f), 100)
            }
            compose.mainClock.advanceTimeBy(350)
            val moved = cards.fetchSemanticsNode().boundsInRoot.top
            assertTrue("Content must follow drag $direction: $initial -> $moved", (moved - initial) * direction > 1f)
            viewport.performTouchInput { if (direction > 0) up() else cancel() }
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(initial, cards.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun shortLazyListBouncesInBothDirections() {
        val viewport = startLazyCards(1)
        val card = compose.onNodeWithTag("card-0")
        assertLazyBounce(viewport, card, 1f)
        assertLazyBounce(viewport, card, -1f)
    }

    @Test fun overflowingLazyListBouncesAtBothEdges() {
        val viewport = startLazyCards(20)
        assertLazyBounce(viewport, compose.onNodeWithTag("card-0"), 1f)
        compose.onNodeWithTag("lazy-cards").performScrollToIndex(19)
        assertLazyBounce(viewport, compose.onNodeWithTag("card-19"), -1f)
    }

    private fun startLazyCards(count: Int): SemanticsNodeInteraction {
        compose.setContent {
            MaterialTheme {
                val scroll = rememberLazyListState()
                Box(Modifier.size(320.dp, 280.dp).testTag("lazy-card-viewport")) {
                    LazyColumn(
                        Modifier.fillMaxSize().cardListBounce(scroll.isScrollInProgress).testTag("lazy-cards"),
                        state = scroll, overscrollEffect = null,
                    ) {
                        items(count) { index ->
                            OutlinedCard(Modifier.fillMaxWidth().height(64.dp).testTag("card-$index")) {
                                Text("Card $index")
                            }
                        }
                    }
                }
            }
        }
        return compose.onNodeWithTag("lazy-card-viewport")
    }

    private fun assertLazyBounce(viewport: SemanticsNodeInteraction, card: SemanticsNodeInteraction, direction: Float) {
        val initial = card.getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = false
        try {
            viewport.performTouchInput {
                down(center)
                moveBy(Offset(0f, direction * 120f), 100)
            }
            compose.mainClock.advanceTimeBy(350)
            val movement = card.getUnclippedBoundsInRoot().top - initial
            assertTrue("Edge displacement should follow the drag: $movement", movement.value * direction > 1f)
            viewport.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(initial.value, card.getUnclippedBoundsInRoot().top.value, 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun dragDoesNotOpenCardAndTapStillWorks() {
        var opened = 0
        start(onOpen = { opened++ })
        compose.onNodeWithTag("open-matching").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(0, opened)
        compose.onNodeWithTag("open-matching").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun overflowingCatalogStillScrollsToCableCard() {
        var opened = 0
        start(height = 180, onOpen = { opened++ })
        val cards = compose.onNodeWithTag("tools-cards")
        val range = cards.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
