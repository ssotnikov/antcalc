package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoxonTest : CalculatorUiFixture() {
    @Before fun resetDraft() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("moxon_calculator", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun navigatesAndScrollsToTheIllustrationAtLargeFont() {
        start(fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-moxon").performScrollTo().performClick()
        compose.onNodeWithTag("moxon-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("screen-title").performClick()
        compose.onNodeWithTag("moxon-art-ROD_A").assertIsDisplayed()
    }

    @Test fun switchesFromPublishedToEditableAndClearsInvalidResults() {
        compose.setContent { MaterialTheme { MoxonScreen(null, null, {}) } }
        choose("moxon_w6ye")
        compose.onNodeWithTag("moxon-art-PVC_BOOM").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("moxon-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("moxon-mode-true").performScrollTo().performClick()
        val frequency = compose.onNodeWithTag("moxon-frequency")
        frequency.performScrollTo().performTextReplacement("435,000")
        compose.onNodeWithTag("moxon-copy").performScrollTo().assertIsEnabled()
        frequency.performScrollTo().performTextReplacement("435,")
        compose.onNodeWithTag("moxon-copy").assertDoesNotExist()
    }

    @Test fun allProfilesShowTheirIllustrationAndAvailableActionsInBothLanguagesAndThemes() {
        val language = mutableStateOf(AppLanguage.ENGLISH)
        val theme = mutableStateOf(AppTheme.LIGHT)
        compose.setContent {
            LocalizedContent(language.value) {
                CalculatorTheme(theme.value) { MoxonScreen({ _, _ -> }, null, {}) }
            }
        }
        for (locale in AppLanguage.entries) {
            for (appearance in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
                compose.runOnIdle {
                    language.value = locale
                    theme.value = appearance
                }
                for (profile in moxonCalculatorProfiles) {
                    choose(profile.id)
                    compose.onNodeWithTag("moxon-art-${profile.view.name}").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("moxon-save").performScrollTo().assertIsEnabled()
                }
            }
        }
    }

    @Test fun excludesReferenceOnlyDesignsAndRecoversPreviousSelection() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("moxon_calculator", Context.MODE_PRIVATE)
            .edit().putString("profile", "moxon_w1cdn").commit()
        compose.setContent { MaterialTheme { MoxonScreen(null, null, {}) } }

        compose.onNodeWithTag("moxon-art-ROD_A").assertIsDisplayed()
        compose.onNodeWithTag("moxon-profile").performScrollTo().performClick()
        for (id in listOf("moxon_w1cdn", "moxon_lora", "moxon_print_10m", "moxon_yama")) {
            compose.onNodeWithTag("moxon-profile-$id").assertDoesNotExist()
        }
        compose.onNodeWithTag("moxon-profile-moxon_w6ye").assertExists()
    }

    @Test fun savesBothBandsAndYagiElementPositionsInTheJournal() {
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme {
                MoxonScreen({ snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) }, null, {})
            }
        }
        choose("moxon_satellite")
        compose.onNodeWithTag("moxon-art-HYBRID").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("moxon-save").performScrollTo().performClick()
        compose.runOnIdle {
            val type = AntennaType.MOXON
            val project = pending.newProject(type, "Satellite")
                .copy(records = listOf(pending.record(type, "Published dimensions")))
            val restored = JournalJson.decodeTransfer(JournalJson.encodeTransfer(project).inputStream())
            assertEquals(project, restored)
            assertNull(restored.target)
            assertNull(restored.initialLength)
            val saved = checkNotNull(restored.records.single().calculation)
            assertEquals("moxon_satellite", saved.model)
            assertEquals(436_500_000.0, saved.inputs.single { it.key == "moxon_second_frequency" }.value, 0.0)
            assertEquals(.925, saved.outputs.single { it.key == "moxon_yagi_position_4" }.value, 1e-12)
        }
    }

    private fun choose(id: String) {
        compose.onNodeWithTag("moxon-profile").performScrollTo().performClick()
        compose.onNodeWithTag("moxon-profile-$id").performScrollTo().performClick()
    }
}
