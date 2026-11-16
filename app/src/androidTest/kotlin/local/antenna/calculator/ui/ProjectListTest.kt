package local.antenna.calculator.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectListTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun projectSearchHighlightsMatchesAndDraggingPreservesHiddenSlots() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalculatorApplication
        val repo = application.projectsRepository
        val first = AntennaProject(UUID.randomUUID(), "Field yagi", AntennaType.YAGI, Frequency.ofHertz(145_500_000))
        val hidden = AntennaProject(UUID.randomUUID(), "Backyard dipole", AntennaType.SYMMETRIC_DIPOLE)
        val last = AntennaProject(UUID.randomUUID(), "Workshop yagi", AntennaType.YAGI, Frequency.ofHertz(144_100_000))
        val example = listOf(first, hidden, last)
        val previous = runBlocking { repo.read().also { repo.update { example } } }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onNodeWithTag("nav-TOOLS").performClick()
            compose.onNodeWithTag("open-journal").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("journal-project").fetchSemanticsNodes().isNotEmpty() }
            val search = compose.onNodeWithTag("journal-project-search")
            val allCards = compose.onAllNodesWithTag("journal-project")
            val firstCard = compose.onNode(hasTestTag("journal-project") and hasText(first.title))
            val lastCard = compose.onNode(hasTestTag("journal-project") and hasText(last.title))

            search.performTextInput("FIELD")
            search.performImeAction()
            allCards.assertCountEquals(1)
            assertTrue(firstCard.fetchSemanticsNode().config[SemanticsProperties.Text]
                .any { it.text == first.title && it.spanStyles.any { span -> span.start == 0 && span.end == 5 } })
            search.performTextReplacement("145,5")
            search.performImeAction()
            allCards.assertCountEquals(1)
            firstCard.assertIsDisplayed()
            search.performTextReplacement("beverage")
            allCards.assertCountEquals(0)

            val yagi = application.getString(R.string.yagi_title)
            search.performTextReplacement(yagi)
            search.performImeAction()
            allCards.assertCountEquals(2)
            val list = compose.onNodeWithTag("journal-projects")
            val origin = list.fetchSemanticsNode().boundsInRoot.topLeft
            val from = firstCard.fetchSemanticsNode().boundsInRoot.center - origin
            val to = lastCard.fetchSemanticsNode().boundsInRoot.center - origin
            list.performTouchInput {
                down(from)
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100L)
                moveTo(from)
                moveTo(to)
            }
            compose.waitUntil(5_000) { lastCard.fetchSemanticsNode().boundsInRoot.center.y - origin.y < to.y - 20 }
            assertTrue(runBlocking { repo.read() } == example)
            list.performTouchInput { moveTo(from) }
            compose.waitUntil(5_000) { lastCard.fetchSemanticsNode().boundsInRoot.center.y - origin.y >= to.y - 1 }
            list.performTouchInput {
                moveTo(to)
                up()
            }
            compose.waitUntil(5_000) { runBlocking { repo.read() } == listOf(last, hidden, first) }

            search.performTextClearance()
            lastCard.performTouchInput { longClick() }
            compose.onNodeWithText(application.getString(R.string.journal_edit_project)).assertIsDisplayed()
            compose.onNodeWithTag("journal-history").assertDoesNotExist()
        } finally {
            scenario.close()
            runBlocking { repo.update { previous } }
        }
    }
}
