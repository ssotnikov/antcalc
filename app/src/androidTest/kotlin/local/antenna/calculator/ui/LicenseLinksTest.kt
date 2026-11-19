package local.antenna.calculator.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorDraft
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LicenseLinksTest : CalculatorUiFixture() {
    @Test fun bundledLicenseRendersLinksAndOpensTheMitPage() = exercise(AppLanguage.RUSSIAN, AppTheme.DARK)
    @Test fun licenseLinksInEnglishAndLightTheme() = exercise(AppLanguage.ENGLISH, AppTheme.LIGHT)

    private fun exercise(language: AppLanguage, theme: AppTheme) {
        start(CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(language = language, theme = theme)) })
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-licenses").performClick()
        val body = compose.onNodeWithText("Copyright ©", substring = true, useUnmergedTree = true)
        val text = body.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals("Both the MIT badge target and the plain MIT URL must be links", 2, links.size)
        assertFalse(text.text.contains("[!["))
        assertFalse(text.text.contains("**Copyright"))
        assertTrue(text.text.contains("Permission is hereby granted, free of charge"))
        val first = links.first()
        assertEquals("https://opensource.org/licenses/MIT", (first.item as LinkAnnotation.Clickable).tag)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requests = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                requests += Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            val layouts = mutableListOf<TextLayoutResult>()
            body.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            body.performTouchInput { click(layouts.single().getBoundingBox(first.start).center) }
            compose.runOnIdle { assertEquals(listOf("https://opensource.org/licenses/MIT"), requests.map { it.dataString }) }
        } finally { instrumentation.removeMonitor(monitor) }
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "license-link-shots").apply { mkdirs() }
        File(directory, "${language.name.lowercase()}-${theme.name.lowercase()}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
