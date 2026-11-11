package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorEvent
import local.antenna.calculator.state.FieldKey
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroundPlaneScreenTest : CalculatorUiFixture() {
    @Test fun toolValidatesGeometryAndKeepsSizingInputs() {
        start()
        openTool()
        val diagram = compose.onNodeWithTag("ground-plane-diagram")
        diagram.performScrollTo().assertIsDisplayed()
        for (symbol in listOf("Lₛ", "Lᵣ", "H", "h", "β", "D")) {
            diagram.assert(hasContentDescription(symbol, substring = true))
        }
        compose.onNodeWithText("Вид сверху").assertDoesNotExist()
        val copy = compose.onNodeWithTag("ground-plane-copy")
        copy.performScrollTo().assertIsEnabled()
        val angle = compose.onNodeWithTag("ground-plane-ANGLE")
        angle.performScrollTo().performTextReplacement("90")
        compose.onNodeWithTag("ground-plane-notice").performScrollTo().assertTextContains("накладываются", substring = true)
        diagram.assertDoesNotExist()
        copy.performScrollTo().assertIsNotEnabled()
        angle.performScrollTo().performTextReplacement("30")
        copy.performScrollTo().assertIsEnabled()
        back()
        openTool()
        angle.performScrollTo().assertTextContains("30")
        compose.onNodeWithTag("nav-SIZING").performClick()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("145")
    }

    @Test fun bothLocalesCanReadAndReachTheToolAtDoubleFontSize() {
        start(fontScale = 2f)
        for (language in AppLanguage.entries) {
            val russian = language == AppLanguage.RUSSIAN
            compose.runOnIdle {
                val settings = model.state.value.draft.settings
                val theme = if (russian) AppTheme.DARK else AppTheme.LIGHT
                model.onEvent(CalculatorEvent.Settings(settings.copy(language = language, theme = theme)))
            }
            openTool()
            compose.onNodeWithTag("ground-plane-copy").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-references").performClick()
            compose.onNodeWithTag("source-smith-antenna-theory").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("source-smith-berkeley").assertExists()
            compose.onNodeWithTag("source-ground-plane-m0ukd").assertExists()
            val russianSource = compose.onNodeWithTag("source-smith-rutube")
            if (russian) russianSource.assertExists() else russianSource.assertDoesNotExist()
            back()
            back()
        }
    }

    private fun openTool() {
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-ground-plane").performScrollTo().performClick()
    }
}
