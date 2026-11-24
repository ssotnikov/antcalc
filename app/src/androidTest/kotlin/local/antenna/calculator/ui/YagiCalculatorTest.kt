package local.antenna.calculator.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.layout.YagiDesign
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.journal.JournalJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiCalculatorTest : CalculatorUiFixture() {

    @Test fun profileMenuGroupsDgDesignsAfterR3udkAndRightsUseTheSameTypography() {
        org.junit.Assume.assumeTrue("DG7YBN materials are included only in the licensed build",
            YagiProfile.YBN.design.available && YagiProfile.GTV.design.available)
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.scrollToAntennaTool("open-yagi-calculator").performClick()
        val picker = compose.onNodeWithTag("yagi-profile").performScrollTo()
        val width = picker.fetchSemanticsNode().boundsInRoot.width
        picker.performClick()
        val ordered = (listOf(YagiProfile.R3, YagiProfile.YBN, YagiProfile.GTV) + YagiProfile.entries)
            .distinct().filter { it.design.available }
        val positions = ordered.map { profile ->
            compose.onNodeWithTag("yagi-profile-${profile.id}").fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        assertTrue(compose.onNodeWithTag("yagi-profile-${YagiProfile.R3.id}")
            .fetchSemanticsNode().boundsInRoot.width >= width - 1)
        compose.onNodeWithTag("yagi-profile-${YagiProfile.YBN.id}").performSemanticsAction(SemanticsActions.OnClick)
        fun style(node: SemanticsNodeInteraction): androidx.compose.ui.text.TextStyle {
            node.performScrollTo()
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style
        }
        val english = style(compose.onNodeWithText("Design by DG7YBN, no republishing", substring = true))
        val russian = style(compose.onNodeWithText("Конструкция DG7YBN.", substring = true))
        assertEquals(russian.fontSize, english.fontSize)
        assertEquals(russian.fontFamily, english.fontFamily)
        assertEquals(russian.fontWeight, english.fontWeight)
        assertEquals(russian.lineHeight, english.lineHeight)
    }

    @Test fun opensFromAntennaToolsAndReturnsFromGuide() {
        val prefs = compose.activity.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
        val previous = prefs.getString("profile", null)
        prefs.edit().putString("profile", "r3udk").commit()
        try {
            start(fontScale = 2f)
            compose.onNodeWithTag("nav-TOOLS").performClick()
            compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
            compose.scrollToAntennaTool("open-yagi-calculator").performClick()
            compose.onNodeWithTag("yagi-calculator").assertExists()
            val dimensions = hasAnyAncestor(hasTestTag("yagi-dimensions-table"))
            for (designation in listOf("R", "DE", "D1", "D2")) {
                compose.onNode(hasText(designation) and dimensions).assertExists()
            }
            compose.onNodeWithText("Размеры заготовок относятся к выбранной конструкции", substring = true)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("yagi-open-guide").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-guide").assertExists()
            back()
            compose.onNodeWithTag("yagi-open-guide").assertIsDisplayed()
            compose.onNodeWithTag("screen-title").performClick()
            compose.onNodeWithTag("yagi-profile").assertIsDisplayed()
        } finally {
            prefs.edit().putString("profile", previous).commit()
        }
    }

    @Test fun guideSelectsR3udkAndRelatedLinksReturnToTheirEntryPoint() {
        val prefs = compose.activity.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
        val previous = prefs.getString("profile", null)
        prefs.edit().putString("profile", YagiProfile.GTV.id).commit()
        try {
            start()
            compose.onNodeWithTag("nav-TOOLS").performClick()
            compose.onNodeWithTag("open-antenna-construction").performScrollTo().performClick()
            compose.onNodeWithTag("open-construction-yagi").performScrollTo().performClick()
            val guide = compose.onNodeWithTag("yagi-guide")
            guide.performScrollToNode(hasTestTag("yagi-guide-open-calculator"))
            compose.onNodeWithTag("yagi-guide-open-calculator").performClick()

            val profile = compose.onNodeWithTag("yagi-profile")
            val guideLink = compose.onNodeWithTag("yagi-open-guide")
            profile.assertIsDisplayed().assertTextContains(YagiProfile.R3.label, substring = true)
            profile.performClick()
            compose.onNodeWithTag("yagi-profile-${YagiProfile.GTV.id}").performClick()
            guideLink.assertDoesNotExist()
            compose.onNodeWithText("Открыть руководство R3UDK").assertDoesNotExist()
            back()
            guide.assertExists()

            compose.onNodeWithTag("yagi-guide-open-calculator").performClick()
            profile.assertTextContains(YagiProfile.R3.label, substring = true)
            guideLink.performScrollTo().performClick()
            guide.assertExists()
            back()
            compose.onNodeWithTag("open-construction-yagi").assertExists()
        } finally {
            prefs.edit().putString("profile", previous).commit()
        }
    }

    @Test fun scalingShowsMissingDataAndOpensResultDiagramAutomatically() {
        compose.setContent {
            MaterialTheme { YagiScalingScreen(initial = null, onSave = null, onCopied = {}) }
        }

        compose.onNodeWithTag("ys-position-0").assertDoesNotExist()
        compose.onNodeWithTag("ys-position-1").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("ys-missing-data").performScrollTo().assertIsDisplayed()
        val diagramElement = compose.onNodeWithTag("ys-diagram-selection-1")
        diagramElement.assertDoesNotExist()

        compose.onNodeWithTag("ys-example").performScrollTo().performClick()
        compose.onNodeWithTag("ys-missing-data").assertDoesNotExist()
        diagramElement.performScrollTo().assertIsDisplayed()
    }

    @Test fun mountedScalingOnlyAllowsProportionalTransfer() {
        compose.setContent {
            MaterialTheme { YagiScalingScreen(initial = null, onSave = null, onCopied = {}) }
        }
        compose.onNodeWithTag("ys-example").performClick()
        compose.onNodeWithTag("ys-mode-BOTH").performScrollTo().performClick()
        compose.onNodeWithTag("ys-geometry-MOUNTED").performScrollTo().performClick()
        compose.onNodeWithTag("ys-mode-BOTH").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("ys-dimensions-table").performScrollTo().assertExists()
        compose.onNodeWithTag("opennec-blocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("opennec-expand").assertDoesNotExist()
    }

    @Test fun scalingDiagramSelectsAnElementFromEitherGeometry() {
        compose.setContent {
            MaterialTheme { YagiScalingScreen(initial = null, onSave = null, onCopied = {}) }
        }

        compose.onNodeWithTag("ys-example").performScrollTo().performClick()
        compose.onNodeWithTag("ys-target").performScrollTo().performTextReplacement("290")
        val diagram = compose.onNodeWithTag("ys-result-diagram").performScrollTo()
        fun tapElement(positionMm: Double) {
            diagram.performTouchInput {
                val scale = minOf(width * .82 / 810, height * .82 / 1040)
                click(Offset((width / 2f + (positionMm - 405) * scale).toFloat(), height / 2f))
            }
        }
        // The original first director is close to the scaled second director.
        tapElement(415.0)
        compose.onNodeWithTag("ys-diagram-selection-2").assertIsSelected()
        tapElement(405.0)
        compose.onNodeWithTag("ys-diagram-selection-3").assertIsSelected()
    }

    @Test fun scalingShowsAndCopiesFrequencyAndDiameterSteps() {
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-additional-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-yagi-scaling").performScrollTo().performClick()
        compose.onNodeWithTag("ys-example").performScrollTo().performClick()
        compose.onNodeWithTag("ys-mode-BOTH").performScrollTo().performClick()
        fun edit(tag: String, value: String) {
            compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        }
        edit("ys-source", "144,1")
        edit("ys-target", "50,1")
        edit("ys-diameter", "10")
        edit("ys-length-2", "900")
        compose.onNodeWithTag("ys-element-diameters-2").performScrollTo().performClick()
        edit("ys-target-d-2", "12")
        compose.onNodeWithTag("ys-diagram-selection-2").performScrollTo().performClick()
        compose.onNodeWithTag("ys-comparison").performScrollTo().performClick()
        for (line in listOf("Длина после переноса частоты", "Поправка на диаметр", "Полное изменение длины",
            "2656,5", "2588,6", "+67,9", "+1756,5")) {
            compose.onNodeWithText(line).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("ys-copy").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            val report = checkNotNull(clipboard.primaryClip).getItemAt(0).text.toString()
            val columns = report.lineSequence().first { it.startsWith("Элемент |") }.split(" | ")
            assertEquals(listOf("Длина после переноса частоты", "Поправка на диаметр", "Полное изменение длины"),
                columns.takeLast(3))
            val director = report.lineSequence().first { it.startsWith("D1 |") }.split(" | ")
            assertEquals(listOf("2656,5", "12", "2588,6", "+67,9", "+1756,5"), director.drop(2))
        }
    }

    @Test fun scalingCanCheckBothModelsAndRejectsAGapWithoutLosingTheResult() {
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-additional-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-yagi-scaling").performScrollTo().performClick()
        compose.onNodeWithTag("ys-example").performScrollTo().performClick()
        compose.onNodeWithTag("ys-mode-BOTH").performScrollTo().performClick()
        compose.onNodeWithTag("opennec-expand").performScrollTo().performClick()
        compose.onNodeWithText("Проверка в NEC").assertExists()
        compose.onNodeWithTag("opennec-run").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("opennec-result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("После переноса частоты, до поправки на диаметр").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Пересчитанная геометрия").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("opennec-pattern-details").performScrollTo().performClick()
        compose.onNodeWithTag("opennec-smith-plot").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Желтый пунктир", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("opennec-low").performScrollTo().performTextReplacement("174")
        compose.onNodeWithTag("opennec-result").assertDoesNotExist()
        compose.onNodeWithTag("ys-gap").performScrollTo().performTextReplacement("10")
        compose.onNodeWithText("Модель питания для вибратора с физическим зазором еще не проверена.",
            substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ys-result-diagram").assertExists()
    }

    @Test fun openNecLicenseIsAvailableFromSettings() {
        start()
        settings()
        compose.onNodeWithTag("settings-opennec-license").performScrollTo().performClick()
        compose.onNodeWithTag("opennec-license").assertExists()
        compose.onNodeWithText("Original NEC-2 source licence:", substring = true).assertExists()
        back()
        compose.onNodeWithTag("settings-scroll").assertExists()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        compose.onNodeWithTag("settings-opennec-license").assertTextContains("OpenNEC license")
            .performScrollTo().performClick()
        compose.onNodeWithText("Original NEC-2 source licence:", substring = true).assertExists()
    }

    @Test fun nec2SettingsBadgeOpensTheWebsite() {
        start()
        settings()
        val requests = mutableListOf<String>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                requests += intent.dataString.orEmpty()
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("settings-nec2-website").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf("https://www.nec2.org/"), requests) }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun driverInputsProduceSeparateCutLengthsAndSurviveProjectExchange() {
        val prefs = compose.activity.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
        val previous = prefs.all
        prefs.edit().clear().putString("profile", YagiDesign.DK50.id).commit()
        try {
            lateinit var pending: PendingProjectCalculation
            compose.setContent {
                MaterialTheme {
                    YagiCalculatorScreen(onGuide = {}, onMeasurements = null,
                        onSave = { snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) },
                        onCopied = {})
                }
            }
            val save = compose.onNodeWithTag("yagi-save")
            fun input(index: Int, text: String) {
                compose.onNodeWithTag("yagi-driver-$index").performScrollTo().performTextReplacement(text)
            }
            input(0, "101")
            save.assertIsNotEnabled()
            val example = compose.onNodeWithTag("yagi-driver-example")
            example.assertIsNotEnabled()
            input(0, "")
            example.performScrollTo().performClick()
            save.performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(0.1565, pending.snapshot.outputs.single { it.key == "yagi_driven_half_blank" }.value, 1e-9)
                assertEquals(0.010, pending.snapshot.inputs.single { it.key == "yagi_driven_gap" }.value, 1e-9)
            }
            compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-profile-gtv_sat").assertDoesNotExist()
        } finally {
            val editor = prefs.edit().clear()
            previous.forEach { (key, value) -> if (value is String) editor.putString(key, value) }
            editor.commit()
        }
    }

    @Test fun profileCutListsSurviveJsonAndDraftsStaySeparate() {
        val prefs = compose.activity.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
        val original = prefs.all
        prefs.edit().clear().commit()
        try {
            var saved: AntennaProject? = null
            compose.setContent {
                MaterialTheme {
                    YagiCalculatorScreen(onGuide = {}, onMeasurements = null, onSave = { snapshot, note ->
                        val pending = PendingProjectCalculation(snapshot, note = note)
                        saved = pending.newProject(AntennaType.YAGI, "Yagi")
                            .copy(records = listOf(pending.record(AntennaType.YAGI, "Cut list")))
                    }, onCopied = {})
                }
            }
            compose.onNodeWithTag("yagi-input-1").performScrollTo().performTextReplacement("30")
            compose.onNodeWithTag("yagi-input-2").performScrollTo().performTextReplacement("40")
            compose.onNodeWithTag("yagi-save").performScrollTo().performClick()
            compose.runOnIdle {
                val project = checkNotNull(saved)
                val restored = JournalJson.decodeTransfer(ByteArrayInputStream(JournalJson.encodeTransfer(project)))
                assertEquals(project, restored)
                val metric = restored.records.single().calculation!!.outputs.first { it.key == "yagi_boom" }
                assertEquals(0.938, metric.value, 0.000001)
            }
            for (design in YagiDesign.entries.filter { it.available && it != YagiDesign.R3 }) {
                compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
                compose.onNodeWithTag("yagi-profile-${design.id}").performScrollTo().performClick()
                compose.onNodeWithTag("yagi-save").performScrollTo().performClick()
                compose.runOnIdle {
                    val project = checkNotNull(saved)
                    val restored = JournalJson.decodeTransfer(ByteArrayInputStream(JournalJson.encodeTransfer(project)))
                    assertEquals(if (design.restricted) emptyList() else project.records, restored.records)
                    val snapshot = checkNotNull(project.records.single().calculation)
                    assertEquals(design.model, snapshot.model)
                    val expectedHalf = design.drivenCut?.halfBlankMm
                    val half = snapshot.outputs.singleOrNull { it.key == "yagi_driven_half_blank" }
                    val gap = snapshot.outputs.singleOrNull { it.key == "yagi_driven_gap" }
                    if (expectedHalf == null) {
                        assertEquals(listOf("YAGI_PARTIAL_CUT_LIST"), snapshot.warnings)
                        assertEquals(null, half)
                        assertEquals(null, gap)
                    } else {
                        assertEquals(2, snapshot.revision)
                        assertEquals(emptyList<String>(), snapshot.warnings)
                        assertEquals(expectedHalf, checkNotNull(half).value * 1000, 1e-9)
                        assertEquals(10.0, checkNotNull(gap).value * 1000, 1e-9)
                    }
                    assertEquals(false, snapshot.outputs.any { it.key == "yagi_de_blank" })
                    if (design == YagiDesign.DK28) {
                        assertEquals(0.120, snapshot.outputs.first { it.key == "yagi_match_rg179" }.value, 1e-9)
                    }
                    if (design == YagiDesign.DUO) {
                        assertEquals(null, project.target)
                    }
                }
            }
            compose.onNodeWithTag("yagi-save").assertIsEnabled()
            compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-profile-r3udk").performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithTag("yagi-input-1").performScrollTo().assertTextContains("30")
        } finally {
            prefs.edit().clear().apply {
                original.forEach { (key, value) -> if (value is String) putString(key, value) }
            }.commit()
        }
    }
}
