package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullWaveLoopScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun showsBothCalculationDirectionsAndPreservesTriangleFeedOnRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-TOOLS").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-TOOLS").performClick()
            click("open-antenna-parameter-tools")
            click("open-full-wave-loop")
            compose.onNodeWithTag("loop-square-image").assertExists()
            compose.onNodeWithTag("loop-input-result").performScrollTo().assertTextContains("21.11 m")
            click("loop-shape-TRIANGLE")
            compose.onNodeWithTag("loop-triangle-image").assertExists()
            click("loop-mode-PERIMETER")
            compose.onNodeWithTag("loop-input-result").performScrollTo().assertTextContains("24.982705 MHz")
            compose.onNodeWithTag("loop-band-table").assertDoesNotExist()
            click("loop-band-table-toggle")
            compose.onNodeWithTag("loop-band-table").assertExists()
            compose.onNodeWithText("1.850").assertExists()
            compose.onNodeWithText("165.58").assertExists()
            compose.onNodeWithText("433.000").assertExists()
            click("loop-band-table-toggle")
            compose.onNodeWithTag("loop-band-table").assertDoesNotExist()
            click("loop-feed-QUARTER_FROM_APEX")
            scenario.recreate()
            compose.onNodeWithTag("loop-feed-QUARTER_FROM_APEX").performScrollTo().assertIsSelected()
            click("loop-save")
            compose.onNodeWithTag("project-transfer-project-picker").assertExists()
            compose.onNodeWithTag("project-transfer-create").performClick()
            compose.onNodeWithTag("project-transfer-name").performTextInput("Loop regression fixture")
            click("project-transfer-save-new")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("full-wave-loop-screen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("loop-PERIMETER").performScrollTo().performTextClearance()
            compose.onNodeWithTag("loop-input-result").assertDoesNotExist()
            compose.onNodeWithTag("loop-save").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("loop-copy").performScrollTo().assertIsNotEnabled()
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
}
