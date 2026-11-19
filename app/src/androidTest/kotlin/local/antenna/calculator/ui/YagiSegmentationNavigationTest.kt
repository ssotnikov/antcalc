package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorDraft
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiSegmentationNavigationTest : CalculatorUiFixture() {
    @Test fun toolsOpenTheStandalonePlannerInEnglish() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.ENGLISH, theme = AppTheme.LIGHT)))
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-additional-tools")
        click("open-yagi-segmentation")
        compose.onNodeWithTag("screen-title").assertTextContains("Yagi–Uda Segmentation")
        click("seg-example")
        compose.onNodeWithText("Source on segment 6 of element 1.").performScrollTo().assertIsDisplayed()
        back()
        compose.onNodeWithTag("open-yagi-segmentation").assertExists()
    }

    @Test fun catalogTransfersThePublishedFreeSpaceExampleInRussian() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.RUSSIAN, theme = AppTheme.DARK)))
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-antenna-parameter-tools")
        compose.scrollToAntennaTool("open-yagi-calculator").performClick()
        compose.onNodeWithTag("open-catalog-segmentation").assertDoesNotExist()
        click("catalog-segmentation-example")
        click("open-catalog-segmentation")
        compose.onNodeWithTag("screen-title").assertTextContains("Сегментация Яги — Уда")
        compose.onNodeWithTag("seg-low").assertTextContains("144")
        compose.onNodeWithTag("seg-high").assertTextContains("146")
        compose.onNodeWithTag("seg-length-0").assertTextContains("1010")
        compose.onNodeWithTag("seg-length-1").assertTextContains("920")
        compose.onNodeWithText("Источник на сегменте 6 элемента 1.").performScrollTo().assertIsDisplayed()
        back()
        compose.onNodeWithTag("yagi-calculator").assertExists()
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
}
