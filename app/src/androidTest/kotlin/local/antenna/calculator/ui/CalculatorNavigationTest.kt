package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
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

    @Test fun tappingTitleResetsOnlyTheCurrentScreenScroll() {
        start(fontScale = 2f)
        val scroll = compose.onNodeWithTag("calculator-scroll")
        val title = compose.onNodeWithTag("screen-title")
        fun position() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun scrollDown() {
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 380f) }
            assertTrue(position() > 0f)
        }

        scrollDown()
        val sizingPosition = position()
        trimming()
        scrollDown()
        title.performClick()
        assertEquals(0f, position(), 0f)
        compose.onNodeWithTag("nav-SIZING").performClick()
        assertEquals(sizingPosition, position(), 0f)

        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-construction").performScrollTo().performClick()
        compose.onNodeWithTag("open-construction-yagi").performClick()
        val list = compose.onNodeWithTag("yagi-guide")
        val geometry = compose.onNodeWithTag("yagi-section-GEOMETRY")
        list.performScrollToNode(hasTestTag("yagi-section-MATERIALS"))
        compose.onNodeWithTag("yagi-section-MATERIALS").performClick()
        list.performScrollToNode(hasText("Сохранить STL в ZIP"))
        val export = compose.onNodeWithText("Сохранить STL в ZIP")
        export.assertIsDisplayed()
        geometry.assertIsNotDisplayed()
        title.performClick()
        geometry.assertIsDisplayed()
    }

    @Test fun menuPagesReturnToTheOriginAndRestoreScroll() {
        start(fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        val cable = compose.onNodeWithTag("cable-scroll")
        cable.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 420f) }
        fun position() = cable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val saved = position()
        assertTrue(saved > 0f)
        for (tag in listOf("open-settings", "open-about", "open-references")) {
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag(tag).performClick()
            cable.assertDoesNotExist()
            back()
            assertEquals(saved, position(), 0f)
        }

        settings()
        val settingsScroll = compose.onNodeWithTag("settings-scroll")
        settingsScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 160f) }
        val settingsPosition = settingsScroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(settingsPosition > 0f)
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").performClick()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-references").performClick()
        back()
        compose.onNodeWithTag("screen-title").assertTextEquals("Справка")
        back()
        assertEquals(settingsPosition, settingsScroll.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value(), 0f)
        back()
        assertEquals(saved, position(), 0f)
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
        fun bannerPixels(): IntArray {
            val image = banner.captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        val russianPixels = bannerPixels()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        banner.performScrollTo().assertContentDescriptionEquals("LizaAlert — open the search and rescue team website")
        assertFalse("The rendered banner must change with the language", russianPixels.contentEquals(bannerPixels()))
        compose.onNodeWithTag("language-RUSSIAN").performScrollTo().performClick()
        banner.performScrollTo().assertContentDescriptionEquals("ЛизаАлерт — открыть сайт поисково-спасательного отряда")
        assertArrayEquals(russianPixels, bannerPixels())
    }

    @Test fun settingsBannerUsesViewportHeightAndRemainsReachableWhenContentOverflows() {
        val height = mutableIntStateOf(700)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, height.intValue.dp).testTag("settings-viewport")) {
                    SettingsScreen(CalculatorUiState(loading = false), {}, {})
                }
            }
        }
        val viewport = compose.onNodeWithTag("settings-viewport")
        val banner = compose.onNodeWithTag("lizaalert-banner")
        val report = compose.onNodeWithTag("report-bug")

        for ((heightDp, gap) in listOf(700 to 17.5f, 420 to 12f)) {
            compose.runOnIdle { height.intValue = heightDp }
            if (heightDp == 700) banner.assertIsDisplayed()
            else {
                banner.assertIsNotDisplayed()
                compose.onNodeWithTag("settings-scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, 10_000f)
                }
                banner.assertIsDisplayed()
            }
            val bounds = viewport.getUnclippedBoundsInRoot()
            val bannerBounds = banner.getUnclippedBoundsInRoot()
            val expectedGap = gap + with(compose.density) { 20.toDp().value }
            assertEquals(expectedGap, (bounds.bottom - bannerBounds.bottom).value, 1f)
            assertTrue(bannerBounds.top >= report.getUnclippedBoundsInRoot().bottom)
        }
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
