package local.antenna.calculator.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import local.antenna.calculator.data.*
import local.antenna.calculator.state.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: CalculatorViewModel
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun start(draft: CalculatorDraft = CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(language = AppLanguage.RUSSIAN)) }, fontScale: Float = 1f, layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        compose.runOnUiThread {
            model = CalculatorViewModel(object : SettingsRepository {
                var value = PersistedCalculatorState.defaults().advance(draft)
                override suspend fun read() = value
                override suspend fun write(state: PersistedCalculatorState) { value = state }
                override suspend fun reset(): PersistedCalculatorState { value = PersistedCalculatorState.defaults(); return value }
            }, SavedStateHandle(mapOf(DraftCodec.KEY to DraftCodec.encode(draft))), persistenceScope)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides layoutDirection) {
                CalculatorApp(state, model::onEvent) { model.state.value }
            }
        }
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("field-${if (draft.settings.section == Section.SIZING) "SIZING_FREQUENCY" else "ACTUAL_LENGTH"}").assertExists()
    }

    @After fun stop() { if (::model.isInitialized) model.viewModelScope.cancel(); persistenceScope.cancel() }

    private fun swipeCalculation(right: Boolean) {
        compose.onNodeWithTag("page-content").performTouchInput {
            val start = Offset(width * if (right) 0.15f else 0.85f, height * 0.85f)
            val end = Offset(width * if (right) 0.85f else 0.15f, height * 0.85f)
            swipe(start, end, 350)
        }
        compose.waitForIdle()
    }

    @Test fun swipesKeepIndependentIncompleteInputsAndUpdateBottomNavigation() {
        start()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").performTextReplacement("145,")
        swipeCalculation(right = false)
        compose.onNodeWithTag("nav-TRIMMING").assertIsSelected()
        compose.onNodeWithTag("field-ACTUAL_LENGTH").performTextReplacement("500,")
        swipeCalculation(right = true)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertTextContains("145,")
        swipeCalculation(right = false)
        compose.onNodeWithTag("field-ACTUAL_LENGTH").assertTextContains("500,")
    }

    @Test fun swipeBoundariesShortDragsAndCancellationDoNotNavigate() {
        start()
        swipeCalculation(right = true)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("page-content").performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.85f), Offset(width * 0.57f, height * 0.85f), 350)
        }
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("page-content").performTouchInput {
            down(Offset(width * 0.85f, height * 0.85f))
            moveTo(Offset(width * 0.15f, height * 0.85f), 350)
            cancel()
        }
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        swipeCalculation(right = false)
        swipeCalculation(right = false)
        compose.onNodeWithTag("nav-TRIMMING").assertIsSelected()
    }

    @Test fun verticalSwipesStillScrollWithoutSwitchingCalculations() {
        start(fontScale = 2f)
        val node = compose.onNodeWithTag("calculator-scroll")
        val before = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        node.performTouchInput { swipeUp() }
        val after = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Vertical content should scroll", after > before)
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
    }

    @Test fun swipesOnMenuPagesLeavePageAndCalculationUnchanged() {
        start()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        swipeCalculation(right = false)
        compose.onNodeWithTag("language-RUSSIAN").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").assertTextContains("Справка").performClick()
        swipeCalculation(right = false)
        compose.onNodeWithTag("back").assertExists()
        compose.onNodeWithTag("nav-SIZING").assertDoesNotExist()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav-SIZING").assertIsSelected()
    }

    @Test fun loadingUsesStartupBackgroundBeforeSettingsAreRestored() {
        val loading = CalculatorUiState(loading = true)
        compose.setContent { CalculatorApp(loading, {}, { loading }) }
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals("Loading must not draw a light frame before the saved theme is known",
            Color(0xFF101D26), pixels[pixels.width / 4, pixels.height / 2])
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

    private fun field(key: FieldKey) = compose.onNodeWithTag("field-${key.name}")
    private fun error(key: FieldKey) = compose.onNodeWithTag("error-${key.name}", useUnmergedTree = true)
    private fun edit(key: FieldKey, text: String) { field(key).performScrollTo().performClick().performTextReplacement(text) }
    private fun trimming() { compose.onNodeWithTag("nav-TRIMMING").performClick() }
    private fun settings() { compose.onNodeWithTag("menu").performClick(); compose.onNodeWithTag("open-settings").performClick() }
    private fun back() { compose.onNodeWithTag("back").performClick() }
    private fun copy(): String {
        compose.onNodeWithTag("copy-result").performScrollTo().performClick()
        compose.onNodeWithText("Расчёт скопирован").assertExists()
        var text = ""
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        }
        return text
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
        compose.onNodeWithTag("help-references").assertDoesNotExist()
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
            val footer = compose.onNodeWithTag("reference-link")
            footer.assertDoesNotExist()
            trimming()
            footer.assertDoesNotExist()
            compose.onNodeWithTag("nav-TOOLS").performClick()
            footer.assertDoesNotExist()
            compose.onNodeWithTag("open-matching").performScrollTo().performClick()
            footer.assertDoesNotExist()
            back()
            compose.onNodeWithTag("open-cable").performScrollTo().performClick()
            footer.assertDoesNotExist()
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
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("145")
        settings()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        back()
        checkSizes(".", "cm")
        checkOrder("nist", "itu", "English")
    }

    @Test fun englishFallbackKeepsLeftToRightLayoutOnRtlSystem() {
        val draft = CalculatorDraft.defaults().copy(settings = CalculatorSettings(language = AppLanguage.ENGLISH))
        start(draft, layoutDirection = LayoutDirection.Rtl)
        compose.onNodeWithTag("nav-SIZING").assertTextContains("Antenna dimensions")
        val sizing = compose.onNodeWithTag("nav-SIZING").getUnclippedBoundsInRoot()
        val tuning = compose.onNodeWithTag("nav-TRIMMING").getUnclippedBoundsInRoot()
        assertTrue(sizing.left < tuning.left)
    }

    @Test fun initialSizingShowsPhysicalAndIdealDimensions() {
        start()
        compose.onNodeWithTag("monopole-result").assertTextContains("491,0 мм")
        compose.onNodeWithTag("dipole-total").assertTextContains("Полная длина диполя").assertTextContains("982,1 мм")
        compose.onNodeWithTag("dipole-arm").assertTextContains("Длина одного плеча").assertTextContains("491,0 мм")
        compose.onNodeWithTag("wavelength").assertTextContains("2067,5 мм")
        compose.onNodeWithContentDescription("Lₐ — длина каждого из двух равных плеч. Полная длина Lᵈ равна 2Lₐ и исключает зазор питания.").assertExists()
    }

    @Test fun frequencyAndLengthUnitsPreservePhysicalSize() {
        start()
        compose.onNodeWithTag("unit-SIZING_FREQUENCY-KILOHERTZ").performScrollTo().performClick()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("145000")
        compose.onNodeWithTag("unit-SIZING_FREQUENCY-GIGAHERTZ").performClick()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("0,145")
        compose.onNodeWithTag("output-unit-CENTIMETERS").performScrollTo().performClick()
        compose.onNodeWithTag("monopole-result").assertTextContains("49,10 см")
        compose.onNodeWithTag("output-unit-METERS").performClick()
        compose.onNodeWithTag("monopole-result").assertTextContains("0,4910 м")
    }

    @Test fun factorErrorKeepsIdealAndFrequencyErrorHidesAllResults() {
        start()
        edit(FieldKey.FACTOR, "1,01")
        error(FieldKey.FACTOR).assertExists()
        compose.onNodeWithTag("monopole-result").assertDoesNotExist()
        compose.onNodeWithTag("wavelength").assertExists()
        compose.onNodeWithTag("copy-result").performScrollTo().assertIsNotEnabled()
        edit(FieldKey.SIZING_FREQUENCY, "0")
        compose.onNodeWithTag("wavelength").assertDoesNotExist()
        error(FieldKey.SIZING_FREQUENCY).assertExists()
    }

    @Test fun navigationWithKeyboardPreservesIndependentInputsAndBack() {
        start()
        edit(FieldKey.SIZING_FREQUENCY, "433")
        compose.waitUntil(timeoutMillis = 5000) {
            var visible = false
            compose.runOnUiThread { visible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            visible
        }
        compose.onNodeWithTag("nav-TRIMMING").assertIsDisplayed()
        trimming()
        edit(FieldKey.ACTUAL_LENGTH, "500")
        compose.onNodeWithTag("nav-SIZING").performClick()
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("433")
        settings()
        compose.onNodeWithTag("theme-DARK").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        field(FieldKey.SIZING_FREQUENCY).assertTextContains("433")
        trimming()
        field(FieldKey.ACTUAL_LENGTH).assertTextContains("500")
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-about").performClick()
        back()
        field(FieldKey.ACTUAL_LENGTH).assertTextContains("500")
    }

    @Test fun practicalTrimCoversShortenLengthenAndEqualFrequencies() {
        start(); trimming()
        edit(FieldKey.ACTUAL_LENGTH, "500")
        compose.onNodeWithTag("adjustment").assertTextContains("Уменьшение на 32,3 мм")
        compose.onNodeWithTag("new-length").assertTextContains("467,7 мм")
        edit(FieldKey.MEASURED, "155"); edit(FieldKey.TARGET, "145")
        compose.onNodeWithTag("adjustment").assertTextContains("Увеличение на 34,5 мм")
        edit(FieldKey.MEASURED, "145")
        compose.onNodeWithTag("adjustment").assertTextContains("Изменение не требуется")
    }

    @Test fun dipoleShowsSeparateArmChangeAndCompleteClipboard() {
        start(); trimming()
        compose.onNodeWithTag("antenna-SYMMETRIC_DIPOLE").performScrollTo().performClick()
        edit(FieldKey.ACTUAL_LENGTH, "1000")
        compose.onNodeWithTag("adjustment").assertTextContains("Уменьшение на 64,5 мм")
        compose.onNodeWithTag("arm-adjustment").assertTextContains("Уменьшение на 32,3 мм")
        compose.onNodeWithTag("new-length").assertTextContains("935,5 мм")
        compose.onNodeWithTag("new-arm").assertTextContains("467,7 мм")
        val text = copy()
        listOf("Симметричный диполь", "Полная длина двух плеч: 1000 мм", "Измеренная резонансная частота: 145 МГц",
            "Целевая частота: 155 МГц", "Оценка полной длины диполя: 935,5 мм", "Изменение каждого плеча: Уменьшение на 32,3 мм",
            "Оценка длины каждого плеча: 467,7 мм", "Приближённый расчёт").forEach { assertTrue(text, text.contains(it)) }
    }

    @Test fun unusualAntennaLengthShowsWarningWithTheEstimate() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "1900")
        val warning = "Введённая длина отличается более чем на 25 %"
        compose.onNodeWithText(warning, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("new-length").performScrollTo().assertTextContains("1777,4 мм")
        val text = copy()
        assertTrue(text, text.contains(warning))
    }

    @Test fun smallNonzeroTrimRetainsDirection() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "500"); edit(FieldKey.TARGET, "145,000001")
        compose.onNodeWithTag("adjustment").assertTextContains("Уменьшение менее чем на 0,1 мм")
        compose.onNodeWithTag("new-length").assertTextContains("500,0 мм")
    }

    @Test fun deletingTargetImmediatelyHidesCommandAndDisablesCopyWithDelayedError() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "500"); edit(FieldKey.TARGET, "")
        compose.onNodeWithTag("adjustment").assertDoesNotExist()
        error(FieldKey.TARGET).assertDoesNotExist()
        field(FieldKey.MEASURED).performScrollTo().performClick()
        error(FieldKey.TARGET).assertExists()
        compose.onNodeWithTag("copy-result").performScrollTo().assertIsNotEnabled()
    }

    @Test fun incompleteFrequencyDoesNotReusePreviousResult() {
        start(); edit(FieldKey.SIZING_FREQUENCY, "145,")
        compose.onNodeWithTag("monopole-result").assertDoesNotExist()
        error(FieldKey.SIZING_FREQUENCY).assertDoesNotExist()
        field(FieldKey.FACTOR).performScrollTo().performClick()
        error(FieldKey.SIZING_FREQUENCY).assertTextContains("Завершите ввод числа")
    }

    @Test fun mainSizingClipboardContainsContextPhysicalAndIdealValues() {
        start()
        val text = copy()
        listOf("Частота: 145 МГц", "Коэффициент укорочения k: 0,95", "Четвертьволновый штырь: 491,0 мм",
            "Полная длина диполя: 982,1 мм", "Длина одного плеча: 491,0 мм", "Длина волны λ: 2067,5 мм", "Приближённый расчёт")
            .forEach { assertTrue(text, text.contains(it)) }
    }

    @Test fun doubledFontKeepsLabelsResultsAndCopyReachable() {
        start(fontScale = 2f)
        compose.onNodeWithTag("dipole-total").performScrollTo().assertIsDisplayed().assertTextContains("Полная длина диполя")
        compose.onNodeWithTag("dipole-arm").performScrollTo().assertIsDisplayed().assertTextContains("Длина одного плеча")
        compose.onNodeWithTag("copy-result").performScrollTo().assertIsDisplayed().assertIsEnabled()
        trimming(); field(FieldKey.ACTUAL_LENGTH).performScrollTo().assertIsDisplayed()
    }

    @Test fun choicesHavePurposeAndErrorsExposeLocalizedMessage() {
        start()
        compose.onNodeWithTag("output-unit-MILLIMETERS").performScrollTo().assertContentDescriptionEquals("Единица результата: мм")
            .assertHeightIsAtLeast(48.dp)
        edit(FieldKey.SIZING_FREQUENCY, "0")
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Error, "Значение должно быть больше нуля"))
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

    @Test fun monopoleClipboardContainsActualLengthBothFrequenciesAndNewLength() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "500")
        val text = copy()
        listOf("Фактическая длина штыря: 500 мм", "Измеренная резонансная частота: 145 МГц", "Целевая частота: 155 МГц",
            "Уменьшение на 32,3 мм", "Оценка новой длины штыря: 467,7 мм", "Приближённый расчёт")
            .forEach { assertTrue(text, text.contains(it)) }
    }

    @Test fun removedModeIsAbsentFromSettingsAndClipboard() {
        val saved = DraftCodec.encode(CalculatorDraft.defaults().copy(settings = CalculatorSettings(language = AppLanguage.RUSSIAN)))
            .apply { this[42] = "true" } // Frozen v1 slot: retired mode flag.
        val draft = requireNotNull(DraftCodec.decode(saved))
        start(draft)
        field(FieldKey.SIZING_FREQUENCY).assertExists()
        compose.onNodeWithTag("field-LEN_FREQUENCY").assertDoesNotExist()
        settings()
        compose.onNodeWithTag("compatibility-switch").assertDoesNotExist()
        back()
        val text = copy()
        assertTrue(text.contains("Расчёт размеров"))
        assertFalse(Regex("оригинал|\\bLEN\\b|\\bTRIM\\b|\\bADD\\b|\\bCUT\\b", RegexOption.IGNORE_CASE).containsMatchIn(text))
        trimming()
        field(FieldKey.ACTUAL_LENGTH).assertExists()
    }

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
    @Test fun cableEnglishDarkLargeTextKeepsActionsReachable() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.ENGLISH, theme = AppTheme.DARK)), fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.onNodeWithTag("cable-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("cable-details").performScrollTo().performClick()
        compose.onNodeWithText("One wavelength").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Phase velocity equals cable velocity factor times the speed of light in vacuum.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-matching").performScrollTo().assertExists()
    }

    @Test fun cableRussianLightLargeTextKeepsInputsReachable() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.RUSSIAN, theme = AppTheme.LIGHT)), fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-cable").performScrollTo().performClick()
        compose.onNodeWithText("Длина кабеля").assertIsDisplayed()
        compose.onNodeWithTag("cable-VELOCITY").performScrollTo().assertTextContains("0,66")
        compose.onNodeWithTag("cable-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("cable-diagram").performScrollTo().assertIsDisplayed()
    }

    @Test fun matchingToolsKeepCalculatorDraftAndRejectStaleResults() {
        start()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").performTextReplacement("146,")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-matching").performClick()
        compose.onNodeWithTag("matching-SWR").assertTextContains("2")
        compose.onNodeWithTag("matching-SWR").performTextReplacement("0")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("matching-SWR").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("nav-SIZING").performClick()
        compose.onNodeWithTag("field-SIZING_FREQUENCY").assertTextContains("146,")
    }

    @Test fun matchingImpedanceAndMagnitudeHaveIndependentInputs() {
        start()
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-matching").performClick()
        compose.onNodeWithTag("matching-SWR").performTextReplacement("3,")
        compose.onNodeWithTag("matching-mode-IMPEDANCE").performClick()
        compose.onNodeWithTag("matching-REACTANCE").performTextReplacement("-50")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("matching-mode-SWR").performScrollTo().performClick()
        compose.onNodeWithTag("matching-SWR").assertTextContains("3,")
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("matching-mode-GAMMA").performScrollTo().performClick()
        compose.onNodeWithTag("matching-GAMMA").performTextReplacement("1")
        compose.onNodeWithText("∞ : 1").assertExists()
    }

    @Test fun matchingLargeFontEnglishAndDetailsRemainReachable() {
        val draft = CalculatorDraft.defaults()
        start(draft.copy(settings = draft.settings.copy(language = AppLanguage.ENGLISH)), fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-matching").performClick()
        compose.onNodeWithTag("matching-copy").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("matching-details").performScrollTo().performClick()
        compose.onNodeWithText("Mismatch loss (ML)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Power balance at an SWR of 2:1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-matching").assertExists()
    }

}
