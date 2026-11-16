package local.antenna.calculator.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiIllustrationTest : CalculatorUiFixture() {
    @Test fun diagramOpensByButtonAndDoubleTapAndReturnsToTheSameScrollPosition() {
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-construction").performScrollTo().performClick()
        compose.onNodeWithTag("open-construction-yagi").performClick()

        val imageTag = "yagi-illustration-images/00.jpg"
        val buttonTag = "yagi-illustration-expand-images/00.jpg"
        val guide = compose.onNodeWithTag("yagi-guide")
        val preview = compose.onNodeWithTag(imageTag)
        val viewer = compose.onNodeWithTag("yagi-illustration-fullscreen")

        guide.performScrollToNode(hasTestTag(imageTag))
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag(buttonTag).fetchSemanticsNodes().size == 1
        }
        val top = preview.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(buttonTag).performTouchInput { click() }
        fun zoom() = viewer.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val initialZoom = zoom()
        viewer.performTouchInput {
            val inner = Offset(width * .1f, 0f)
            val outer = inner * 3f
            pinch(center - inner, center + inner, center - outer, center + outer)
        }
        assertTrue(zoom() != initialZoom)
        compose.onNodeWithTag("yagi-illustration-fullscreen-reset").performClick()
        assertEquals(initialZoom, zoom())
        compose.onNodeWithTag("yagi-illustration-fullscreen-close").performClick()
        viewer.assertDoesNotExist()
        assertEquals(top, preview.fetchSemanticsNode().boundsInRoot.top, 0f)

        preview.performTouchInput { doubleClick() }
        assertEquals(initialZoom, zoom())
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("yagi-illustration-fullscreen").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(top, preview.fetchSemanticsNode().boundsInRoot.top, 0f)

        preview.performTouchInput { swipe(center, center - Offset(0f, height * .3f)) }
        assertTrue(preview.fetchSemanticsNode().boundsInRoot.top < top)
    }
}
