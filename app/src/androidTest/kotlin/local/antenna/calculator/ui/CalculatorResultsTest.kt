package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.ClipboardManager
import android.content.Context

@RunWith(AndroidJUnit4::class)
class CalculatorResultsTest : CalculatorUiFixture() {
    private fun error(key: FieldKey) = compose.onNodeWithTag("error-${key.name}", useUnmergedTree = true)

    private fun copy(): String {
        compose.onNodeWithTag("copy-result").performScrollTo().performClick()
        compose.onNodeWithText("Расчет скопирован").assertExists()
        var text = ""
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        }
        return text
    }

    @Test fun initialSizingShowsPhysicalAndIdealDimensions() {
        start()
        compose.onNodeWithTag("monopole-result").assertTextContains("491 мм")
        compose.onNodeWithTag("dipole-total").assertTextContains("Полная длина диполя").assertTextContains("982 мм")
        compose.onNodeWithTag("dipole-arm").assertTextContains("Длина одного плеча").assertTextContains("491 мм")
        compose.onNodeWithTag("wavelength").assertTextContains("2068 мм")
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
        compose.onNodeWithTag("monopole-result").assertTextContains("0,49 м")
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

    @Test fun practicalTrimCoversShortenLengthenAndEqualFrequencies() {
        start(); trimming()
        edit(FieldKey.ACTUAL_LENGTH, "500")
        compose.onNodeWithTag("adjustment").assertTextContains("Уменьшение на 32 мм")
        compose.onNodeWithTag("new-length").assertTextContains("468 мм")
        edit(FieldKey.MEASURED, "155"); edit(FieldKey.TARGET, "145")
        compose.onNodeWithTag("adjustment").assertTextContains("Увеличение на 34 мм")
        edit(FieldKey.MEASURED, "145")
        compose.onNodeWithTag("adjustment").assertTextContains("Изменение не требуется")
    }

    @Test fun openingTuningFillsTheLengthAndSizingUpdatesBothProjections() {
        start()
        edit(FieldKey.SIZING_FREQUENCY, "146")
        trimming()
        field(FieldKey.ACTUAL_LENGTH).performScrollTo().assertTextContains("488")
        edit(FieldKey.ACTUAL_LENGTH, "475")
        compose.onNodeWithTag("antenna-SYMMETRIC_DIPOLE").performScrollTo().performClick()
        field(FieldKey.ACTUAL_LENGTH).performScrollTo().assertTextContains("975")
        edit(FieldKey.ACTUAL_LENGTH, "960")
        edit(FieldKey.MEASURED, "145")
        compose.onNodeWithTag("nav-SIZING").performClick()
        compose.onNodeWithTag("sizing-actual-monopole").performScrollTo().assertTextContains("475 мм")
        compose.onNodeWithTag("sizing-actual-dipole").performScrollTo().assertTextContains("960 мм")
        compose.onNodeWithTag("sizing-tuned-monopole").performScrollTo().assertTextContains("472 мм")
        compose.onNodeWithTag("sizing-tuned-dipole").performScrollTo().assertTextContains("953 мм")
        compose.onNodeWithTag("sizing-tuned-arm").performScrollTo().assertTextContains("477 мм")
    }

    @Test fun dipoleLengthEditsRefreshSizingTuningCardWithoutChangingStartingDimensions() {
        start()
        edit(FieldKey.SIZING_FREQUENCY, "162")
        edit(FieldKey.FACTOR, "0,92")
        compose.onNodeWithTag("output-unit-CENTIMETERS").performScrollTo().performClick()
        trimming()
        compose.onNodeWithTag("antenna-SYMMETRIC_DIPOLE").performScrollTo().performClick()
        edit(FieldKey.MEASURED, "145")

        for ((actualCm, projectedTotal, projectedArm) in listOf(
            Triple(100, "89,51 см", "44,75 см"),
            Triple(120, "107,41 см", "53,70 см"),
        )) {
            edit(FieldKey.ACTUAL_LENGTH, (actualCm * 10).toString())
            compose.onNodeWithTag("nav-SIZING").performClick()
            compose.onNodeWithText("Прогноз по данным подстройки").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("sizing-actual-dipole").assertTextContains("$actualCm,00 см")
            compose.onNodeWithTag("sizing-actual-arm").assertTextContains("${actualCm / 2},00 см")
            compose.onNodeWithTag("sizing-tuned-dipole").assertTextContains(projectedTotal)
            compose.onNodeWithTag("sizing-tuned-arm").assertTextContains(projectedArm)
            compose.onNodeWithTag("dipole-total").assertTextContains("85,13 см")
            compose.onNodeWithTag("dipole-arm").assertTextContains("42,56 см")
            trimming()
        }
    }

    @Test fun dipoleShowsSeparateArmChangeAndCompleteClipboard() {
        start(); trimming()
        compose.onNodeWithTag("antenna-SYMMETRIC_DIPOLE").performScrollTo().performClick()
        edit(FieldKey.ACTUAL_LENGTH, "1000")
        compose.onNodeWithTag("adjustment").assertTextContains("Уменьшение на 65 мм")
        compose.onNodeWithTag("arm-adjustment").assertTextContains("Уменьшение на 32 мм")
        compose.onNodeWithTag("new-length").assertTextContains("935 мм")
        compose.onNodeWithTag("new-arm").assertTextContains("468 мм")
        val text = copy()
        listOf("Симметричный диполь", "Полная длина двух плеч: 1000 мм", "Измеренная резонансная частота: 145,000 МГц",
            "Целевая частота: 155,000 МГц", "Оценка полной длины диполя: 935 мм", "Изменение каждого плеча: Уменьшение на 32 мм",
            "Оценка длины каждого плеча: 468 мм", "Приближённый расчет").forEach { assertTrue(text, text.contains(it)) }
    }

    @Test fun unusualAntennaLengthShowsWarningWithTheEstimate() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "1900")
        val warning = "Введённая длина отличается более чем на 25 %"
        compose.onNodeWithText(warning, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("new-length").performScrollTo().assertTextContains("1777 мм")
        val text = copy()
        assertTrue(text, text.contains(warning))
    }

    @Test fun smallNonzeroTrimRetainsDirection() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "500"); edit(FieldKey.TARGET, "145,000001")
        compose.onNodeWithTag("adjustment").assertTextContains("Уменьшение менее чем на 1 мм")
        compose.onNodeWithTag("new-length").assertTextContains("500 мм")
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
        listOf("Частота: 145,000 МГц", "Коэффициент укорочения k: 0,95", "Четвертьволновый штырь: 491 мм",
            "Полная длина диполя: 982 мм", "Длина одного плеча: 491 мм", "Длина волны λ: 2068 мм", "Приближённый расчет")
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

    @Test fun monopoleClipboardContainsActualLengthBothFrequenciesAndNewLength() {
        start(); trimming(); edit(FieldKey.ACTUAL_LENGTH, "500")
        val text = copy()
        listOf("Фактическая длина штыря: 500 мм", "Измеренная резонансная частота: 145,000 МГц", "Целевая частота: 155,000 МГц",
            "Уменьшение на 32 мм", "Оценка новой длины штыря: 468 мм", "Приближённый расчет")
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
        assertTrue(text.contains("Расчет размеров"))
        assertFalse(Regex("оригинал|\\bLEN\\b|\\bTRIM\\b|\\bADD\\b|\\bCUT\\b", RegexOption.IGNORE_CASE).containsMatchIn(text))
        trimming()
        field(FieldKey.ACTUAL_LENGTH).assertExists()
    }
}
