package local.antenna.calculator.state

import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.sizing.*
import local.antenna.calculator.core.trimming.*
import local.antenna.calculator.core.units.*
import java.math.BigDecimal
import java.util.Locale

enum class Section { SIZING, TRIMMING, TOOLS }
enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String) {
    RUSSIAN("ru"), ENGLISH("en");

    companion object {
        fun forSystemLanguage(language: String): AppLanguage =
            if (language.equals("ru", ignoreCase = true)) RUSSIAN else ENGLISH
        fun initial(): AppLanguage = forSystemLanguage(Locale.getDefault().language)
    }
}
enum class FieldKey { SIZING_FREQUENCY, FACTOR, ACTUAL_LENGTH, MEASURED, TARGET }

/** validationUnit retains an invalid field's interpretation across unit changes and restoration. */
data class NumericDraft(
    val text: String,
    val unit: String,
    val validationUnit: String = unit,
    val checked: Boolean = false,
    val focused: Boolean = false,
) {
    fun frequency(): FieldState<Frequency> = QuantityInput.frequency(text, FrequencyUnit.valueOf(validationUnit))
    fun length(): FieldState<PhysicalLength> = QuantityInput.physicalLength(text, LengthUnit.valueOf(validationUnit))
    fun factor(): FieldState<ShorteningFactor> = QuantityInput.shorteningFactor(text)
    fun state(key: FieldKey): FieldState<*> = when (key) {
        FieldKey.FACTOR -> factor()
        FieldKey.ACTUAL_LENGTH -> length()
        else -> frequency()
    }
    fun hasVisibleError(key: FieldKey): Boolean = when (state(key)) {
        is FieldState.Valid -> false
        is FieldState.Invalid -> true
        else -> checked
    }
    fun edit(value: String) = copy(text = value.boundedNumericText(), validationUnit = unit)
    fun changeUnit(key: FieldKey, next: String): NumericDraft {
        require(key != FieldKey.FACTOR)
        if (key == FieldKey.ACTUAL_LENGTH) LengthUnit.valueOf(next) else FrequencyUnit.valueOf(next)
        val value = (state(key) as? FieldState.Valid)?.value
        val converted = when (value) {
            is Frequency -> value.inUnit(FrequencyUnit.valueOf(next))
            is PhysicalLength -> value.inUnit(LengthUnit.valueOf(next))
            else -> null
        }
        return copy(text = converted?.inputText() ?: text, unit = next,
            validationUnit = if (converted != null) next else validationUnit)
    }
}

// One overflow character keeps oversized input invalid instead of calculating a truncated number.
internal fun String.boundedNumericText(): String = take(PositiveDecimalParser.MAX_CHARACTERS + 1)
internal fun BigDecimal.inputText(): String = stripTrailingZeros().toPlainString().replace('.', ',')
internal fun <T> FieldState<T>.validValue(): T? = (this as? FieldState.Valid<T>)?.value

data class CalculatorSettings(
    val section: Section = Section.SIZING,
    val theme: AppTheme = AppTheme.SYSTEM,
    val antennaType: AntennaType = AntennaType.MONOPOLE,
    val sizingLengthUnit: LengthUnit = LengthUnit.MILLIMETERS,
    val trimmingLengthUnit: LengthUnit = LengthUnit.MILLIMETERS,
    val language: AppLanguage = AppLanguage.initial(),
)

data class CalculatorDraft(val fields: Map<FieldKey, NumericDraft>, val settings: CalculatorSettings = CalculatorSettings(), val matching: MatchingDraft = MatchingDraft()) {
    operator fun get(key: FieldKey): NumericDraft = fields.getValue(key)
    fun update(key: FieldKey, field: NumericDraft) = copy(fields = fields + (key to field))
    companion object {
        fun defaults() = CalculatorDraft(FieldKey.entries.associateWith { key ->
            when (key) {
                FieldKey.FACTOR -> NumericDraft("0,95", "FACTOR")
                FieldKey.ACTUAL_LENGTH -> NumericDraft("", LengthUnit.MILLIMETERS.name)
                FieldKey.TARGET -> NumericDraft("155", FrequencyUnit.MEGAHERTZ.name)
                else -> NumericDraft("145", FrequencyUnit.MEGAHERTZ.name)
            }
        })
    }
}

data class SizingUiState(val frequency: NumericDraft, val factor: NumericDraft, val ideal: IdealSizingResult?, val result: SizingResult?) {
    val canCopy: Boolean get() = result != null
}
data class TrimmingUiState(val actualLength: NumericDraft, val measured: NumericDraft, val target: NumericDraft, val result: TrimmingResult?) {
    val canCopy: Boolean get() = result != null
}
enum class StorageIssue { READ_FAILED, WRITE_FAILED, CORRUPT, UNSUPPORTED_SCHEMA }

data class CalculatorUiState(
    val draft: CalculatorDraft = CalculatorDraft.defaults(),
    val loading: Boolean = true,
    val storageIssue: StorageIssue? = null,
    val saving: Boolean = false,
) {
    val editingEnabled: Boolean get() = !loading
    val sizing: SizingUiState get() {
        val frequency = draft[FieldKey.SIZING_FREQUENCY]
        val factor = draft[FieldKey.FACTOR]
        val f = frequency.frequency().validValue()
        val k = factor.factor().validValue()
        return SizingUiState(frequency, factor, f?.let(::calculateIdealSizing),
            if (f != null && k != null) calculateSizing(SizingInput(f, k)) else null)
    }
    val trimming: TrimmingUiState get() {
        val length = draft[FieldKey.ACTUAL_LENGTH]
        val measured = draft[FieldKey.MEASURED]
        val target = draft[FieldKey.TARGET]
        val l = length.length().validValue()
        val fm = measured.frequency().validValue()
        val ft = target.frequency().validValue()
        return TrimmingUiState(length, measured, target, if (l != null && fm != null && ft != null)
            calculateTrimming(TrimmingInput(l, fm, ft, draft.settings.antennaType)) else null)
    }
}

sealed interface CalculatorEvent {
    data class Matching(val value: MatchingDraft) : CalculatorEvent
    data class Edit(val key: FieldKey, val text: String) : CalculatorEvent
    data class ChangeUnit(val key: FieldKey, val unit: String) : CalculatorEvent
    data class Focus(val key: FieldKey, val focused: Boolean) : CalculatorEvent
    data class Check(val keys: Set<FieldKey>) : CalculatorEvent
    data class Settings(val value: CalculatorSettings) : CalculatorEvent
    data object RetryStorage : CalculatorEvent
    /** Only dispatch after an explicit user action consenting to discard stored settings. */
    data object ResetStorage : CalculatorEvent
    data object Background : CalculatorEvent
}
