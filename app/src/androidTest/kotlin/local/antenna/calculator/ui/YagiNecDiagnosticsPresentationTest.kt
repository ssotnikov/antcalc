package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.nec.OpenNec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiNecDiagnosticsPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun displaysPeaksAndImpedanceWhenTheWorstFrequencyMoves() = direction(moves = true)
    @Test fun displaysPeaksAndImpedanceWhenTheWorstFrequencyStays() = direction(moves = false)

    @Test fun methodSourcesOmitArchiveAndPhysicalGapKeepsNecUnavailable() {
        compose.setContent {
            MaterialTheme {
                YagiScalingScreen(initial = null, onSave = null, necAvailable = { true }, onCopied = {})
            }
        }
        click("ys-example")
        compose.onNodeWithTag("ys-gap").performScrollTo().performTextReplacement("10")
        compose.onNodeWithTag("ys-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("opennec-expand").assertDoesNotExist()
        click("ys-method-sources")
        compose.onNodeWithTag("opennec-pml-example").assertDoesNotExist()
        compose.onNodeWithTag("opennec-band-example").assertDoesNotExist()
        compose.onNodeWithTag("opennec-optimize").assertDoesNotExist()
        compose.onNodeWithTag("opennec-apply").assertDoesNotExist()
    }

    private fun direction(moves: Boolean) {
        val baseline = listOf(Element(0.0, 1.04, .008), Element(.215, .9968, .008))
        val candidate = baseline.toMutableList().apply { this[1] = this[1].copy(length = .9971) }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    YagiNecPanel(baseline, candidate, 145_000_000, null, null, sweep = { geometry, segments, grid ->
                        val after = geometry == candidate
                        val refined = segments.first() > 11
                        val worst = if (moves && after) grid.first() else grid.last()
                        val maximum = if (!after) 1.5 else if (refined) 1.6 else 1.4
                        grid.map { hz ->
                            val r = 35.0 + (if (after) .3 else 0.0) + (if (refined) 2.0 else 0.0)
                            val x = if (after && refined) .2 else 0.0
                            OpenNec.Point(hz, r, x, if (hz == worst) maximum else 1.1, List(73) { 2.0 })
                        }
                    }, onApply = {})
                }
            }
        }
        click("opennec-expand")
        click("opennec-run")
        click("opennec-direction")
        compose.onNodeWithTag("opennec-direction-peaks").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.opennec_direction_indeterminate))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.opennec_direction_indeterminate_help))
            .performScrollTo().assertIsDisplayed()
        click("opennec-direction-details")
        compose.onNodeWithText(compose.activity.getString(R.string.opennec_diag_residual, matchingNumber(compose.activity, 0.2, 4)))
            .performScrollTo().assertIsDisplayed()
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
}
