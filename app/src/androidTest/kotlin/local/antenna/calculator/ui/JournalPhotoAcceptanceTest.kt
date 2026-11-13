package local.antenna.calculator.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalPhotoAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensPhotoEditsCommentAndConfirmsDeletion() {
        val project = AntennaProject(UUID.randomUUID(), "Photo project", AntennaType.MONOPOLE)
        val repository = (compose.activity.application as CalculatorApplication).projectsRepository
        val photo = ByteArrayOutputStream().also { output ->
            val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
        }.toByteArray()
        try {
            runBlocking {
                repository.update { it + project }
                repository.addPhoto(project.id) { photo.inputStream() }
            }
            compose.activityRule.scenario.onActivity { ViewModelProvider(it)[JournalViewModel::class.java].reload() }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-TOOLS").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-TOOLS").performClick()
            compose.onNodeWithTag("open-journal").performScrollTo().performClick()
            val card = hasTestTag("journal-project") and hasText(project.title)
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("journal-projects").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("journal-projects").performScrollToNode(card)
            compose.onNode(card).performClick()
            val history = compose.onNodeWithTag("journal-history")
            history.performScrollToNode(hasTestTag("journal-photo-card"))
            compose.onNodeWithTag("journal-photo-card").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("journal-photo-preview").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("journal-photo-preview").performClick()
            val fullscreen = compose.onNodeWithTag("journal-photo-fullscreen")
            fullscreen.assertIsDisplayed()
            val initialZoom = fullscreen.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            fullscreen.performTouchInput {
                val inner = Offset(width * 0.1f, 0f)
                val outer = Offset(width * 0.3f, 0f)
                pinch(start0 = center - inner, start1 = center + inner, end0 = center - outer, end1 = center + outer)
            }
            val enlargedZoom = fullscreen.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            assertNotEquals(initialZoom, enlargedZoom)
            fullscreen.performTouchInput {
                val inner = Offset(width * 0.1f, 0f)
                val outer = Offset(width * 0.35f, 0f)
                pinch(start0 = center - outer, start1 = center + outer, end0 = center - inner, end1 = center + inner)
            }
            assertEquals(initialZoom, fullscreen.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            compose.onNodeWithTag("journal-photo-fullscreen-close").performClick()
            fullscreen.assertDoesNotExist()
            compose.onNodeWithTag("journal-photo-preview").assertIsDisplayed()
            compose.onNodeWithTag("journal-photo-edit").performScrollTo().performClick()
            compose.onNodeWithTag("journal-photo-comment-input").performTextReplacement("Feed point")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("journal-photo-comment-input").assertTextContains("Feed point")
            compose.onNodeWithTag("journal-photo-comment-save").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("journal-photo-comment") and hasText("Feed point"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("journal-photo-comment").assertTextContains("Feed point")
            compose.onNodeWithTag("back").performClick()
            history.performScrollToNode(hasTestTag("journal-photo-card"))
            compose.onNodeWithTag("journal-photo-card").assertIsDisplayed().performClick()
            compose.onNodeWithTag("journal-photo-actions").performScrollTo().performClick()
            compose.onNodeWithTag("journal-photo-delete").performClick()
            compose.onNodeWithTag("journal-photo-confirm-delete").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("journal-history").fetchSemanticsNodes().isNotEmpty() }
            history.performScrollToNode(hasTestTag("journal-photos"))
            compose.onNodeWithTag("journal-photo-export-all").assertIsNotEnabled()
        } finally {
            runBlocking { repository.update { projects -> projects.filterNot { it.id == project.id } } }
        }
    }
}
