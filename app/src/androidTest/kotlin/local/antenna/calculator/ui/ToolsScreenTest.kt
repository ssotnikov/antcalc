package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

    private fun start(height: Int = 500, onOpen: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, height.dp)) { ToolsScreen(onOpen, onOpen) }
            }
        }
        compose.waitForIdle()
    }

    @Test fun shortCatalogMovesBothWaysAndReturnsAfterReleaseOrCancellation() {
        start()
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

    @Test fun dragDoesNotOpenCardAndTapStillWorks() {
        var opened = 0
        start(onOpen = { opened++ })
        compose.onNodeWithTag("open-matching").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(0, opened)
        compose.onNodeWithTag("open-matching").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun overflowingCatalogStillScrollsToSecondCard() {
        var opened = 0
        start(height = 180, onOpen = { opened++ })
        val cards = compose.onNodeWithTag("tools-cards")
        val range = cards.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
