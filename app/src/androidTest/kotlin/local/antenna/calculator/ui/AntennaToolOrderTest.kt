package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

internal fun SemanticsNodeInteractionsProvider.scrollToAntennaTool(tag: String): SemanticsNodeInteraction {
    onNodeWithTag("antenna-parameter-tools").performScrollToNode(hasTestTag(tag))
    return onNodeWithTag(tag)
}

@RunWith(AndroidJUnit4::class)
class AntennaToolOrderTest : CalculatorUiFixture() {
    @Test fun dragReordersCardsAndTheOrderSurvivesReopening() {
        val preferences = compose.activity.getSharedPreferences("tool_card_order", Context.MODE_PRIVATE)
        val listTag = "antenna-parameter-tools"
        val saved = preferences.getString(listTag, null)
        preferences.edit().remove(listTag).commit()
        try {
            start()
            compose.onNodeWithTag("nav-TOOLS").performClick()
            fun openList() {
                compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
            }
            openList()
            val list = compose.onNodeWithTag(listTag)
            val first = compose.onNodeWithTag("open-inverted-v")
            val second = compose.onNodeWithTag("open-ground-plane")
            fun dragCard(source: SemanticsNodeInteraction, destination: SemanticsNodeInteraction) {
                val origin = list.fetchSemanticsNode().boundsInRoot.topLeft
                val from = source.fetchSemanticsNode().boundsInRoot.center - origin
                val to = destination.fetchSemanticsNode().boundsInRoot.center - origin
                list.performTouchInput {
                    down(from)
                    advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100L)
                    moveTo(from)
                    moveTo(to)
                    up()
                }
            }
            fun before(a: SemanticsNodeInteraction, b: SemanticsNodeInteraction) =
                a.fetchSemanticsNode().boundsInRoot.top < b.fetchSemanticsNode().boundsInRoot.top

            assertTrue(before(first, second))
            dragCard(first, second)
            assertTrue(before(second, first))
            back()
            openList()
            assertTrue(before(second, first))
            dragCard(first, second)
            assertTrue(before(first, second))

            back()
            compose.onNodeWithTag("open-antenna-construction").performScrollTo().performClick()
            compose.onNodeWithTag("antenna-construction-tools").assertIsDisplayed()
            compose.onNodeWithTag("open-construction-yagi").assertHasClickAction().assertIsDisplayed()
        } finally {
            preferences.edit().putString(listTag, saved).commit()
        }
    }
}
