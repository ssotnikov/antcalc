package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.R
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorEvent
import local.antenna.calculator.state.FieldKey
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvertedVScreenTest : CalculatorUiFixture() {
    @Test fun validationUpdatesTheDiagramAndLeavingPreservesBothTools() {
        start()
        openTool()
        val height = compose.onNodeWithTag("inverted-v-HEIGHT")
        val diagram = compose.onNodeWithTag("inverted-v-diagram")
        val copy = compose.onNodeWithTag("inverted-v-copy")
        diagram.performScrollTo().assertIsDisplayed()
        val span = R.string.inverted_v_span
        compose.onNodeWithTag("inverted-v-result-$span").performScrollTo().assertTextContains("8,66 м")
        val arm = R.string.dipole_arm
        compose.onNodeWithTag("inverted-v-result-$arm").performScrollTo().assertTextContains("5,00 м")
        height.performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("inverted-v-ground-notice").performScrollTo().assertIsDisplayed()
        copy.performScrollTo().assertIsEnabled()
        height.performScrollTo().performTextReplacement("")
        diagram.assertDoesNotExist()
        copy.performScrollTo().assertIsNotEnabled()
        height.performScrollTo().performTextReplacement("7,5")
        back()
        compose.onNodeWithTag("antenna-parameter-tools").assertIsDisplayed()
        compose.onNodeWithTag("open-inverted-v").performScrollTo().performClick()
        height.performScrollTo().assertTextContains("7,5")
        compose.onNodeWithTag("nav-SIZING").performClick()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("145")
    }

    @Test fun localizedReadingAndControlsAtDoubleFontSize() {
        start(fontScale = 2f)
        for (language in AppLanguage.entries) {
            compose.runOnIdle {
                val settings = model.state.value.draft.settings
                val theme = if (language == AppLanguage.RUSSIAN) AppTheme.DARK else AppTheme.LIGHT
                model.onEvent(CalculatorEvent.Settings(settings.copy(language = language, theme = theme)))
            }
            openTool()
            val russian = language == AppLanguage.RUSSIAN
            val title = if (russian) "Петлевой вибратор в антенне Inverted V" else "Here Is a Dipole"
            compose.onNodeWithTag("inverted-v-source").performScrollTo().assertIsDisplayed().assertTextContains(title)
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-references").performClick()
            val source = if (russian) "ru" else "en"
            val other = if (russian) "en" else "ru"
            compose.onNodeWithTag("source-inverted-v-$source").performScrollTo().assertIsDisplayed().assertTextContains(title)
            compose.onNodeWithTag("source-inverted-v-$other").assertDoesNotExist()
            back()
            back()
        }
    }

    private fun openTool() {
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-inverted-v").performScrollTo().performClick()
    }
}
