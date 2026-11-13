package local.antenna.calculator.state

import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.sizing.*
import local.antenna.calculator.core.trimming.*
import local.antenna.calculator.core.units.*
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.roundToLong

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
enum class FieldKey {
    SIZING_FREQUENCY, FACTOR, ACTUAL_LENGTH, MEASURED, TARGET, OTHER_ANTENNA_LENGTH;

    val isLength: Boolean get() = this == ACTUAL_LENGTH || this == OTHER_ANTENNA_LENGTH
}

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
        FieldKey.ACTUAL_LENGTH, FieldKey.OTHER_ANTENNA_LENGTH -> length()
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
        if (key.isLength) LengthUnit.valueOf(next) else FrequencyUnit.valueOf(next)
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
) {
    init { require(antennaType in AntennaType.straightConductors) }
}

data class CalculatorDraft(
    val fields: Map<FieldKey, NumericDraft>,
    val settings: CalculatorSettings = CalculatorSettings(),
    val matching: MatchingDraft = MatchingDraft(),
    val cable: CableDraft = CableDraft(),
    val invertedV: InvertedVDraft = InvertedVDraft(),
    val groundPlane: GroundPlaneDraft = GroundPlaneDraft(),
    val feedline: FeedlineDraft = FeedlineDraft(),
    val fullWaveLoop: FullWaveLoopDraft = FullWaveLoopDraft(),
) {
    operator fun get(key: FieldKey): NumericDraft = fields.getValue(key)
    fun update(key: FieldKey, field: NumericDraft) = copy(fields = fields + (key to field))
    fun changeSettings(next: CalculatorSettings): CalculatorDraft {
        if (settings.antennaType == next.antennaType) return copy(settings = next)
        val active = get(FieldKey.ACTUAL_LENGTH)
        val other = get(FieldKey.OTHER_ANTENNA_LENGTH)
        return copy(settings = next).update(FieldKey.ACTUAL_LENGTH, other.copy(focused = false))
            .update(FieldKey.OTHER_ANTENNA_LENGTH, active.copy(focused = false))
    }

    fun edit(key: FieldKey, text: String): CalculatorDraft {
        val field = get(key).edit(text).let { if (key.isLength) it.copy(checked = true) else it }
        val updated = update(key, field)
        val pairedKey = when (key) {
            FieldKey.SIZING_FREQUENCY -> FieldKey.TARGET
            FieldKey.TARGET -> FieldKey.SIZING_FREQUENCY
            else -> return updated
        }
        val frequency = field.frequency().validValue() ?: return updated
        val pairedField = get(pairedKey)
        return updated.update(pairedKey, pairedField.edit(
            frequency.inUnit(FrequencyUnit.valueOf(pairedField.unit)).inputText(),
        ))
    }

    companion object {
        fun defaults() = CalculatorDraft(FieldKey.entries.associateWith { key ->
            when (key) {
                FieldKey.FACTOR -> NumericDraft("0,95", "FACTOR")
                FieldKey.ACTUAL_LENGTH, FieldKey.OTHER_ANTENNA_LENGTH -> NumericDraft("", LengthUnit.MILLIMETERS.name)
                FieldKey.TARGET -> NumericDraft("155", FrequencyUnit.MEGAHERTZ.name)
                else -> NumericDraft("145", FrequencyUnit.MEGAHERTZ.name)
            }
        })
    }
}

data class SizingUiState(val frequency: NumericDraft, val factor: NumericDraft, val ideal: IdealSizingResult?, val result: SizingResult?)
data class TrimmingUiState(val actualLength: NumericDraft, val measured: NumericDraft, val target: NumericDraft, val result: TrimmingResult?)
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
    fun measuredLength(type: AntennaType): PhysicalLength? {
        val key = if (type == draft.settings.antennaType) FieldKey.ACTUAL_LENGTH else FieldKey.OTHER_ANTENNA_LENGTH
        return draft[key].length().validValue()
    }

    fun trimmingProjection(type: AntennaType): TrimmingResult? =
        measuredLength(type)?.let { calculateProjection(type, it) }

    private fun calculateProjection(type: AntennaType, length: PhysicalLength): TrimmingResult? {
        val measured = draft[FieldKey.MEASURED].frequency().validValue() ?: return null
        val target = draft[FieldKey.TARGET].frequency().validValue() ?: return null
        return calculateTrimming(TrimmingInput(length, measured, target, type))
    }

    fun transferableSizingLength(): PhysicalLength? {
        val estimate = sizing.result ?: return null
        val meters = if (draft.settings.antennaType == AntennaType.MONOPOLE)
            estimate.monopoleMeters else estimate.dipoleTotalMeters
        val nanometers = (meters * 1e9).roundToLong()
        return nanometers.takeIf { it in PhysicalLength.MIN_NANOMETERS..PhysicalLength.MAX_NANOMETERS }
            ?.let(PhysicalLength::ofNanometers)
    }
    val suggestedTrimmingLength: NumericDraft? get() {
        val field = draft[FieldKey.ACTUAL_LENGTH]
        if (draft.settings.section != Section.TRIMMING || field.text.isNotBlank() || (field.focused && field.checked)) return null
        val estimate = transferableSizingLength() ?: return null
        return field.edit(estimate.inUnit(LengthUnit.valueOf(field.unit)).inputText())
    }

    val trimming: TrimmingUiState get() {
        val length = suggestedTrimmingLength ?: draft[FieldKey.ACTUAL_LENGTH]
        val measured = draft[FieldKey.MEASURED]
        val target = draft[FieldKey.TARGET]
        val result = length.length().validValue()?.let { calculateProjection(draft.settings.antennaType, it) }
        return TrimmingUiState(length, measured, target, result)
    }
}

sealed interface CalculatorEvent {
    data class Cable(val value: CableDraft) : CalculatorEvent
    data class Feedline(val value: FeedlineDraft) : CalculatorEvent
    data class GroundPlane(val value: GroundPlaneDraft) : CalculatorEvent
    data class FullWaveLoop(val value: FullWaveLoopDraft) : CalculatorEvent
    data class InvertedV(val value: InvertedVDraft) : CalculatorEvent
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
