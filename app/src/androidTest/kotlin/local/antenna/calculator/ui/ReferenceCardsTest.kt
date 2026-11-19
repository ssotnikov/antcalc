package local.antenna.calculator.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorDraft
import org.junit.Assert.*
import org.junit.Test

class ReferenceCardsTest : CalculatorUiFixture() {
    @Test fun russianDarkCardsPreserveDescriptionsAndSourceActions() = exercise(AppLanguage.RUSSIAN, AppTheme.DARK)
    @Test fun englishLightCardsPreserveDescriptionsAndSourceActions() = exercise(AppLanguage.ENGLISH, AppTheme.LIGHT)

    private fun exercise(language: AppLanguage, theme: AppTheme) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resources = instrumentation.targetContext.createConfigurationContext(Configuration().apply {
            setLocale(Locale(if (language == AppLanguage.RUSSIAN) "ru" else "en"))
        }).resources
        val extraUrls = CantennaSource.entries.map { it.url } + hb9cvReferenceSources.map { it.url } +
            MoxonSource.entries.map { it.url } + listOf(SLIM_JIM_OK1IKE_URL, OK1IKE_HB9ACC_URL, SLIM_JIM_SV1JRF_URL)
        assertEquals(extraUrls.toSet(), additionalReferenceSummaries.keys)
        assertEquals(34, extraUrls.size)
        assertEquals(69, referenceGroups.sumOf { it.materials.size })
        for (url in extraUrls) {
            assertNotNull(url, validatedSourceAddress(url))
            assertTrue(resources.getString(additionalReferenceSummaries.getValue(url).description).isNotBlank())
        }
        for (source in referenceGroups.flatMap { it.materials }) {
            assertTrue(source.id, resources.getString(source.description).isNotBlank())
        }
        start(CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(language = language, theme = theme)) })
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-references").performClick()
        compose.onNodeWithTag("source-cantenna-changpuak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(resources.getString(additionalReferenceSummaries.getValue(CantennaSource.CHANGPUAK.url).description)).assertExists()
        capture("${language.name.lowercase()}-cantenna")
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
            compose.onNodeWithTag("source-cantenna-changpuak").performClick()
            compose.onNodeWithText(resources.getString(local.antenna.calculator.R.string.cantenna_source_terms, CantennaSource.CHANGPUAK.author))
                .performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(listOf(CantennaSource.CHANGPUAK.url, CantennaSource.CHANGPUAK.termsUrl), requests.map { it.dataString })
            }
        } finally { instrumentation.removeMonitor(monitor) }
        compose.onNodeWithTag("source-hb9cv-lvt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(resources.getString(additionalReferenceSummaries.getValue(Hb9cvSource.LVT.url).description)).assertExists()
        capture("${language.name.lowercase()}-hb9cv")
        compose.onNodeWithTag("source-moxon-method").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(resources.getString(additionalReferenceSummaries.getValue(MoxonSource.METHOD.url).description)).assertExists()
        capture("${language.name.lowercase()}-moxon")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap)
        val directory = File(compose.activity.getExternalFilesDir(null), "reference-card-shots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
