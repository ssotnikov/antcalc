package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class CalculatorHelpTest : CalculatorUiFixture() {
    private fun checkFormulaImages() {
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").performClick()
        listOf("wavelength", "monopole", "dipole", "five-eighths", "trimming", "delta", "arm").forEach { name ->
            compose.onNodeWithTag("formula-$name").performScrollTo().assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
        }
        back()
        field(FieldKey.SIZING_FREQUENCY).assertExists()
    }

    @Test fun browserFailuresDoNotCrashTheCalculator() {
        var failure: RuntimeException = ActivityNotFoundException("Test: no browser")
        var attempts = 0
        val blocked = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { attempts++; throw failure }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides blocked) {
                CalculatorTheme(AppTheme.LIGHT) { ReferencesScreen(AppLanguage.ENGLISH) }
            }
        }
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("source-nist").performScrollTo().performClick()
        compose.runOnIdle { failure = SecurityException("Test: browser launch denied") }
        compose.onNodeWithTag("source-nist").performClick().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun languageSwitchUpdatesAllPagesClipboardAndPreservesRawInputs() {
        start()
        edit(FieldKey.SIZING_FREQUENCY, "433,5")
        settings()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        compose.onNodeWithText("Interface language").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        back()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("433,5")
        compose.onNodeWithTag("monopole-result").performScrollTo().assertTextContains("164.2 mm")
        compose.onNodeWithTag("copy-result").performScrollTo().performClick()
        compose.onNodeWithText("Calculation copied").assertExists()
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text, text.contains("Frequency: 433.5 MHz"))
            assertTrue(text, text.contains("Quarter-wave monopole"))
            assertFalse(text, text.contains("Частота"))
        }
        trimming()
        field(FieldKey.ACTUAL_LENGTH).performScrollTo().assertTextContains("Actual monopole length")
        edit(FieldKey.ACTUAL_LENGTH, "500")
        compose.onNodeWithTag("adjustment").performScrollTo().assertTextContains("Decrease of 32.3 mm")
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").assertTextContains("Help").performClick()
        compose.onNodeWithText("Calculation copied").assertDoesNotExist()
        compose.onNodeWithTag("formula-wavelength").assertContentDescriptionEquals("Wavelength equals the speed of light divided by frequency.")
        compose.onNodeWithTag("source-nist").assertDoesNotExist()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-references").assertTextContains("References").performClick()
        compose.onNodeWithTag("source-nist").performScrollTo().assertTextContains("Speed of light in vacuum")
        back()
        compose.onNodeWithTag("formula-wavelength").assertExists()
        back()
        settings()
        compose.onNodeWithTag("language-RUSSIAN").performScrollTo().performClick()
        compose.onNodeWithText("Язык интерфейса").assertExists()
        back()
        field(FieldKey.ACTUAL_LENGTH).performScrollTo().assertTextContains("500")
    }

    @Test fun linksUseExternalHttpsIntentsFromLocalizedContext() {
        start()
        val requests = mutableListOf<Intent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                requests += Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-references").assertTextContains("Материалы и источники").performClick()
            val sources = listOf("itu" to "www.itu.int", "nist" to "physics.nist.gov", "rsgb" to "rsgb.org",
                "analyzer" to "www.arrl.org", "antenna-geometry" to "www.arrl.org", "nashua" to "www.n1fd.org",
                "reflection" to "www.analog.com", "cable-delay" to "helpfiles.keysight.com",
                "nasa" to "radiojove.gsfc.nasa.gov", "verticals" to "www.arrl.org", "five-eighths" to "practicalantennas.com",
                "rig-tuning" to "www.rigexpert.com", "kadamm" to "www.kadamm.com", "rohde" to "scdn.rohde-schwarz.com",
                "rig-cable" to "www.rigexpert.com", "steer" to "eng.libretexts.org")
            assertEquals(sources.map { it.first }.toSet(), referenceGroups.flatMap { it.materials }.map { it.id }.toSet())
            for ((name, _) in sources) compose.onNodeWithTag("source-$name").performScrollTo().performClick()
            compose.onNodeWithTag("source-author").assertDoesNotExist()
            back(); settings()
            compose.onNodeWithTag("source-author").performScrollTo().performClick()
            compose.onNodeWithTag("report-bug").performScrollTo().assertTextContains("Сообщить об ошибке").performClick()
            compose.runOnIdle {
                val expected = sources + listOf("author" to "ssotnikov.pro", "report-bug" to "github.com")
                assertEquals(expected.size, requests.size)
                requests.zip(expected).forEach { (intent, source) ->
                    assertEquals("https", intent.data!!.scheme)
                    assertEquals(source.second, intent.data!!.host)
                }
                assertEquals("https://github.com/ssotnikov/antcalc/issues/new", requests.last().dataString)
            }
            back()
            val technicalLinks = compose.onAllNodes(SemanticsMatcher("technical reference card") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("source-") == true
            })
            technicalLinks.assertCountEquals(0)
            trimming()
            technicalLinks.assertCountEquals(0)
            compose.onNodeWithTag("nav-TOOLS").performClick()
            technicalLinks.assertCountEquals(0)
            compose.onNodeWithTag("open-matching").performScrollTo().performClick()
            technicalLinks.assertCountEquals(0)
            back()
            compose.onNodeWithTag("open-cable").performScrollTo().performClick()
            technicalLinks.assertCountEquals(0)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun sizingAndReferenceOrderingFollowTheInterfaceLanguageAtLargeFont() {
        start(fontScale = 2f)
        compose.onNodeWithTag("output-unit-CENTIMETERS").performScrollTo().performClick()

        fun checkSizes(separator: String, unit: String) {
            val expected = listOf("wavelength" to "206.75", "monopole-result" to "49.10", "dipole-arm" to "49.10",
                "dipole-total" to "98.21", "ideal-five-eighths" to "129.22")
            for ((tag, value) in expected) {
                compose.onNodeWithTag(tag).performScrollTo().assertTextContains("${value.replace(".", separator)} $unit")
            }
        }

        fun checkOrder(first: String, second: String, languageLabel: String) {
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-references").performClick()
            val firstCard = compose.onNodeWithTag("source-$first")
            firstCard.performScrollTo().assertIsDisplayed().assertTextContains(languageLabel, substring = true)
            val secondCard = compose.onNodeWithTag("source-$second")
            assertTrue(firstCard.getUnclippedBoundsInRoot().top < secondCard.getUnclippedBoundsInRoot().top)
            back()
        }

        checkSizes(",", "см")
        checkOrder("itu", "nist", "Русский")
        checkOrder("radiolab", "rfi-dielectrics", "Русский")
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("145")
        settings()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        back()
        checkSizes(".", "cm")
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-references").assertTextContains("References").performClick()
        compose.onNodeWithTag("source-nist").performScrollTo().assertIsDisplayed()
            .assertTextContains("English", substring = true)
        for (id in listOf("itu", "rig-tuning", "kadamm", "rig-cable", "radiolab")) {
            compose.onNodeWithTag("source-$id").assertDoesNotExist()
        }
        compose.onNodeWithTag("source-rfi-dielectrics").performScrollTo().assertIsDisplayed()
            .assertTextContains("Coaxial Cable Dielectrics", substring = true)
        compose.onNodeWithTag("source-cable-delay").performScrollTo().assertIsDisplayed()
    }

    @Test fun englishFallbackKeepsLeftToRightLayoutOnRtlSystem() {
        val draft = CalculatorDraft.defaults().copy(settings = CalculatorSettings(language = AppLanguage.ENGLISH))
        start(draft, layoutDirection = LayoutDirection.Rtl)
        compose.onNodeWithTag("nav-SIZING").assertTextContains("Antenna dimensions")
        val sizing = compose.onNodeWithTag("nav-SIZING").getUnclippedBoundsInRoot()
        val tuning = compose.onNodeWithTag("nav-TRIMMING").getUnclippedBoundsInRoot()
        assertTrue(sizing.left < tuning.left)
    }

    @Test fun largeTextKeepsAllPagesReachableInBothLanguagesAndThemes() {
        start(fontScale = 2f)
        for (language in AppLanguage.entries) for (theme in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            compose.runOnIdle { model.onEvent(CalculatorEvent.Settings(model.state.value.draft.settings.copy(language = language, theme = theme, section = Section.SIZING))) }
            compose.onNodeWithTag("dipole-total").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("copy-result").performScrollTo().assertIsDisplayed()
            trimming()
            field(FieldKey.ACTUAL_LENGTH).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("output-unit-METERS").performScrollTo().assertIsDisplayed()
            settings()
            compose.onNodeWithTag("theme-DARK").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("© 2026 Sergey Sotnikov").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("source-author").performScrollTo().assertIsDisplayed()
            back()
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-about").performClick()
            compose.onNodeWithTag("formula-wavelength").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("source-author").assertDoesNotExist()
            compose.onNodeWithText("© 2026 Sergey Sotnikov").assertDoesNotExist()
            back()
        }
    }

    @Test fun formulaImagesHaveDescriptionsInLightTheme() {
        start()
        settings()
        compose.onNodeWithTag("theme-LIGHT").performClick()
        back()
        checkFormulaImages()
    }

    @Test fun formulaImagesRemainReadableInDarkTheme() {
        start()
        settings()
        compose.onNodeWithTag("theme-DARK").performClick()
        back()
        checkFormulaImages()
    }
}
