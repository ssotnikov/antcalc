package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.layout.YagiDesign
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiCalculatorTest : CalculatorUiFixture() {

    @Test fun opensFromAntennaToolsAndReturnsFromGuide() {
        val prefs = compose.activity.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
        val previous = prefs.getString("profile", null)
        prefs.edit().putString("profile", "r3udk").commit()
        try {
            start(fontScale = 2f)
            compose.onNodeWithTag("nav-TOOLS").performClick()
            compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
            compose.onNodeWithTag("open-yagi-calculator").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-calculator").assertExists()
            compose.onNodeWithText("Размеры ведомости относятся к выбранной конструкции", substring = true)
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
            input(0, "10")
            save.performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(0.1565, pending.snapshot.outputs.single { it.key == "yagi_driven_half_blank" }.value, 1e-9)
                assertEquals(0.010, pending.snapshot.inputs.single { it.key == "yagi_driven_gap" }.value, 1e-9)
            }
            compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-profile-gtv_sat").performScrollTo().performClick()
            listOf("10", "103", "83").forEachIndexed(::input)
            save.performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(listOf("YAGI_PARTIAL_CUT_LIST"), pending.snapshot.warnings)
                assertEquals(0.4822, pending.snapshot.outputs.single { it.key == "yagi_hdriven_half_blank" }.value, 1e-9)
                assertEquals(false, pending.snapshot.outputs.any { it.key == "yagi_vdriven_half_blank" })
            }
            listOf("12", "100", "82").forEachIndexed { index, text -> input(index + 3, text) }
            save.performScrollTo().performClick()
            compose.runOnIdle {
                val type = AntennaType.YAGI
                val project = pending.newProject(type, "SAT").copy(records = listOf(pending.record(type, "Cut list")))
                val restored = JournalJson.decodeTransfer(ByteArrayInputStream(JournalJson.encodeTransfer(project)))
                assertEquals(project, restored)
                val snapshot = checkNotNull(restored.records.single().calculation)
                assertEquals(emptyList<String>(), snapshot.warnings)
                assertEquals(0.012, snapshot.inputs.single { it.key == "yagi_vdriven_gap" }.value, 1e-9)
                assertEquals(0.4824, snapshot.outputs.single { it.key == "yagi_vdriven_half_blank" }.value, 1e-9)
            }
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
            compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-profile-ybn_2_5m").performClick()
            compose.onNodeWithTag("yagi-dimensions-table", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("1485 mm", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("yagi-save").assertIsEnabled()
            compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
            compose.onNodeWithTag("yagi-profile-gtv_2_6m").performClick()
            compose.onNodeWithTag("yagi-dimensions-table", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Director 4", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("2358 mm", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("2398 mm", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("970.83 mm", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("yagi-input-1").performScrollTo().assertTextContains("40")
            compose.onNodeWithTag("yagi-input-1").performTextReplacement("1001")
            compose.onNodeWithTag("yagi-copy").assertIsNotEnabled()
            compose.onNodeWithTag("yagi-input-1").performScrollTo().performTextReplacement("40")
            for (design in YagiDesign.entries.filter { it != YagiDesign.R3 }) {
                compose.onNodeWithTag("yagi-profile").performScrollTo().performClick()
                compose.onNodeWithTag("yagi-profile-${design.id}").performScrollTo().performClick()
                compose.onNodeWithTag("yagi-save").performScrollTo().performClick()
                compose.runOnIdle {
                    val project = checkNotNull(saved)
                    val restored = JournalJson.decodeTransfer(ByteArrayInputStream(JournalJson.encodeTransfer(project)))
                    assertEquals(project, restored)
                    val snapshot = checkNotNull(restored.records.single().calculation)
                    assertEquals(design.model, snapshot.model)
                    val expectedHalf = when (design) {
                        YagiDesign.YBN -> 492.95
                        YagiDesign.DUO -> 464.0
                        YagiDesign.GTV -> 490.2
                        YagiDesign.GTV_DATV -> 481.7
                        YagiDesign.GTV_DATV_THROUGH -> 482.7
                        else -> null
                    }
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
                    if (design.verticalElements.isNotEmpty()) {
                        assertEquals(design.elements.size + design.verticalElements.size,
                            snapshot.inputs.count { it.key.endsWith("_table") })
                        val h = snapshot.inputs.single { it.key == "yagi_de_table" }
                        val v = snapshot.inputs.single { it.key == "yagi_vde_table" }
                        assertEquals(design.elements[1].overallMm, h.value * 1000, 1e-9)
                        assertEquals(design.verticalElements[1].overallMm, v.value * 1000, 1e-9)
                        assertEquals(false, snapshot.outputs.any { it.key == "yagi_vde_blank" })
                        if (design == YagiDesign.GTV_SAT) {
                            assertEquals(956.23, h.value * 1000, 1e-9)
                            assertEquals(959.23, v.value * 1000, 1e-9)
                        }
                    }
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
