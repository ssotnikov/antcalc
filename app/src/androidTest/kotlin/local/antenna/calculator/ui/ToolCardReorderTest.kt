package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolCardReorderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun repeatedDropsKeepTheHeaderPositionAndDoNotOpenTheCards() {
        val orderKey = "antenna-parameter-tools"
        val prefs = compose.activity.getSharedPreferences("tool_card_order", 0)
        val original = prefs.getString(orderKey, null)
        prefs.edit().remove(orderKey).commit()
        var opens = 0
        val open = { opens++; Unit }
        try {
            compose.setContent {
                MaterialTheme {
                    AntennaParameterToolsScreen(open, open, open, open, open, open, open, open)
                }
            }
            val list = compose.onNodeWithTag(orderKey)
            val first = compose.onNodeWithTag("open-inverted-v")
            val second = compose.onNodeWithTag("open-ground-plane")
            val top = first.fetchSemanticsNode().boundsInRoot.top
            repeat(6) { pass ->
                val from = if (pass % 2 == 0) first else second
                val to = if (pass % 2 == 0) second else first
                val listBounds = list.fetchSemanticsNode().boundsInRoot
                val source = from.fetchSemanticsNode().boundsInRoot.center - listBounds.topLeft
                val destination = to.fetchSemanticsNode().boundsInRoot.center - listBounds.topLeft
                list.performTouchInput {
                    down(source)
                    advanceEventTime(700)
                    moveTo(destination + Offset(0f, 12f))
                    up()
                }
                assertTrue(to.fetchSemanticsNode().boundsInRoot.top < from.fetchSemanticsNode().boundsInRoot.top)
                assertEquals(top, to.fetchSemanticsNode().boundsInRoot.top, 1f)
                assertEquals(0, opens)
            }
            assertEquals("open-inverted-v", prefs.getString(orderKey, "")?.substringBefore(','))
            first.performClick()
            compose.runOnIdle { assertEquals(1, opens) }
        } finally {
            prefs.edit().putString(orderKey, original).commit()
        }
    }
}
