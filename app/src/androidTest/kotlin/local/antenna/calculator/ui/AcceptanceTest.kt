package local.antenna.calculator.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Acceptance checks use the real Activity, SavedStateHandle and private DataStore. */
@RunWith(AndroidJUnit4::class)
class AcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CalculatorViewModel

    private fun prepare(section: Section = Section.SIZING) {
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[CalculatorViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.loading && compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            model.onEvent(CalculatorEvent.Settings(model.state.value.draft.settings.copy(
                language = AppLanguage.RUSSIAN, section = section)))
            for (key in listOf(FieldKey.SIZING_FREQUENCY, FieldKey.MEASURED, FieldKey.TARGET)) {
                model.onEvent(CalculatorEvent.ChangeUnit(key, "MEGAHERTZ"))
                model.onEvent(CalculatorEvent.Edit(key, if (key == FieldKey.TARGET) "155" else "145"))
            }
            model.onEvent(CalculatorEvent.Edit(FieldKey.FACTOR, "0,95"))
            model.onEvent(CalculatorEvent.ChangeUnit(FieldKey.ACTUAL_LENGTH, "MILLIMETERS"))
            model.onEvent(CalculatorEvent.Edit(FieldKey.ACTUAL_LENGTH, "500"))
        }
    }

    @Test fun incompleteDraftSurvivesRotationAndActivityRecreation() {
        prepare()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").performTextReplacement("145,")
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertTextContains("145,")
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertTextContains("145,")
        compose.onNodeWithTag("monopole-result").assertDoesNotExist()
        compose.onNodeWithTag("copy-result").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").performScrollTo().assertTextContains("145,")
        compose.onNodeWithTag("monopole-result").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(15000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    }

    @Test fun restoringReferencesReturnsToHelp() {
        prepare()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").performClick()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-references").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("references-screen").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("formula-wavelength").assertExists()
    }

    @Test fun sizingRecalculationP95() = measure("sizing", Section.SIZING)

    @Test fun trimmingRecalculationP95() = measure("trimming", Section.TRIMMING)

    private fun measure(mode: String, section: Section) {
        prepare(section)
        assertFalse("Performance acceptance requires no debugger", Debug.isDebuggerConnected())
        val samples = mutableListOf<Long>()
        repeat(110) { index ->
            val frequency = 145 + index
            compose.runOnIdle {
                val start = SystemClock.elapsedRealtimeNanos()
                model.onEvent(CalculatorEvent.Edit(
                    if (section == Section.SIZING) FieldKey.SIZING_FREQUENCY else FieldKey.TARGET,
                    frequency.toString()))
                val state = model.state.value
                // Include lazy result evaluation, not merely publication of an input draft.
                val actual = if (section == Section.SIZING) state.sizing.result!!.monopoleMeters
                    else state.trimming.result!!.newLengthMeters
                val elapsed = SystemClock.elapsedRealtimeNanos() - start
                val expected = if (section == Section.SIZING) 299792458.0 * 0.95 / (4 * frequency * 1000000.0)
                    else 0.5 * 145 / frequency
                assertEquals("Result must correspond to this event", expected, actual, 1e-10)
                if (index >= 10) samples += elapsed
            }
        }
        samples.forEachIndexed { index, nanos -> Log.i("AntcalcAcceptance", "PERF,$mode,${Build.VERSION.SDK_INT},${index + 1},$nanos") }
        val p95 = samples.sorted()[94]
        Log.i("AntcalcAcceptance", "P95,$mode,${Build.VERSION.SDK_INT},$p95")
        assertTrue("$mode p95 ${p95 / 1000000.0} ms exceeds 100 ms", p95 <= 100000000L)
    }
}
