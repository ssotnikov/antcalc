package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.JournalRecord
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

    @Test fun r3udkCutListSurvivesJsonTransferAndSourceOnlyProfilesDoNotOfferIt() {
        val prefs = compose.activity.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)
        val original = prefs.all
        prefs.edit().clear().commit()
        try {
            var saved: AntennaProject? = null
            compose.setContent {
                MaterialTheme {
                    YagiCalculatorScreen(onGuide = {}, onMeasurements = null, onSave = { snapshot, note ->
                        val record = JournalRecord(UUID.randomUUID(), "Cut list", OffsetDateTime.now(),
                            AntennaType.YAGI, note = note, calculation = snapshot)
                        saved = AntennaProject(UUID.randomUUID(), "Yagi", AntennaType.YAGI, records = listOf(record))
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
            compose.onNodeWithTag("yagi-profile-gtv_2_6m").performClick()
            compose.onNodeWithTag("yagi-save").assertDoesNotExist()
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
