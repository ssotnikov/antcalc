package local.antenna.calculator.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.measurements.MeasurementTrace
import local.antenna.calculator.measurements.SweepPlane
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixedJournalAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun projectComparesAManualRecordWithATraceAndWithSizing() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalculatorApplication
        val repository = application.projectsRepository
        val id = UUID.randomUUID()
        val title = "S1P comparison " + id.toString().take(8)
        val now = OffsetDateTime.now()
        val type = AntennaType.SYMMETRIC_DIPOLE
        val manual = JournalRecord(UUID.randomUUID(), "Manual 145", now, type, measurement = JournalMeasurement(
            length = PhysicalLength.ofNanometers(980_000_000), testFrequency = Frequency.ofHertz(145_000_000),
            swr = 1.3, resistanceOhms = 60.0, reactanceOhms = 5.0, point = MeasurementPoint.FEED_POINT, referenceOhms = 50.0,
        ))
        val sizing = JournalRecord(UUID.randomUUID(), "Sizing 145", now.minusMinutes(2), type,
            calculation = CalculationSnapshot("sizing", 1, listOf(SnapshotValue("frequency", 145e6, SnapshotUnit.HERTZ)),
                listOf(SnapshotValue("dipole_total", 0.9821, SnapshotUnit.METERS))))
        val trimming = sizing.copy(id = UUID.randomUUID(), title = "Trimming 145",
            calculation = checkNotNull(sizing.calculation).copy(model = "trimming"))
        val trace = MeasurementTrace("before.s1p", S11Sweep(50.0, listOf(S11Point(145e6, 0.1, 0.0))), SweepPlane.FEED_POINT)
        val followUp = trace.copy(name = "after.s1p", plane = SweepPlane.TRANSCEIVER)
        try {
            runBlocking {
                repository.update { it + AntennaProject(id, title, type, records = listOf(manual, sizing, trimming)) }
                repository.saveSweeps(id, listOf(trace, followUp))
                assertEquals(50.0, repository.read().first { it.id == id }.records.first().measurement?.referenceOhms)
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                click("nav-TOOLS")
                compose.onNodeWithTag("open-journal").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
                val project = hasTestTag("journal-project") and hasText(title)
                waitFor("journal-projects")
                compose.onNodeWithTag("journal-projects").performScrollToNode(project)
                compose.onNode(project).performSemanticsAction(SemanticsActions.OnClick)
                waitFor("journal-history")
                val history = compose.onNodeWithTag("journal-history")
                val selectManual = hasTestTag("journal-select-record") and
                    hasContentDescription(application.getString(R.string.journal_select, manual.title))
                fun selectionFor(record: JournalRecord) = hasTestTag("journal-select-record") and
                    hasContentDescription(application.getString(R.string.journal_select, record.title))
                fun selectRecord(record: JournalRecord) {
                    val selection = selectionFor(record)
                    history.performScrollToNode(selection)
                    compose.onNode(selection).performSemanticsAction(SemanticsActions.OnClick)
                }
                val selectSweep = hasTestTag("journal-select-sweep")
                history.performScrollToNode(selectSweep)
                click("journal-select-sweep")
                selectRecord(sizing)
                compose.onNode(selectionFor(sizing)).assertIsOff()
                compose.onNodeWithText(application.getString(R.string.journal_sweep_compare_select_manual)).assertExists()
                history.performScrollToNode(selectSweep)
                compose.onNode(selectSweep).assertIsOn()
                selectRecord(manual)
                history.performScrollToNode(hasTestTag("journal-compare"))
                click("journal-compare")
                waitFor("journal-sweep-comparison")
                waitFor("journal-comparable-fields")
                click("journal-compare-trace-1")
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText(application.getString(R.string.journal_compare_points)).fetchSemanticsNodes().isNotEmpty()
                }
                click("back")
                waitFor("journal-history")
                history.performScrollToNode(hasTestTag("journal-select-sweep"))
                click("journal-select-sweep")
                selectRecord(sizing)
                history.performScrollToNode(hasTestTag("journal-compare"))
                click("journal-compare")
                waitFor("journal-geometry-comparison")
                compose.onNodeWithTag("journal-comparable-fields").assertExists()
                compose.onAllNodesWithTag("journal-diff-value").assertCountEquals(0)
                click("back")
                waitFor("journal-history")
                history.performScrollToNode(selectManual)
                compose.onNode(selectManual).performSemanticsAction(SemanticsActions.OnClick)
                selectRecord(trimming)
                compose.onNode(selectionFor(trimming)).assertIsOff()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(application.getString(R.string.journal_reject_calculation_models))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                history.performScrollToNode(selectionFor(sizing))
                compose.onNode(selectionFor(sizing)).assertIsOn()
            }
        } finally {
            runBlocking { repository.update { projects -> projects.filterNot { it.id == id } } }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
