package local.antenna.calculator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CantennaUiTest : CalculatorUiFixture() {
    @Test fun opensFromToolsWithLargeTextAndCanExpandModel() {
        start(fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-cantenna").performScrollTo().performClick()
        compose.onNodeWithTag("cantenna-screen").assertIsDisplayed()
        compose.onNodeWithTag("antenna-rotating-view").performTouchInput { doubleClick() }
        compose.onNodeWithTag("antenna-full-view").assertIsDisplayed()
        back()
        compose.onNodeWithTag("antenna-full-view").assertDoesNotExist()
    }

    @Test fun preservesSelectedGeometryInProjectExchange() {
        compose.activity.getSharedPreferences("cantenna_calculator", 0).edit().clear().commit()
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme {
                CantennaScreen(null, { snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) }, {})
            }
        }
        compose.onNodeWithTag("cantenna-save").performScrollTo().performClick()
        compose.runOnIdle {
            val type = AntennaType.CANTENNA
            val project = pending.newProject(type, "Cantenna").copy(records = listOf(pending.record(type, "Geometry")))
            val restored = JournalJson.decodeTransfer(JournalJson.encodeTransfer(project).inputStream())
            assertEquals(project, restored)
            val snapshot = checkNotNull(restored.records.single().calculation)
            assertEquals("cantenna_calculated", snapshot.model)
            assertEquals(.085, snapshot.inputs.single { it.key == "cantenna_diameter" }.value, 1e-10)
            assertEquals(.0581, snapshot.outputs.single { it.key == "cantenna_offset" }.value, .0001)
        }
    }
}
