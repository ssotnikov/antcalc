package local.antenna.calculator.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorEvent
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmithChartScreenTest : CalculatorUiFixture() {
    @Test
    fun chartRespondsToControlsAndRetainsStateOnReturn() {
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithText("Изучите, как импеданс", substring = true).assertExists()
        compose.onNodeWithTag("open-smith-chart").performScrollTo().performClick()
        compose.onNodeWithTag("smith-legend").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("smith-mode-circle").assertDoesNotExist()
        compose.onNodeWithTag("smith-mode-vector").assertDoesNotExist()
        compose.onNodeWithText("Жёлтый маркер (Z)", substring = true).assertExists()
        compose.onNodeWithText("Зелёный маркер (Γ(d))", substring = true).assertExists()
        val chart = compose.onNodeWithTag("smith-chart")
        chart.performScrollTo().assert(hasContentDescription("74", substring = true))
        chart.performTouchInput {
            swipe(Offset(width * 0.426f, height * 0.671f), Offset(width * 0.6f, height * 0.6f))
        }
        chart.assert(hasContentDescription("74 + j40", substring = true))
        chart.assert(!hasContentDescription("0,375", substring = true))
        chart.performTouchInput {
            val from = Offset(width * 0.330f, height * 0.411f)
            val to = Offset(width * 0.45f, height * 0.35f)
            swipe(from, to)
        }
        chart.assert(!hasContentDescription("74 + j40", substring = true))

        compose.onNodeWithTag("smith-distance").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(250f) }
        compose.onNodeWithTag("smith-line-value").assertTextContains("0,25", substring = true)
        val reference = compose.onNodeWithTag("smith-field-REFERENCE")
        val export = compose.onNodeWithTag("smith-export")
        reference.performScrollTo().performTextReplacement("0")
        export.performScrollTo().assertIsNotEnabled()
        reference.performScrollTo().performTextReplacement("50")
        export.performScrollTo().assertIsEnabled()
        back()
        compose.onNodeWithTag("open-smith-chart").performScrollTo().performClick()
        reference.performScrollTo().assertTextContains("50")
        chart.performScrollTo().assertIsDisplayed()
    }

    @Test
    fun referencePanelIsReachableInBothLocalesAtDoubleFontSize() {
        start(fontScale = 2f)
        for (language in AppLanguage.entries) {
            val russian = language == AppLanguage.RUSSIAN
            compose.runOnIdle {
                val settings = model.state.value.draft.settings
                val theme = if (russian) AppTheme.DARK else AppTheme.LIGHT
                model.onEvent(CalculatorEvent.Settings(settings.copy(language = language, theme = theme)))
            }
            openTool()
            compose.onNodeWithTag("smith-legend").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("smith-anatomy-figure").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("smith-grid-details-figure").performScrollTo().assertIsDisplayed()
            val reference = if (russian) "Опорный импеданс Z₀" else "The reference impedance Z₀"
            compose.onNodeWithText(reference, substring = true).performScrollTo().assertIsDisplayed()
            val tables = compose.onNodeWithTag("smith-tables").performScrollTo().assertIsDisplayed()
            val heading = if (russian) "Типичные параметры кабелей" else "Typical cable parameters"
            val cableTable = compose.onNodeWithText(heading)
            cableTable.assertDoesNotExist()
            tables.performClick()
            cableTable.performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("smith-source").assertDoesNotExist()
            tables.performScrollTo().performClick()
            back()
        }
    }

    private fun openTool() {
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-smith-chart").performScrollTo().performClick()
    }
}
