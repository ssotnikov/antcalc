package local.antenna.calculator.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import local.antenna.calculator.core.feedline.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CableAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun prepare(): CalculatorViewModel {
        lateinit var model: CalculatorViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[CalculatorViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.loading && compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            model.onEvent(CalculatorEvent.Settings(model.state.value.draft.settings.copy(section = Section.TOOLS, language = AppLanguage.RUSSIAN)))
            model.onEvent(CalculatorEvent.Cable(CableDraft()))
        }
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        return model
    }
    @Test fun quarterWavePresetsAndUnitConversion() {
        prepare()
        compose.onNodeWithTag("cable-result-0").performScrollTo().assertTextContains("341,1 мм")
        compose.onNodeWithTag("cable-length-unit-METERS").performScrollTo().performClick()
        compose.onNodeWithTag("cable-result-0").performScrollTo().assertTextContains("0,3411 м")
        compose.onNodeWithTag("cable-preset-180").performScrollTo().performClick()
        compose.onNodeWithTag("cable-ANGLE").assertTextContains("180")
        compose.onNodeWithTag("cable-result-0").performScrollTo().assertTextContains("0,6823 м")
    }
    @Test fun zeroLengthInvalidInputAndIndependentDirections() {
        prepare()
        compose.onNodeWithTag("cable-ANGLE").performScrollTo().performTextReplacement("450")
        selectMode(CableMode.LENGTH_TO_ANGLE)
        compose.onNodeWithTag("cable-LENGTH").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("cable-result-3").performScrollTo().assertTextContains("0 нс")
        compose.onNodeWithTag("cable-LENGTH").performScrollTo().performTextReplacement("0,25")
        compose.onNodeWithTag("cable-result-0").performScrollTo().assertTextContains("0,3 мм")
        compose.onNodeWithTag("cable-LENGTH").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("cable-result-0").assertDoesNotExist()
        compose.onNodeWithTag("cable-copy").performScrollTo().assertIsNotEnabled()
        selectMode(CableMode.ANGLE_TO_LENGTH)
        compose.onNodeWithTag("cable-ANGLE").performScrollTo().assertTextContains("450")
        compose.onNodeWithTag("cable-result-1").performScrollTo().assertTextContains("450°")
        compose.onNodeWithTag("cable-result-2").performScrollTo().assertTextContains("90°")
    }
    private fun selectMode(mode: CableMode) {
        val chip = compose.onNodeWithTag("cable-mode-${mode.name}")
        chip.performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        chip.assertIsSelected()
    }

    @Test fun coefficientIsolationAndClipboardContext() {
        prepare()
        compose.onNodeWithTag("cable-VELOCITY").performScrollTo().performTextReplacement("0,84")
        compose.onNodeWithTag("nav-SIZING").performClick()
        compose.onNodeWithTag("field-FACTOR").performScrollTo().performTextReplacement("0,93")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.onNodeWithTag("cable-VELOCITY").performScrollTo().assertTextContains("0,84")
        compose.onNodeWithTag("cable-copy").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("145 МГц")); assertTrue(text.contains("VF: 0,84")); assertTrue(text.contains("90°") || text.contains("°: 90"))
            assertTrue(text.contains("плоскостями A и B"))
        }
    }
    @Test fun incompleteCableDraftSurvivesRotationAndRecreation() {
        prepare()
        compose.onNodeWithTag("cable-ANGLE").performScrollTo().performTextReplacement("90,")
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("cable-ANGLE").performScrollTo().assertTextContains("90,")
        compose.onNodeWithTag("cable-copy").performScrollTo().assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun cableRecalculationP95() {
        val model = prepare()
        val samples = mutableListOf<Long>()
        repeat(110) { index -> compose.runOnIdle {
            val start = SystemClock.elapsedRealtimeNanos()
            model.onEvent(CalculatorEvent.Cable(model.state.value.draft.cable.edit(CableField.ANGLE, (index + 1).toString())))
            val result = model.state.value.draft.cable.result()!!
            assertEquals((index + 1).toDouble(), result.fullDegrees.toDouble(), 0.0)
            if (index >= 10) samples += SystemClock.elapsedRealtimeNanos() - start
        } }
        val p95 = samples.sorted()[94]
        Log.i("AntcalcAcceptance", "P95,cable,${android.os.Build.VERSION.SDK_INT},$p95")
        assertTrue("Cable p95 exceeds 100 ms", p95 <= 100000000L)
    }
}
