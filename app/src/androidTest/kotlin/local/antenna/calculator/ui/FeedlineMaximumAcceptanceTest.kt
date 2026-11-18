package local.antenna.calculator.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.io.File
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedlineMaximumAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CalculatorViewModel

    private fun prepare(language: AppLanguage = AppLanguage.RUSSIAN, theme: AppTheme = AppTheme.DARK,
        draft: FeedlineDraft = FeedlineDraft(profileId = null, frequency = "150", attenuation = "6,3",
            attenuationHertz = 150_000_000, task = FeedlineTask.MAXIMUM_LENGTH, length = "11", power = "100")) {
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[CalculatorViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.loading && compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            model.onEvent(CalculatorEvent.Settings(model.state.value.draft.settings.copy(
                section = Section.TOOLS, language = language, theme = theme)))
            model.onEvent(CalculatorEvent.Feedline(draft))
        }
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-feedline").performScrollTo().performClick()
    }

    @Test fun limitsConnectionsCopyAndRecreationUseCurrentInputs() {
        prepare()
        val maximum = compose.onNodeWithTag("feedline-max-length")
        maximum.performScrollTo().assertTextContains("47,61", substring = true)
        compose.onNodeWithTag("feedline-allowed-loss").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("feedline-connection-loss").performScrollTo().performTextReplacement("0,4")
        maximum.performScrollTo().assertTextContains("25,39", substring = true)
        compose.onNodeWithTag("feedline-copy").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val copied = clipboard.primaryClip!!.getItemAt(0).coerceToText(compose.activity).toString()
            assertTrue(copied.contains("25,39"))
            assertTrue(copied.contains("0,4"))
            assertTrue(copied.contains("Допустимые потери"))
        }
        compose.onNodeWithTag("feedline-frequency").performScrollTo().performTextReplacement("450")
        compose.onNodeWithTag("feedline-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("feedline-attenuation").performScrollTo().performTextReplacement("12,1")
        maximum.performScrollTo().assertTextContains("13,22", substring = true)
        compose.onNodeWithTag("feedline-copy").performScrollTo().assertIsEnabled()
        compose.activityRule.scenario.recreate()
        maximum.performScrollTo().assertTextContains("13,22", substring = true)
        compose.onNodeWithTag("feedline-details").performScrollTo().performClick()
        compose.onNodeWithTag("feedline-limit-definitions").performScrollTo().assertExists()
        compose.onNodeWithText("Aдоп —", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun noFiniteLengthAndImpossibleAllowanceAreDifferentStates() {
        prepare()
        compose.onNodeWithTag("feedline-attenuation").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("feedline-max-length").performScrollTo()
            .assertTextContains("Конечного предела", substring = true)
        compose.onNodeWithTag("feedline-connection-loss").performScrollTo().performTextReplacement("4")
        compose.onNodeWithTag("feedline-limit-error").performScrollTo().assertExists()
        compose.onNodeWithTag("feedline-max-length").assertDoesNotExist()
        compose.onNodeWithTag("feedline-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("feedline-connection-loss").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("feedline-attenuation").performScrollTo().performTextReplacement("10")
        compose.onNodeWithTag("feedline-max-length").performScrollTo().assertTextContains("0 м", substring = true)
        compose.onNodeWithTag("feedline-allowed-loss").performScrollTo().performTextClearance()
        compose.onNodeWithTag("feedline-result").assertDoesNotExist()
    }

    @Test fun illustrationsAndModeSwitchKeepDraftsSeparate() {
        prepare()
        for (view in listOf("CABLE", "LINK", "CHART")) {
            compose.onNodeWithTag("feedline-illustration-$view").performScrollTo().performClick()
            compose.onNodeWithTag("feedline-illustration-$view").assertIsSelected()
        }
        compose.onNodeWithTag("feedline-task-LOSS").performScrollTo().performClick()
        compose.onNodeWithTag("feedline-length").performScrollTo().assertTextContains("11")
        compose.onNodeWithTag("feedline-allowed-loss").assertDoesNotExist()
        compose.onNodeWithTag("feedline-task-MAXIMUM_LENGTH").performScrollTo().performClick()
        compose.onNodeWithTag("feedline-allowed-loss").performScrollTo().assertTextContains("3")
        compose.onNodeWithTag("feedline-max-length").performScrollTo().assertTextContains("47,61", substring = true)
    }

    @Test fun capturesGuideInBothLanguagesAndThemes() {
        for (language in listOf(AppLanguage.RUSSIAN, AppLanguage.ENGLISH)) {
            for (theme in listOf(AppTheme.DARK, AppTheme.LIGHT)) {
                prepare(language, theme)
                val value = if (language == AppLanguage.RUSSIAN) "47,61" else "47.61"
                compose.onNodeWithTag("feedline-max-length").performScrollTo().assertTextContains(value, substring = true)
                capture("${language.name}-${theme.name}-maximum-result")
                if (compose.onAllNodesWithTag("feedline-limit-definitions").fetchSemanticsNodes().isEmpty()) {
                    compose.onNodeWithTag("feedline-details").performScrollTo().performClick()
                }
                compose.onNodeWithTag("feedline-limit-definitions").performScrollTo().assertExists()
                capture("${language.name}-${theme.name}-definitions")
                compose.onNodeWithTag("screen-title").performClick()
                compose.waitForIdle()
                compose.onNodeWithTag("feedline-illustration-CHART").assertIsDisplayed()
                capture("${language.name}-${theme.name}-maximum")
                compose.onNodeWithTag("feedline-task-LOSS").performClick()
                capture("${language.name}-${theme.name}-loss")
            }
        }
    }

    private fun capture(name: String) {
        val target = File(compose.activity.getExternalFilesDir(null), "feedline-limit-guide").apply { mkdirs() }
        File(target, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
