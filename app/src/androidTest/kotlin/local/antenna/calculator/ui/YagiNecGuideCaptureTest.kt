package local.antenna.calculator.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorDraft
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiNecGuideCaptureTest : CalculatorUiFixture() {
    @Test fun russianDark() = capture(AppLanguage.RUSSIAN, AppTheme.DARK)
    @Test fun russianLight() = capture(AppLanguage.RUSSIAN, AppTheme.LIGHT)
    @Test fun englishDark() = capture(AppLanguage.ENGLISH, AppTheme.DARK)
    @Test fun englishLight() = capture(AppLanguage.ENGLISH, AppTheme.LIGHT)

    private fun capture(language: AppLanguage, theme: AppTheme) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureNecGuide") == "1")
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = language, theme = theme)))
        compose.onNodeWithTag("nav-TOOLS").performClick()
        click("open-additional-tools")
        click("open-yagi-scaling")
        click("ys-example")
        click("ys-mode-BOTH")
        edit("ys-source", "144.1")
        edit("ys-target", "50.1")
        edit("ys-diameter", "10")
        edit("ys-length-2", "900")
        click("ys-element-diameters-2")
        edit("ys-target-d-2", "12")
        click("opennec-expand")
        edit("opennec-limit", "1.01")
        closeSoftKeyboard()
        click("opennec-run")
        val suffix = "${language.name.lowercase()}-${theme.name.lowercase()}"
        save("opennec-result", "nec-comparison-$suffix.png", "opennec-result")
        click("opennec-pattern-details")
        save("opennec-result", "nec-smith-$suffix.png", "opennec-smith-plot")
        click("opennec-optimize")
        save("opennec-optimization", "nec-optimization-$suffix.png", "opennec-optimization")
    }

    private fun save(tag: String, fileName: String, plotTag: String = "opennec-swr-plot") {
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(plotTag).onLast().performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("nec-guide"))
        val screenshot = instrumentation.uiAutomation.executeShellCommand("screencap -p")
        ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { image ->
            folder.resolve(fileName).outputStream().use { check(image.copyTo(it) > 0) }
        }
    }

    private fun edit(tag: String, value: String) =
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
}
