package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@RunWith(AndroidJUnit4::class)
class CalculatorNavigationTest : CalculatorUiFixture() {
    private fun swipeCalculation(right: Boolean) {
        compose.onNodeWithTag("page-content").performTouchInput {
            val start = Offset(width * if (right) 0.15f else 0.85f, height * 0.85f)
            val end = Offset(width * if (right) 0.85f else 0.15f, height * 0.85f)
            swipe(start, end, 350)
        }
        compose.waitForIdle()
    }

    @Test fun swipesKeepIndependentIncompleteInputsAndUpdateBottomNavigation() {
        start()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").performTextReplacement("145,")
        swipeCalculation(right = false)
        compose.onNodeWithTag("nav-TRIMMING").assertIsSelected()
        compose.onNodeWithTag("field-ACTUAL_LENGTH").performTextReplacement("500,")
        swipeCalculation(right = true)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertTextContains("145,")
        swipeCalculation(right = false)
        compose.onNodeWithTag("field-ACTUAL_LENGTH").assertTextContains("500,")
    }

    @Test fun swipeBoundariesShortDragsAndCancellationDoNotNavigate() {
        start()
        swipeCalculation(right = true)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("page-content").performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.85f), Offset(width * 0.57f, height * 0.85f), 350)
        }
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("page-content").performTouchInput {
            down(Offset(width * 0.85f, height * 0.85f))
            moveTo(Offset(width * 0.15f, height * 0.85f), 350)
            cancel()
        }
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        swipeCalculation(right = false)
        swipeCalculation(right = false)
        compose.onNodeWithTag("nav-TRIMMING").assertIsSelected()
    }

    @Test fun verticalSwipesStillScrollWithoutSwitchingCalculations() {
        start(fontScale = 2f)
        val node = compose.onNodeWithTag("calculator-scroll")
        val before = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        node.performTouchInput { swipeUp() }
        val after = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Vertical content should scroll", after > before)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
    }

    @Test fun switchingCalculationsRestoresEachScrollPosition() {
        start(fontScale = 2f)
        val scroll = compose.onNodeWithTag("calculator-scroll")
        fun position() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
        val sizing = position()
        assertTrue(sizing > 0f)
        trimming()
        assertEquals(0f, position(), 0f)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 600f) }
        val tuning = position()
        assertTrue(tuning > sizing)
        swipeCalculation(right = true)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        assertEquals(sizing, position(), 0f)
        swipeCalculation(right = false)
        compose.onNodeWithTag("nav-TRIMMING").assertIsSelected()
        assertEquals(tuning, position(), 0f)
        compose.onNodeWithTag("nav-SIZING").performClick()
        assertEquals(sizing, position(), 0f)
    }

    @Test fun settingsShowsTheLocalizedLizaAlertLinkBelowBugReporting() {
        start()
        settings()
        val banner = compose.onNodeWithTag("lizaalert-banner")
        val report = compose.onNodeWithTag("report-bug")
        banner.performScrollTo().assertHasClickAction()
            .assertContentDescriptionEquals("ЛизаАлерт — открыть сайт поисково-спасательного отряда")
        assertTrue(banner.fetchSemanticsNode().positionInRoot.y > report.fetchSemanticsNode().positionInRoot.y)
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        banner.performScrollTo().assertContentDescriptionEquals("LizaAlert — open the search and rescue team website")
    }

    @Test fun swipesOnMenuPagesLeavePageAndCalculationUnchanged() {
        start()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        swipeCalculation(right = false)
        compose.onNodeWithTag("language-RUSSIAN").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").assertTextContains("Справка").performClick()
        swipeCalculation(right = false)
        compose.onNodeWithTag("back").assertExists()
        compose.onNodeWithTag("nav-SIZING").assertDoesNotExist()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
    }

    @Test fun loadingUsesStartupBackgroundBeforeSettingsAreRestored() {
        val loading = CalculatorUiState(loading = true)
        compose.setContent { CalculatorApp(loading, {}) { loading } }
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals("Loading must not draw a light frame before the saved theme is known",
            Color(0xFF101D26), pixels[pixels.width / 4, pixels.height / 2])
    }

    @Test fun navigationWithKeyboardPreservesIndependentInputsAndBack() {
        start()
        edit(FieldKey.SIZING_FREQUENCY, "433")
        compose.waitUntil(timeoutMillis = 5000) {
            var visible = false
            compose.runOnUiThread { visible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            visible
        }
        compose.onNodeWithTag("nav-TRIMMING").assertIsDisplayed()
        trimming()
        edit(FieldKey.ACTUAL_LENGTH, "500")
        compose.onNodeWithTag("nav-SIZING").performClick()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("433", substring = true)
        settings()
        compose.onNodeWithTag("theme-DARK").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("433", substring = true)
        trimming()
        field(FieldKey.ACTUAL_LENGTH).assertTextContains("500")
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").performClick()
        back()
        field(FieldKey.ACTUAL_LENGTH).assertTextContains("500")
    }
}
