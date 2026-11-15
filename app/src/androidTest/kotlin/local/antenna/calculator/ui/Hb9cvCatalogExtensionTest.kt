package local.antenna.calculator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.core.trimming.AntennaType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Hb9cvCatalogExtensionTest : CalculatorUiFixture() {
    @Test fun transfersProfileTargetsAndCalculatesHingedRodBlanks() {
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme {
                Hb9cvScreen(onSave = { snapshot, note ->
                    pending = PendingProjectCalculation(snapshot, note = note)
                }, onCopied = {}, onMeasurements = null)
            }
        }
        val save = compose.onNodeWithTag("hb9cv-save-profile")
        save.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(50_000_000L, pending.newProject(AntennaType.HB9CV, "DK7ZB").target?.hertz)
        }
        compose.onNodeWithTag("hb9cv-profile").performScrollTo().performClick()
        compose.onNodeWithText("F1LVT · 400–406", substring = true).performScrollTo().performClick()
        save.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("hb9cv_hinge_r").performScrollTo().performTextReplacement("120")
        compose.onNodeWithTag("hb9cv_hinge_s").performScrollTo().performTextReplacement("110")
        compose.onNodeWithTag("hb9cv_hinge_margin").performScrollTo().performTextReplacement("5")
        save.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(403_000_000L, pending.newProject(AntennaType.HB9CV, "F1LVT").target?.hertz)
            assertEquals(.1325, pending.snapshot.outputs.single { it.key == "hb9cv_cut_r" }.value, 1e-12)
            assertEquals(.120, pending.snapshot.outputs.single { it.key == "hb9cv_cut_s" }.value, 1e-12)
        }
    }
}
