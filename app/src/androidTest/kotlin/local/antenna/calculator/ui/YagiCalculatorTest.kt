package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
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
                compose.onNodeWithTag("yagi-profile-${design.id}").performClick()
                compose.onNodeWithTag("yagi-save").performScrollTo().performClick()
                compose.runOnIdle {
                    val project = checkNotNull(saved)
                    val restored = JournalJson.decodeTransfer(ByteArrayInputStream(JournalJson.encodeTransfer(project)))
                    assertEquals(project, restored)
                    val snapshot = checkNotNull(restored.records.single().calculation)
                    assertEquals(design.model, snapshot.model)
                    assertEquals(listOf("YAGI_PARTIAL_CUT_LIST"), snapshot.warnings)
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
            compose.onNodeWithTag("yagi-profile-r3udk").performClick()
            compose.onNodeWithTag("yagi-input-1").performScrollTo().assertTextContains("30")
        } finally {
            prefs.edit().clear().apply {
                original.forEach { (key, value) -> if (value is String) putString(key, value) }
            }.commit()
        }
    }
}
