package local.antenna.calculator.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun journalKeepsDraftAndComparesRecordsAfterRecreation() {
        val project = "Dipole " + UUID.randomUUID().toString().take(6)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-TOOLS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-journal").performScrollTo().performClick()
        waitFor("journal-new-project")
        compose.onNodeWithTag("journal-new-project").performClick()
        edit("journal-name", project)
        edit("journal-target", "145")
        compose.onNodeWithTag("journal-name").assertTextContains(project)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("journal-name").assertTextContains(project)
        click("journal-save")
        waitFor("journal-history")
        addRecord("Before", "142.8", "2.4", "FEED_POINT")
        addRecord("After", "145", "1.3", "TRANSCEIVER")
        compose.activityRule.scenario.recreate()
        waitFor("journal-history")
        val history = compose.onNodeWithTag("journal-history")
        for (name in listOf("After", "Before")) {
            val description = compose.activity.getString(R.string.journal_select, name)
            val checkbox = hasContentDescription(description)
            history.performScrollToNode(checkbox)
            compose.onNode(checkbox).performClick()
        }
        history.performScrollToNode(hasTestTag("journal-compare"))
        compose.onNodeWithTag("journal-compare").performClick()
        compose.onNodeWithTag("journal-copy-comparison").assertExists()
        compose.onNodeWithTag("journal-comparison-notice").assertExists()
        assertEquals(6, compose.onAllNodesWithTag("journal-diff-value").fetchSemanticsNodes().size)
        compose.onNodeWithText("Before").assertExists()
        compose.onNodeWithText("After").assertExists()
        click("journal-copy-comparison")
        compose.waitUntil(15_000) { compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            val activity = compose.activity
            val clipboard = activity.getSystemService(ClipboardManager::class.java)!!
            val sections = clipboard.primaryClip!!.getItemAt(0).text.toString().split("\n\n")
            fun field(label: Int, value: String) =
                activity.getString(R.string.label_value, activity.getString(label), value)
            assertEquals(5, sections.size)
            assertEquals(field(R.string.journal_report_project, project), sections[0])
            assertEquals(field(R.string.journal_before, "Before"), sections[1].lineSequence().first())
            assertEquals(field(R.string.journal_after, "After"), sections[2].lineSequence().first())
            assertEquals(field(R.string.journal_compare_notice, activity.getString(R.string.journal_compare_points)), sections[3])
            assertEquals(activity.getString(R.string.journal_scope), sections[4])
        }
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("journal-history").assertExists()
        compose.onNodeWithTag("back").performClick()
        val projects = compose.onNodeWithTag("journal-projects")
        val projectCard = hasTestTag("journal-project") and hasText(project)
        projects.performScrollToNode(projectCard)
        compose.onNode(projectCard).performTouchInput { longClick() }
        val actions = listOf(R.string.journal_edit_project, R.string.journal_duplicate, R.string.journal_export, R.string.journal_delete)
            .map { compose.onNodeWithText(compose.activity.getString(it)) }
        val actionPositions = actions.map { it.fetchSemanticsNode().boundsInRoot.top }
        assertTrue(actionPositions.zipWithNext().all { (a, b) -> a < b })
        actions.last().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.journal_delete_project)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.journal_cancel)).performClick()
        compose.onNode(projectCard).assertExists().performClick()
        compose.onNodeWithTag("journal-history").assertExists()
        compose.onNodeWithTag("journal-project-actions").performClick()
        val historyPositions = actions.map { it.fetchSemanticsNode().boundsInRoot.top }
        assertTrue(historyPositions.zipWithNext().all { (a, b) -> a < b })
        actions.first().performClick()
        compose.onNodeWithTag("journal-name").assertTextContains(project)
        repeat(2) { compose.onNodeWithTag("back").performClick() }
        compose.onNodeWithTag("journal-projects").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-journal").assertExists()
    }

    @Test fun invertedVSnapshotPreservesTheGeometryAndProjectConditions() {
        val project = "Layout " + UUID.randomUUID().toString().take(6)
        val installation = "Central pole, level field"
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-journal")
        waitFor("journal-new-project")
        compose.onNodeWithTag("journal-new-project").performClick()
        edit("journal-name", project)
        click("journal-type-SYMMETRIC_DIPOLE")
        edit("journal-installation", installation)
        click("journal-save")
        waitFor("journal-history")
        repeat(2) { compose.onNodeWithTag("back").performClick() }

        click("open-inverted-v")
        edit("inverted-v-ARM", "5")
        edit("inverted-v-ANGLE", "120")
        edit("inverted-v-HEIGHT", "1")
        compose.onNodeWithTag("inverted-v-save").performScrollTo().assertIsNotEnabled()
        edit("inverted-v-HEIGHT", "8")
        click("inverted-v-save")
        compose.onNode(hasTestTag("inverted-v-project") and hasText(project)).performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("inverted-v-project").fetchSemanticsNodes().isEmpty() }
        edit("inverted-v-ANGLE", "90")
        compose.activityRule.scenario.recreate()
        waitFor("inverted-v-ANGLE")
        compose.onNodeWithTag("back").performClick()

        click("open-journal")
        val projectCard = hasTestTag("journal-project") and hasText(project)
        compose.onNodeWithTag("journal-projects").performScrollToNode(projectCard)
        compose.onNode(projectCard).performClick()
        waitFor("journal-history")
        val title = hasText("Inverted-V dipole") or hasText("Диполь «Перевёрнутая V»")
        compose.onNodeWithTag("journal-history").performScrollToNode(title)
        compose.onNode(title and hasClickAction()).performClick()
        waitFor("journal-record-details")
        compose.onNodeWithText(installation, substring = true).assertExists()
        compose.onNodeWithText("120°", substring = true).assertExists()
    }

    @Test fun feedbackMenuOpensTelegramAndGitHub() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requests = mutableListOf<String?>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW || intent.data?.scheme !in listOf("tg", "https")) return null
                requests += intent.dataString
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("menu").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-references").assertExists()
            val contact = compose.onNodeWithTag("contact-author")
            val bugReport = compose.onNodeWithTag("menu-report-bug")
            assertTrue(contact.fetchSemanticsNode().boundsInRoot.top < bugReport.fetchSemanticsNode().boundsInRoot.top)
            contact.performClick()
            compose.onNodeWithTag("menu").performClick()
            bugReport.performClick()
            compose.runOnIdle {
                assertEquals(listOf("tg://resolve?domain=ssotnikov", BUG_REPORT_URL), requests)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun addRecord(name: String, resonance: String, swr: String, point: String) {
        compose.onNodeWithTag("journal-add").performClick()
        edit("journal-record-name", name)
        edit("journal-resonance", resonance)
        edit("journal-swr", swr)
        edit("journal-test-frequency", "145")
        click("journal-point-$point")
        click("journal-save-record")
        waitFor("journal-record-details")
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithTag("back").performClick()
        waitFor("journal-history")
    }

    private fun edit(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
