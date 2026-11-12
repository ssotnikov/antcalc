package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.core.measurements.Touchstone
import local.antenna.calculator.measurements.MeasurementTrace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwrBandwidthScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun loadsVerifiedTracesAndKeepsCriterionAcrossNavigationAndRelaunch() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalculatorApplication
        val storage = app.measurementStorage
        val previous = storage.readWorkspace()
        val preferences = app.getSharedPreferences("measured-swr-bandwidth", Context.MODE_PRIVATE)
        val previousPreferences = preferences.all
        check(previousPreferences.isEmpty())
        val a = MeasurementTrace("two-bands.s1p", Touchstone.read(
            "# MHz S RI R 50\n100 0.5 0\n110 0 0\n120 0.5 0\n130 0 0\n140 0.5 0".byteInputStream(),
        ))
        val b = MeasurementTrace("outside-band.s1p", Touchstone.read(
            "# MHz S RI R 50\n100 0.5 0\n110 0.5 0".byteInputStream(),
        ))
        try {
            storage.writeWorkspace(listOf(a, b))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openBandwidth()
                val list = compose.onNodeWithTag("bandwidth-screen")
                val threshold = compose.onNodeWithTag("bandwidth-threshold")
                list.performScrollToNode(hasTestTag("bandwidth-interval-1"))
                compose.onNodeWithTag("bandwidth-interval-1").assertIsDisplayed()
                list.performScrollToIndex(0)
                click("bandwidth-trace-1")
                list.performScrollToNode(hasTestTag("bandwidth-empty"))
                compose.onNodeWithTag("bandwidth-empty").assertIsDisplayed()
                list.performScrollToIndex(1)
                threshold.performTextReplacement("0")
                compose.onNodeWithTag("bandwidth-empty").assertDoesNotExist()
                compose.onNodeWithTag("bandwidth-copy").assertDoesNotExist()
                threshold.performTextReplacement("2,5")
                scenario.recreate()
                compose.onNodeWithTag("bandwidth-screen").performScrollToIndex(1)
                threshold.assertTextContains("2,5")
                compose.onNodeWithTag("bandwidth-screen").performScrollToIndex(0)
                click("bandwidth-open-measurements")
                compose.onNodeWithTag("measurement-screen").assertExists()
                click("back")
                compose.onNodeWithTag("bandwidth-screen").assertExists()
                click("back")
                compose.onNodeWithTag("open-bandwidth").assertExists()
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                openBandwidth()
                compose.onNodeWithTag("bandwidth-screen").performScrollToIndex(1)
                compose.onNodeWithTag("bandwidth-threshold").assertTextContains("2.5")
            }
            assertEquals(listOf(a, b), storage.readWorkspace())
        } finally {
            storage.writeWorkspace(previous)
            preferences.edit().clear().commit()
        }
    }

    private fun openBandwidth() {
        click("nav-TOOLS")
        compose.onNodeWithTag("open-additional-tools").performScrollTo()
        click("open-additional-tools")
        click("open-bandwidth")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("bandwidth-trace-1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }
}
