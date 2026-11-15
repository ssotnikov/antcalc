package local.antenna.calculator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.core.Hb9cvAerial
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Hb9cvAerialUiTest : CalculatorUiFixture() {
    @Test fun opensFromAntennaToolsAndRetainsProfileInput() {
        start(fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-hb9cv").performScrollTo().performClick()
        chooseAerial()
        editField("frequency", "435,000")
        compose.onNodeWithTag("hb9cv-aerial-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("screen-title").performClick()
        val modelView = compose.onNodeWithTag("antenna-rotating-view")
        modelView.assertIsDisplayed().performTouchInput { doubleClick() }
        compose.onNodeWithTag("antenna-full-view").assertIsDisplayed().performTouchInput { doubleClick() }
        settings()
        back()
        compose.onNodeWithTag("hb9cv-aerial-frequency").performScrollTo().assertTextContains("435,000")
    }

    @Test fun validatesInputAndSavesAllDimensionsAndCapacitance() {
        lateinit var pending: PendingProjectCalculation
        var vnaOpened = false
        compose.setContent {
            MaterialTheme {
                Hb9cvScreen(
                    onSave = { snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) },
                    onCopied = {},
                    onMeasurements = { vnaOpened = true },
                )
            }
        }
        chooseAerial()
        val copy = compose.onNodeWithTag("hb9cv-aerial-copy")
        copy.performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("hb9cv-aerial-tuning").performScrollTo().performClick()
        compose.onNodeWithTag("hb9cv-aerial-vna").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, vnaOpened) }
        editField("frequency", "1500.001")
        copy.performScrollTo().assertIsNotEnabled()
        editField("frequency", "435.000")
        editField("rear", "15")
        editField("front", "10001")
        copy.performScrollTo().assertIsNotEnabled()
        editField("front", "20")
        compose.onNodeWithTag("hb9cv-aerial-save").performScrollTo().performClick()
        compose.runOnIdle {
            val type = AntennaType.HB9CV
            val project = pending.newProject(type, "HB9CV")
                .copy(records = listOf(pending.record(type, "3G-Aerial")))
            val restored = JournalJson.decodeTransfer(JournalJson.encodeTransfer(project).inputStream())
            assertEquals(project, restored)
            val snapshot = checkNotNull(restored.records.single().calculation)
            assertEquals(Hb9cvAerial.MODEL, snapshot.model)
            assertEquals(13, snapshot.outputs.size)
            val capacitance = snapshot.outputs.single { it.key == HB9CV_AERIAL_CAPACITANCE }
            assertEquals(SnapshotUnit.PICOFARADS, capacitance.unit)
            assertEquals(2304.0 / 435, capacitance.value, 1e-12)
            assertEquals((35 + 29952.0 / 435) / 1000, snapshot.outputs.single { it.key == "hb9cv_boom" }.value, 1e-12)
        }
    }

    private fun chooseAerial() {
        compose.onNodeWithTag("hb9cv-profile").performScrollTo().performClick()
        compose.onNodeWithTag("hb9cv-aerial-profile").performClick()
    }

    private fun editField(field: String, value: String) {
        compose.onNodeWithTag("hb9cv-aerial-$field").performScrollTo().performTextReplacement(value)
    }
}
