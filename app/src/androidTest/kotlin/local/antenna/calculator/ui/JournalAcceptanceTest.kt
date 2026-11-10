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
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val name = compose.onNodeWithTag("journal-name")
        val examples = hasText("Portable dipole") or hasText("Диполь для выездов")
        name.assert(examples)
        name.assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText,
            androidx.compose.ui.text.AnnotatedString(""),
        ))
        edit("journal-name", project)
        name.assert(!examples)
        click("journal-type-SYMMETRIC_DIPOLE")
        edit("journal-target", "145,512")
        edit("journal-installation", installation)
        val feedline = "LMR-240, 7 m"
        val feedField = compose.onNodeWithTag("journal-feedline")
        feedField.performScrollTo().performClick()
        val russian = compose.onAllNodesWithText("Симметричная линия 450 Ом").fetchSemanticsNodes().isNotEmpty()
        val ladderLine = compose.onNodeWithText(if (russian) "Симметричная линия 450 Ом" else "450 Ω ladder line")
        ladderLine.assertExists()
        feedField.performTextReplacement(if (russian) "р" else "adder")
        ladderLine.assertDoesNotExist()
        feedField.performTextReplacement(if (russian) "  сИм" else "  450 Ω LaD")
        ladderLine.assertExists()
        feedField.performTextReplacement("LMR-2")
        compose.onNodeWithText("LMR-240").performScrollTo().performClick()
        feedField.assertTextContains("LMR-240")
        feedField.performTextInput(", 7 m")
        feedField.assertTextContains(feedline)
        click("journal-save")
        waitFor("journal-history")
        repeat(2) { compose.onNodeWithTag("back").performClick() }

        click("open-inverted-v")
        click("inverted-v-mode-MEASURED")
        click("inverted-v-length-unit-METERS")
        edit("inverted-v-ARM", "5")
        edit("inverted-v-ANGLE", "120")
        edit("inverted-v-HEIGHT", "1")
        compose.onNodeWithTag("inverted-v-save").performScrollTo().assertIsNotEnabled()
        edit("inverted-v-HEIGHT", "8")
        click("inverted-v-save")
        waitFor("project-transfer-project-picker")
        val confirm = compose.onNodeWithTag("project-transfer-confirm-project")
        confirm.assertIsNotEnabled()
        compose.onNodeWithTag("project-transfer-project-search").performTextReplacement("145.512")
        val projectRow = hasTestTag("project-transfer-project") and hasText(project)
        val list = compose.onNodeWithTag("project-transfer-project-list")
        list.performScrollToNode(projectRow)
        compose.onNodeWithTag("project-transfer-project-search").performTextReplacement(project)
        list.performScrollToNode(projectRow)
        compose.onNode(projectRow).performClick().assertIsSelected()
        confirm.assertIsEnabled()
        compose.activityRule.scenario.recreate()
        waitFor("project-transfer-project-picker")
        compose.onNodeWithTag("project-transfer-project-search").assertTextContains(project)
        compose.onNode(projectRow).assertIsSelected()
        confirm.performClick()
        waitFor("inverted-v-screen")
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
        compose.onNodeWithText(feedline, substring = true).assertExists()
        compose.onNodeWithText("120°", substring = true).assertExists()
    }

    @Test fun invertedVCreatesDipoleProjectsFromBothInputModes() {
        val suffix = UUID.randomUUID().toString().take(6)
        val measuredName = "Measured V $suffix"
        val estimatedName = "Estimated V $suffix"
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-inverted-v")
        click("inverted-v-mode-MEASURED")
        click("inverted-v-length-unit-METERS")
        edit("inverted-v-ARM", "5")
        edit("inverted-v-HEIGHT", "8")
        edit("inverted-v-ANGLE", "120")
        click("inverted-v-save")
        compose.onNodeWithTag("project-transfer-create").performClick()
        compose.onNodeWithTag("back").performClick()
        waitFor("project-transfer-project-picker")
        compose.onNodeWithTag("back").performClick()
        waitFor("inverted-v-screen")
        click("inverted-v-save")
        compose.onNodeWithTag("project-transfer-create").performClick()
        edit("project-transfer-name", measuredName)
        click("project-transfer-save-new")
        waitFor("inverted-v-screen")
        val measured = savedProject(measuredName)
        assertEquals(AntennaType.SYMMETRIC_DIPOLE, measured.antennaType)
        assertEquals(10_000_000_000L, measured.initialLength?.nanometers)
        assertNull(measured.target)
        assertEquals("inverted_v", measured.records.single().calculation?.model)

        click("inverted-v-mode-ESTIMATED")
        click("inverted-v-frequency-unit-MEGAHERTZ")
        edit("inverted-v-FREQUENCY", "14.2")
        edit("inverted-v-FACTOR", "0.95")
        click("inverted-v-save")
        compose.onNodeWithTag("project-transfer-create").performClick()
        edit("project-transfer-name", estimatedName)
        compose.activityRule.scenario.recreate()
        waitFor("project-transfer-name")
        compose.onNodeWithTag("project-transfer-name").assertTextContains(estimatedName)
        click("project-transfer-save-new")
        waitFor("inverted-v-screen")
        val estimated = savedProject(estimatedName)
        assertEquals(AntennaType.SYMMETRIC_DIPOLE, estimated.antennaType)
        assertEquals(14_200_000L, estimated.target?.hertz)
        val snapshot = requireNotNull(estimated.records.single().calculation)
        assertEquals("inverted_v", snapshot.model)
        assertEquals(snapshot.outputs.single { it.key == "dipole_total" }.value,
            requireNotNull(estimated.initialLength).nanometers / 1e9, 1e-9)
    }

    @Test fun projectExamplesHideOnFocusAndNeverBecomeSavedData() {
        val title = "Empty fields " + UUID.randomUUID().toString().take(6)
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-journal")
        waitFor("journal-new-project")
        compose.onNodeWithTag("journal-new-project").performClick()

        val name = compose.onNodeWithTag("journal-name")
        val example = hasText("Portable dipole") or hasText("Диполь для выездов")
        name.assert(example).performClick().assert(!example)
        compose.onNodeWithTag("journal-target").performScrollTo().performClick()
        name.assert(example)
        click("journal-save")
        val required = hasText("Enter a name.") or hasText("Введите название.")
        name.assertIsDisplayed()
        compose.onNode(required).assertIsDisplayed()

        edit("journal-name", title)
        click("journal-save")
        waitFor("journal-history")
        val application = compose.activity.application as CalculatorApplication
        val saved = runBlocking { application.projectsRepository.read().single { it.title == title } }
        assertNull(saved.target)
        assertNull(saved.initialLength)
        assertEquals("", saved.installation)
        assertEquals("", saved.feedline)
        assertEquals("", saved.note)
    }

    @Test fun recordSaveScrollsToMissingMeasurementAndImpedanceField() {
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-journal")
        waitFor("journal-new-project")
        compose.onNodeWithTag("journal-new-project").performClick()
        edit("journal-name", "Scroll " + UUID.randomUUID().toString().take(6))
        click("journal-save")
        waitFor("journal-history")
        click("journal-add")
        edit("journal-record-name", "Measurement")
        click("journal-save-record")
        compose.onNodeWithTag("journal-length").assertIsDisplayed()
        val measurementRequired = hasText("Enter at least one measurement.") or hasText("Введите хотя бы один замер.")
        compose.onNode(measurementRequired).assertIsDisplayed()

        edit("journal-resistance", "50")
        click("journal-save-record")
        compose.onNodeWithTag("journal-reactance").assertIsDisplayed()
        val pairRequired = hasText("Enter both R and X when recording impedance.") or hasText("Для импеданса укажите и R, и X.")
        compose.onNode(pairRequired).assertIsDisplayed()

        edit("journal-reactance", "0")
        click("journal-save-record")
        waitFor("journal-record-details")
    }

    @Test fun measurementFormSharesExamplesAndSuggestionsInEnglishDarkTheme() {
        val project = openNewRecord("ENGLISH", "DARK")
        val name = compose.onNodeWithTag("journal-record-name")
        val example = hasText("First sweep")
        name.assert(example).performClick().assert(!example)
        val length = compose.onNodeWithTag("journal-length")
        length.performScrollTo().performClick().assert(!hasText("98.3"))
        name.assert(example)
        click("journal-save-record")
        name.assertIsDisplayed()
        compose.onNodeWithText("Enter a name.").assertIsDisplayed()

        edit("journal-record-name", "Before adjustment")
        edit("journal-resistance", "61")
        edit("journal-reactance", "-12")
        val feedline = compose.onNodeWithTag("journal-feedline")
        feedline.performScrollTo().assertTextContains("RG-213, 10 m").performClick()
        edit("journal-feedline", "58")
        compose.onNodeWithTag("journal-feedline-suggestions").assertDoesNotExist()
        edit("journal-feedline", "  rG-5")
        compose.onNodeWithText("RG-58").performScrollTo().performClick()
        edit("journal-feedline", "RG-58, 5 m")
        compose.onNodeWithTag("journal-installation").performScrollTo()
            .assertTextContains("Inherited setup").performTextClearance()

        compose.activityRule.scenario.recreate()
        feedline.performScrollTo().assertTextContains("RG-58, 5 m")
        click("journal-save-record")
        waitFor("journal-record-details")
        val saved = savedProject(project).records.single()
        assertEquals("", saved.installation)
        assertEquals("", saved.note)
        assertEquals("RG-58, 5 m", saved.feedline)
        val measurement = requireNotNull(saved.measurement)
        assertNull(measurement.length)
        assertNull(measurement.resonance)
        assertNull(measurement.swr)
        assertNull(measurement.testFrequency)
        assertEquals(-12.0, measurement.reactanceOhms)
    }

    @Test fun calculationFormKeepsClearedValuesEmptyInRussianLightTheme() {
        val project = openNewRecord("RUSSIAN", "LIGHT")
        click("journal-record-type-CALCULATION")
        compose.onNodeWithTag("journal-length").assertDoesNotExist()
        compose.onNodeWithTag("journal-record-name").assertTextContains("Расчёт перед подстройкой")
        edit("journal-record-name", "Расчёт перед установкой")
        compose.onNodeWithTag("journal-installation").performScrollTo().performClick()
        edit("journal-installation", "  на МА")
        compose.onNodeWithText("На мачте").performScrollTo().performClick()
        compose.onNodeWithTag("journal-feedline").performScrollTo()
            .assertTextContains("RG-213, 10 m").performTextClearance()
        click("journal-save-record")
        waitFor("journal-record-details")
        val saved = savedProject(project).records.single()
        assertEquals("На мачте", saved.installation)
        assertEquals("", saved.feedline)
        assertEquals("", saved.note)
        assertNull(saved.measurement)
        assertTrue(saved.calculation != null)
    }

    private fun openNewRecord(language: String, theme: String): String {
        val project = "Form " + UUID.randomUUID().toString().take(6)
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        click("language-$language")
        click("theme-$theme")
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-journal")
        waitFor("journal-new-project")
        click("journal-new-project")
        edit("journal-name", project)
        edit("journal-installation", "Inherited setup")
        edit("journal-feedline", "RG-213, 10 m")
        click("journal-save")
        waitFor("journal-history")
        click("journal-add")
        return project
    }

    private fun savedProject(title: String) = runBlocking {
        val application = compose.activity.application as CalculatorApplication
        application.projectsRepository.read().single { it.title == title }
    }

    @Test fun calculatorTransfersTypedSnapshotsAndCreatesProjectWithFirstEntry() {
        val name = "Sizing " + UUID.randomUUID().toString().take(6)
        val monopoleName = "Monopole " + UUID.randomUUID().toString().take(6)
        val tuningName = "Tuning " + UUID.randomUUID().toString().take(6)
        waitFor("nav-SIZING")
        compose.onNodeWithTag("nav-SIZING").performClick()
        click("unit-SIZING_FREQUENCY-MEGAHERTZ")
        edit("field-SIZING_FREQUENCY", "145.5")
        edit("field-FACTOR", "0.95")
        click("copy-to-project")
        click("project-transfer-type-SYMMETRIC_DIPOLE")
        click("project-transfer-continue")
        waitFor("project-transfer-project-picker")
        compose.onNodeWithTag("project-transfer-create").performClick()
        click("project-transfer-save-new")
        compose.onNodeWithTag("project-transfer-name").assertIsDisplayed()
        edit("project-transfer-name", name)
        compose.activityRule.scenario.recreate()
        waitFor("project-transfer-name")
        compose.onNodeWithTag("project-transfer-name").assertTextContains(name)
        click("project-transfer-save-new")
        waitFor("copy-to-project")
        val sizing = savedProject(name)
        assertEquals(145_500_000L, sizing.target?.hertz)
        val first = requireNotNull(sizing.records.single().calculation)
        assertEquals("sizing", first.model)
        assertEquals(listOf("frequency", "factor"), first.inputs.map { it.key })
        assertEquals(listOf("wavelength", "ideal_quarter", "ideal_half", "dipole_total", "dipole_arm"),
            first.outputs.map { it.key })
        val dipoleMeters = first.outputs.single { it.key == "dipole_total" }.value
        assertEquals(dipoleMeters, requireNotNull(sizing.initialLength).nanometers / 1e9, 1e-9)

        click("copy-to-project")
        click("project-transfer-type-MONOPOLE")
        click("project-transfer-continue")
        compose.onNodeWithTag("project-transfer-create").performClick()
        edit("project-transfer-name", monopoleName)
        click("project-transfer-save-new")
        waitFor("copy-to-project")
        val monopole = savedProject(monopoleName)
        val monopoleSnapshot = requireNotNull(monopole.records.single().calculation)
        assertEquals(listOf("wavelength", "ideal_quarter", "ideal_five_eighths", "monopole_length"),
            monopoleSnapshot.outputs.map { it.key })
        click("copy-to-project")
        click("project-transfer-type-MONOPOLE")
        click("project-transfer-continue")
        compose.onNodeWithTag("project-transfer-project-search").performTextReplacement(monopoleName)
        val monopoleRow = hasTestTag("project-transfer-project") and hasText(monopoleName)
        compose.onNodeWithTag("project-transfer-project-list").performScrollToNode(monopoleRow)
        compose.onNode(monopoleRow).performClick()
        compose.onNodeWithTag("project-transfer-confirm-project").performClick()
        waitFor("copy-to-project")
        assertEquals(listOf(monopoleSnapshot, monopoleSnapshot), savedProject(monopoleName).records.map { it.calculation })

        val legacy = first.copy(outputs = (first.outputs + monopoleSnapshot.outputs).distinctBy { it.key })
        val presentation = JournalPresentation(compose.activity)
        for ((record, expected) in listOf(sizing.records.single() to first, monopole.records.single() to monopoleSnapshot)) {
            val rendered = presentation.recordDetails(record.copy(calculation = legacy)).toSet()
            assertEquals(presentation.recordDetails(record.copy(calculation = expected)).toSet(), rendered)
        }

        compose.onNodeWithTag("nav-TRIMMING").performClick()
        click("antenna-SYMMETRIC_DIPOLE")
        click("unit-ACTUAL_LENGTH-CENTIMETERS")
        click("unit-MEASURED-MEGAHERTZ")
        click("unit-TARGET-MEGAHERTZ")
        edit("field-ACTUAL_LENGTH", "99")
        edit("field-MEASURED", "144.4")
        edit("field-TARGET", "146")
        click("copy-to-project")
        waitFor("project-transfer-project-picker")
        compose.onNodeWithTag("project-transfer-project-search").performTextReplacement(name)
        val row = hasTestTag("project-transfer-project") and hasText(name)
        compose.onNodeWithTag("project-transfer-project-list").performScrollToNode(row)
        compose.onNode(row).performClick()
        compose.onNodeWithTag("project-transfer-frequency-warning").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitFor("project-transfer-frequency-warning")
        compose.onNodeWithTag("project-transfer-confirm-project").performClick()
        waitFor("copy-to-project")
        val updated = savedProject(name)
        assertEquals(sizing.target, updated.target)
        assertEquals(sizing.initialLength, updated.initialLength)
        val tuning = requireNotNull(updated.records.last().calculation)
        assertEquals("trimming", tuning.model)
        assertEquals(146_000_000.0, tuning.inputs.single { it.key == "target" }.value, 0.0)

        click("copy-to-project")
        compose.onNodeWithTag("project-transfer-create").performClick()
        edit("project-transfer-name", tuningName)
        click("project-transfer-save-new")
        waitFor("copy-to-project")
        val created = savedProject(tuningName)
        assertEquals(990_000_000L, created.initialLength?.nanometers)
        assertEquals(146_000_000L, created.target?.hertz)
        assertEquals(tuning, created.records.single().calculation)
        val open = hasText("Open project") or hasText("Открыть проект")
        compose.onNode(open and hasClickAction()).performClick()
        waitFor("journal-history")
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
