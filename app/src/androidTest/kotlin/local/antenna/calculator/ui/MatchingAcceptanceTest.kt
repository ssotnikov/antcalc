package local.antenna.calculator.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import local.antenna.calculator.core.matching.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchingAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun prepare(): CalculatorViewModel {
        lateinit var model: CalculatorViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[CalculatorViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.loading && compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            model.onEvent(CalculatorEvent.Settings(model.state.value.draft.settings.copy(section = Section.TOOLS)))
            model.onEvent(CalculatorEvent.Matching(MatchingDraft()))
        }
        compose.onNodeWithTag("open-matching").performClick()
        return model
    }
    @Test fun incompleteMatchingDraftSurvivesRotationAndRecreation() {
        prepare()
        compose.onNodeWithTag("matching-SWR").performTextReplacement("3,")
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("matching-SWR").assertTextContains("3,")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("matching-SWR").assertTextContains("3,")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun matchingRecalculationP95() {
        val model = prepare()
        val samples = mutableListOf<Long>()
        repeat(110) { index -> compose.runOnIdle {
            val start = SystemClock.elapsedRealtimeNanos()
            model.onEvent(CalculatorEvent.Matching(model.state.value.draft.matching.edit(MatchingField.SWR, (index + 1).toString())))
            val actual = model.state.value.draft.matching.result()!!
            assertEquals(index.toDouble() / (index + 2), actual.magnitude, 1e-12)
            if (index >= 10) samples += SystemClock.elapsedRealtimeNanos() - start
        } }
        val p95 = samples.sorted()[94]
        Log.i("AntcalcAcceptance", "P95,matching,${android.os.Build.VERSION.SDK_INT},$p95")
        assertTrue("Matching p95 exceeds 100 ms", p95 <= 100000000L)
    }
}
