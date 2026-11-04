package local.antenna.calculator.ui

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun darkWindowSurvivesReturnAndActivityRecreation() {
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("nav-SIZING").performClick()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        compose.onNodeWithTag("back").performClick()
        assertDarkWindow()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertDarkWindow()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertExists()
        assertDarkWindow()
    }

    private fun assertDarkWindow() {
        compose.runOnIdle {
            val background = compose.activity.window.decorView.background
            assertTrue("Native window must have a solid background", background is ColorDrawable)
            assertEquals(0xFF101D26.toInt(), (background as ColorDrawable).color)
        }
    }
}
