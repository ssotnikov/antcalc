package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AntennaRotationTest : CalculatorUiFixture() {
    @Test fun dragKeepsThePageInPlaceAndDoubleTapOpensTheViewer() {
        start()
        openAntennaTool("open-ground-plane")
        val model = compose.onNodeWithTag("antenna-rotating-view")
        val before = model.fetchSemanticsNode().boundsInRoot.top
        model.performTouchInput { swipe(center, center - Offset(0f, 70f)) }
        check(model.fetchSemanticsNode().boundsInRoot.top == before)
        model.performTouchInput { doubleClick() }
        val fullScreen = compose.onNodeWithTag("antenna-full-view")
        fullScreen.assertIsDisplayed()
        fullScreen.performTouchInput { swipe(center, center + Offset(80f, 40f)) }
        fullScreen.performTouchInput { doubleClick() }
        fullScreen.assertDoesNotExist()
        model.assertIsDisplayed()
        val page = compose.onNodeWithTag("ground-plane-screen")
        val y = model.fetchSemanticsNode().boundsInRoot.bottom - page.fetchSemanticsNode().boundsInRoot.top + 45f
        page.performTouchInput { swipe(Offset(centerX, y), Offset(centerX, y - 140f)) }
        check(model.fetchSemanticsNode().boundsInRoot.top < before)
    }

    @Test fun hb9cvOpensPublishedProfilesAndUsesBoundedNumericInput() {
        start()
        openAntennaTool("open-hb9cv")
        compose.onNodeWithTag("antenna-rotating-view").assertIsDisplayed()
        compose.onNodeWithTag("hb9cv-profile").performScrollTo().performClick()
        compose.onNodeWithText("UW3BJ", substring = true).performScrollTo().performClick()
        val overhang = compose.onNodeWithTag("hb9cv_rear").performScrollTo()
        overhang.performTextReplacement("100")
        overhang.performTextReplacement("100 mm")
        overhang.assertTextContains("100")
        compose.onNodeWithTag("screen-title").performClick()
        compose.onNodeWithTag("antenna-rotating-view").assertIsDisplayed()
    }

    private fun openAntennaTool(tag: String) {
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
}
