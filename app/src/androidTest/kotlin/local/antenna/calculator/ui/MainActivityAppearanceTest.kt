package local.antenna.calculator.ui

import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun splashArtworkHasRoundedTransparentCornersAndFitsSystemMask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = requireNotNull(context.getDrawable(R.drawable.ic_splash))
        val bitmap = Bitmap.createBitmap(324, 324, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, bitmap.width, bitmap.height)
            icon.draw(Canvas(bitmap))
            assertEquals("Outer corner must be transparent", 0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals("Rounded square must not fill its bounding corner", 0, Color.alpha(bitmap.getPixel(81, 81)))
            assertEquals("Top center must show the navy background", Color.rgb(16, 40, 63), bitmap.getPixel(162, 90))
            var visiblePixels = 0
            val center = bitmap.width / 2f
            val maskRadius = bitmap.width / 3f
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                visiblePixels++
                val dx = x + 0.5f - center
                val dy = y + 0.5f - center
                assertTrue("Artwork would be cropped by the system mask", dx * dx + dy * dy <= maskRadius * maskRadius)
            }
            assertTrue("Icon must contain visible artwork", visiblePixels > 1000)
        } finally {
            bitmap.recycle()
        }
    }

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
