package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CalculatorToolsUiTest : CalculatorUiFixture() {
    @Test fun cableEnglishDarkLargeTextKeepsActionsReachable() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.ENGLISH, theme = AppTheme.DARK)), fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.onNodeWithTag("cable-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("cable-details").performScrollTo().performClick()
        compose.onNodeWithText("One wavelength").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Phase velocity equals cable velocity factor times the speed of light in vacuum.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-matching").performScrollTo().assertExists()
    }

    @Test fun cableRussianLightLargeTextKeepsInputsReachable() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.RUSSIAN, theme = AppTheme.LIGHT)), fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.onNodeWithText("Длина кабеля").assertIsDisplayed()
        compose.onNodeWithTag("cable-VELOCITY").performScrollTo().assertTextContains("0,66")
        compose.onNodeWithTag("cable-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("cable-diagram").performScrollTo().assertIsDisplayed()
    }

    @Test fun matchingToolsKeepCalculatorDraftAndRejectStaleResults() {
        start()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").performTextReplacement("146,")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-matching").performClick()
        compose.onNodeWithTag("matching-SWR").assertTextContains("2")
        compose.onNodeWithTag("matching-SWR").performTextReplacement("0")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("matching-SWR").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("nav-SIZING").performClick()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertTextContains("146,")
    }

    @Test fun matchingImpedanceAndMagnitudeHaveIndependentInputs() {
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-matching").performClick()
        compose.onNodeWithTag("matching-SWR").performTextReplacement("3,")
        compose.onNodeWithTag("matching-mode-IMPEDANCE").performClick()
        compose.onNodeWithTag("matching-REACTANCE").performTextReplacement("-50")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("matching-mode-SWR").performScrollTo().performClick()
        compose.onNodeWithTag("matching-SWR").assertTextContains("3,")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("matching-mode-GAMMA").performScrollTo().performClick()
        compose.onNodeWithTag("matching-GAMMA").performTextReplacement("1")
        compose.onNodeWithText("∞ : 1").assertExists()
    }

    @Test fun matchingLargeFontEnglishAndDetailsRemainReachable() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.ENGLISH)), fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-matching").performClick()
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("matching-details").performScrollTo().performClick()
        compose.onNodeWithText("Mismatch loss (ML)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Power balance at an SWR of 2:1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-matching").assertExists()
    }
}
