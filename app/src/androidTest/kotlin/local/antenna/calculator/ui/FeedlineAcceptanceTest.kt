package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import local.antenna.calculator.state.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedlineAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun switchesProfileAndRejectsMissingFrequency() {
        prepare(AppLanguage.RUSSIAN)
        compose.onNodeWithTag("open-feedline").performScrollTo().performClick()
        compose.onNodeWithTag("feedline-result").performScrollTo()
        compose.onNodeWithText("74,47 Вт").assertExists()
        compose.onNodeWithTag("feedline-profile").performScrollTo().performClick()
        compose.onNodeWithText("Belden 8267 (RG-213)").performClick()
        compose.onNodeWithTag("feedline-result").performScrollTo()
        compose.onNodeWithText("56,32 Вт").assertExists()
        compose.onNodeWithTag("feedline-frequency").performScrollTo().performTextReplacement("915")
        compose.onNodeWithTag("feedline-result").assertDoesNotExist()
        compose.onNodeWithTag("feedline-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("В паспорте нет точки", substring = true).assertExists()
    }

    @Test fun cableProfileUsesCatalogAndPreservesManualInput() {
        prepare(AppLanguage.ENGLISH)
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.onNodeWithTag("cable-profile").performScrollTo().performClick()
        compose.onNodeWithText("Times Microwave LMR-400").performClick()
        compose.onNodeWithTag("cable-profile-vf").assertTextContains("Datasheet velocity factor · 0.84")
        compose.onNodeWithTag("cable-VELOCITY").assertDoesNotExist()
        compose.onNodeWithTag("cable-profile").performScrollTo().performClick()
        compose.onNodeWithText("Manual").performClick()
        compose.onNodeWithTag("cable-VELOCITY").performScrollTo().assertTextContains("0,66")
    }

    private fun prepare(language: AppLanguage) {
        lateinit var model: CalculatorViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[CalculatorViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.loading && compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            model.onEvent(CalculatorEvent.Settings(model.state.value.draft.settings.copy(section = Section.TOOLS, language = language)))
            model.onEvent(CalculatorEvent.Feedline(FeedlineDraft(length = "10", power = "100")))
            model.onEvent(CalculatorEvent.Cable(CableDraft()))
        }
        compose.onNodeWithTag("nav-TOOLS").performClick()
    }
}
