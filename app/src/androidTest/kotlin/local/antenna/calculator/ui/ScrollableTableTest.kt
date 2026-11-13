package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScrollableTableTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun scrollbarAppearsOnlyForOverflowAndMovesTheTable() {
        val overflow = ScrollState(0)
        val fitting = ScrollState(0)
        compose.setContent {
            Box(Modifier.width(220.dp)) {
                Column {
                    ScrollableTable(Modifier.width(440.dp), scrollState = overflow) { Text("Wide table") }
                    ScrollableTable(Modifier.width(110.dp), scrollState = fitting) { Text("Narrow table") }
                }
            }
        }

        val scrollbar = compose.onNodeWithTag("table-horizontal-scrollbar")
        scrollbar.assertIsDisplayed()
        scrollbar.performTouchInput { swipeRight(startX = width * .2f, endX = width * .45f) }
        compose.runOnIdle {
            assertTrue(overflow.value in 1..overflow.maxValue)
            assertTrue(fitting.maxValue == 0 && fitting.value == 0)
        }
    }
}
