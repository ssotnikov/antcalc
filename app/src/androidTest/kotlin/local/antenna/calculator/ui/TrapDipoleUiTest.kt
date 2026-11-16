package local.antenna.calculator.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import local.antenna.calculator.core.TrapDipole.Band
import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.measurements.MeasurementTrace
import local.antenna.calculator.measurements.SweepPlane
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrapDipoleUiTest {
    @get:Rule(order = 0)
    val measurementFixture = object : ExternalResource() {
        private val storage get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as CalculatorApplication).measurementStorage
        private var previous = emptyList<MeasurementTrace?>()

        override fun before() {
            previous = storage.readWorkspace()
            val points = listOf(3.55, 3.7, 3.82, 6.99, 7.05, 7.16, 7.22, 13.98, 14.02, 14.23, 14.4)
                .mapIndexed { index, frequency -> S11Point(frequency * 1e6, .15, if (index % 2 == 0) -.03 else .03) }
            storage.writeWorkspace(listOf(MeasurementTrace("Trap sample", S11Sweep(50.0, points), SweepPlane.FEED_POINT), null))
        }

        override fun after() {
            storage.writeWorkspace(previous)
        }
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectsProfilesSavesAndReopensAnExplicitBand() {
        openCalculator()
        Profile.entries.forEach { profile ->
            click("trap-profile")
            compose.onNodeWithTag("trap-profile-${profile.name}").performClick()
            compose.onNodeWithTag("trap-total").performScrollTo().assertIsDisplayed()
            click("trap-copy")
        }
        click("trap-band-40")
        edit("trap-allowance", "50")
        edit("trap-target", "7.155")
        click("trap-save")
        waitFor("project-transfer-project-picker")
        compose.onNodeWithTag("project-transfer-create").performSemanticsAction(SemanticsActions.OnClick)
        val name = "Trap " + UUID.randomUUID().toString().take(6)
        edit("project-transfer-name", name)
        click("project-transfer-save-new")
        waitFor("trap-save")

        val application = compose.activity.application as CalculatorApplication
        val record = runBlocking { application.projectsRepository.read().single { it.title == name } }.records.single()
        val (profile, band, draft) = requireNotNull(record.calculation?.let(TrapDraft::restore))
        assertEquals(Profile.AA4LR, profile)
        assertEquals(Band.M40, band)
        assertEquals(50.0, draft.allowanceMm)
        assertEquals(7.155, draft.target(band))

        repeat(2) { compose.onNodeWithTag("back").performClick() }
        click("open-journal")
        val project = hasTestTag("journal-project") and hasText(name)
        compose.onNodeWithTag("journal-projects").performScrollToNode(project)
        compose.onNode(project).performClick()
        val row = hasText(record.title) and hasClickAction()
        compose.onNodeWithTag("journal-history").performScrollToNode(row)
        compose.onAllNodes(row).onFirst().performClick()
        click("journal-open-trap-dipole")
        compose.onNodeWithTag("trap-target").performScrollTo().assertTextContains("7.155")
    }

    @Test fun measuresAllThreeBandsAndExplainsAnEmptyWindow() {
        openCalculator()
        click("trap-section-${R.string.trap_tuning}")
        for (band in Band.entries) {
            click("trap-band-${band.meters}")
            click("trap-open-bandwidth")
            val screen = compose.onNodeWithTag("bandwidth-screen")
            screen.performScrollToNode(hasTestTag("trap-window-lower"))
            compose.onNodeWithTag("trap-window-lower").assertTextContains(band.lowerMhz.toString())
            screen.performScrollToNode(hasTestTag("bandwidth-interval-0"))
            compose.onNodeWithTag("bandwidth-interval-0").assertIsDisplayed()
            compose.onNodeWithTag("back").performClick()
        }
        click("trap-open-bandwidth")
        val screen = compose.onNodeWithTag("bandwidth-screen")
        screen.performScrollToNode(hasTestTag("trap-window-upper"))
        compose.onNodeWithTag("trap-window-upper").performTextReplacement("8.5")
        compose.onNodeWithTag("trap-window-lower").performTextReplacement("8")
        screen.performScrollToNode(hasTestTag("trap-window-empty"))
        compose.onNodeWithTag("trap-window-empty").assertIsDisplayed()
    }

    @Test fun overviewIsCollapsedUntilOpened() {
        openCalculator()
        val overview = compose.onNodeWithText("G8KW", substring = true)
        overview.assertDoesNotExist()
        click("trap-section-${R.string.trap_overview_title}")
        overview.performScrollTo().assertIsDisplayed()
        click("trap-section-${R.string.trap_overview_title}")
        overview.assertDoesNotExist()
    }

    private fun openCalculator() {
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-antenna-parameter-tools")
        click("open-trap-dipole")
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun edit(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
