package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.nec.OpenNec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiNecInterruptionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cancellationInputChangesAndFailureAllowARetry() {
        val geometry = listOf(Element(0.0, 3.0, .02), Element(.62, 2.8, .02))
        var runs = 0
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    YagiNecPanel(geometry, geometry, 50_100_000, null, null, sweep = { _, _, grid ->
                        when (++runs) {
                            1, 2, 3 -> awaitCancellation()
                            4 -> null
                            else -> grid.map { OpenNec.Point(it, 50.0, 0.0, 1.0, List(73) { 2.0 }) }
                        }
                    }, onApply = {})
                }
            }
        }
        click("opennec-expand")
        click("opennec-run")
        click("opennec-cancel")
        status(R.string.opennec_cancelled)
        click("opennec-run")
        compose.onNodeWithTag("opennec-high").performScrollTo().performTextReplacement("51")
        status(R.string.opennec_inputs_changed)
        compose.onNodeWithTag("opennec-result").assertDoesNotExist()
        click("opennec-optimize")
        click("opennec-cancel")
        status(R.string.opennec_cancelled)
        compose.onNodeWithTag("opennec-optimization").assertDoesNotExist()
        click("opennec-run")
        status(R.string.opennec_failed)
        click("opennec-run")
        compose.onNodeWithTag("opennec-result").performScrollTo().assertIsDisplayed()
    }

    @Test fun unavailableEngineLeavesAnalyticalScalingUsable() {
        compose.setContent {
            MaterialTheme {
                YagiScalingScreen(initial = null, onSave = null, necAvailable = { false }, onCopied = {})
            }
        }
        click("ys-example")
        status(R.string.opennec_unavailable)
        compose.onNodeWithTag("ys-result-diagram").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ys-copy").assertIsEnabled()
        compose.onNodeWithTag("opennec-expand").assertDoesNotExist()
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    private fun status(id: Int) =
        compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().assertIsDisplayed()
}
