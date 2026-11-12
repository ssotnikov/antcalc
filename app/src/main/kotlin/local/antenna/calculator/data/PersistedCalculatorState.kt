package local.antenna.calculator.data

import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*
import java.math.BigDecimal

/** Last valid inputs: canonical quantities and bounded matching decimals, never calculated results. */
data class PersistedCalculatorState(
    val values: Map<FieldKey, Long>,
    val units: Map<FieldKey, String>,
    val settings: CalculatorSettings,
    val matching: MatchingDraft = MatchingDraft(),
    val cable: CableDraft = CableDraft(),
    val invertedV: InvertedVDraft = InvertedVDraft(),
    val groundPlane: GroundPlaneDraft = GroundPlaneDraft(),
    val feedline: FeedlineDraft = FeedlineDraft(),
    val fullWaveLoop: FullWaveLoopDraft = FullWaveLoopDraft(),
) {
    fun advance(draft: CalculatorDraft): PersistedCalculatorState = copy(
        values = values + draft.fields.mapNotNull { (key, field) ->
            when (val value = (field.state(key) as? FieldState.Valid)?.value) {
                is Frequency -> key to value.hertz
                is PhysicalLength -> key to value.nanometers
                is ShorteningFactor -> key to value.tenThousandths.toLong()
                else -> null
            }
        },
        units = draft.fields.mapValues { it.value.unit }, settings = draft.settings,
        matching = draft.matching.canonical(matching),
        cable = draft.cable.canonical(cable),
        invertedV = draft.invertedV.canonical(invertedV),
        groundPlane = draft.groundPlane.canonical(groundPlane),
        feedline = draft.feedline,
        fullWaveLoop = draft.fullWaveLoop.canonical(fullWaveLoop),
    )

    fun toDraft(): CalculatorDraft = CalculatorDraft(FieldKey.entries.associateWith { key ->
        val unit = units.getValue(key)
        val value = values[key]
        val text = if (value == null) "" else when (key) {
            FieldKey.FACTOR -> BigDecimal.valueOf(value, 4).inputText()
            FieldKey.ACTUAL_LENGTH -> PhysicalLength.ofNanometers(value).inUnit(LengthUnit.valueOf(unit)).inputText()
            else -> Frequency.ofHertz(value).inUnit(FrequencyUnit.valueOf(unit)).inputText()
        }
        NumericDraft(text, unit)
    }, settings, matching, cable, invertedV, groundPlane, feedline, fullWaveLoop)

    companion object {
        fun defaults(): PersistedCalculatorState {
            val draft = CalculatorDraft.defaults()
            return PersistedCalculatorState(emptyMap(), draft.fields.mapValues { it.value.unit }, draft.settings).advance(draft)
        }
    }
}
