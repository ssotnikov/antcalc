package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.data.PersistedCalculatorState
import local.antenna.calculator.state.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullWaveLoopScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun showsBothCalculationDirectionsAndPreservesTriangleFeedInRussian() = exercise(AppLanguage.RUSSIAN)

    @Test fun showsBothCalculationDirectionsAndPreservesTriangleFeedInEnglish() = exercise(AppLanguage.ENGLISH)

    private fun exercise(language: AppLanguage) {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalculatorApplication
        val repository = application.settingsRepository
        val previous = runBlocking { repository.read() }
        val fixture = PersistedCalculatorState.defaults()
        val projectName = "Loop regression ${UUID.randomUUID()}"
        val russian = language == AppLanguage.RUSSIAN
        runBlocking { repository.write(fixture.copy(settings = fixture.settings.copy(language = language))) }
        try {
            checkLoop(russian, projectName)
        } finally {
            runBlocking {
                repository.write(previous)
                val projects = application.projectsRepository
                if (projects.read().any { it.title == projectName }) {
                    projects.update { records -> records.filterNot { it.title == projectName } }
                }
            }
        }
    }

    private fun checkLoop(russian: Boolean, projectName: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-TOOLS").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-TOOLS").performClick()
            click("open-antenna-parameter-tools")
            compose.scrollToAntennaTool("open-full-wave-loop").performClick()
            compose.onNodeWithTag("loop-square-image").assertExists()
            compose.onNodeWithTag("loop-input-result").performScrollTo()
                .assertTextContains(if (russian) "21,11 м" else "21.11 m")
            click("loop-shape-TRIANGLE")
            compose.onNodeWithTag("loop-triangle-image").assertExists()
            click("loop-mode-PERIMETER")
            compose.onNodeWithTag("loop-input-result").performScrollTo()
                .assertTextContains(if (russian) "24,982705 МГц" else "24.982705 MHz")
            compose.onNodeWithTag("loop-band-table").assertDoesNotExist()
            click("loop-band-table-toggle")
            compose.onNodeWithTag("loop-band-table").assertExists()
            compose.onNodeWithText(if (russian) "1,850" else "1.850").assertExists()
            compose.onNodeWithText(if (russian) "165,58" else "165.58").assertExists()
            compose.onNodeWithText(if (russian) "433,000" else "433.000").assertExists()
            click("loop-band-table-toggle")
            compose.onNodeWithTag("loop-band-table").assertDoesNotExist()
            click("loop-feed-QUARTER_FROM_APEX")
            scenario.recreate()
            compose.onNodeWithTag("loop-feed-QUARTER_FROM_APEX").performScrollTo().assertIsSelected()
            click("loop-save")
            compose.onNodeWithTag("project-transfer-project-picker").assertExists()
            compose.onNodeWithTag("project-transfer-create").performClick()
            compose.onNodeWithTag("project-transfer-name").performTextInput(projectName)
            click("project-transfer-save-new")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("full-wave-loop-screen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("loop-PERIMETER").performScrollTo().performTextClearance()
            compose.onNodeWithTag("loop-input-result").assertDoesNotExist()
            compose.onNodeWithTag("loop-save").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("loop-copy").performScrollTo().assertIsNotEnabled()
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
}
