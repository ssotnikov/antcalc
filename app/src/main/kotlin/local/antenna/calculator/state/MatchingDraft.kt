package local.antenna.calculator.state

import local.antenna.calculator.core.matching.*

private fun boundedMatchingText(text: String): String {
    val trimmed = text.trim()
    // An overflow marker cannot disappear when the numeric parser trims whitespace.
    return if (trimmed.length > 32) trimmed.take(32) + "!" else trimmed
}

data class MatchingDraft(
    val mode: MatchingMode = MatchingMode.SWR,
    val fields: Map<MatchingField, String> = mapOf(
        MatchingField.SWR to "2",
        MatchingField.GAMMA to "0,333",
        MatchingField.RETURN_LOSS to "9,54",
        MatchingField.RESISTANCE to "100",
        MatchingField.REACTANCE to "0",
        MatchingField.REFERENCE to "50",
    ),
    val checked: Set<MatchingField> = emptySet(),
) {
    val active: List<MatchingField> get() = when (mode) {
        MatchingMode.SWR -> listOf(MatchingField.SWR)
        MatchingMode.GAMMA -> listOf(MatchingField.GAMMA)
        MatchingMode.RETURN_LOSS -> listOf(MatchingField.RETURN_LOSS)
        MatchingMode.IMPEDANCE -> listOf(MatchingField.RESISTANCE, MatchingField.REACTANCE, MatchingField.REFERENCE)
    }
    fun edit(field: MatchingField, text: String) = copy(fields = fields + (field to boundedMatchingText(text)))
    fun number(field: MatchingField) = parseMatchingNumber(fields.getValue(field), field)
    fun canonical(previous: MatchingDraft): MatchingDraft = copy(
        fields = fields.mapValues { (key, text) -> if (number(key).value != null) text.trim() else previous.fields.getValue(key) },
        checked = emptySet(),
    )
    fun result(): MatchingResult? {
        return when (mode) {
            MatchingMode.SWR -> number(MatchingField.SWR).value?.let(::matchingFromSwr)
            MatchingMode.GAMMA -> number(MatchingField.GAMMA).value?.let(::matchingFromMagnitude)
            MatchingMode.RETURN_LOSS -> number(MatchingField.RETURN_LOSS).value?.let(::matchingFromReturnLoss)
            MatchingMode.IMPEDANCE -> {
                val resistance = number(MatchingField.RESISTANCE).value ?: return null
                val reactance = number(MatchingField.REACTANCE).value ?: return null
                val reference = number(MatchingField.REFERENCE).value ?: return null
                matchingFromImpedance(resistance, reactance, reference)
            }
        }
    }
}

/** Small, explicit wire layout, independent of enum ordering. */
object MatchingDraftCodec {
    const val KEY = "matching.draft.v1"
    private val fields = listOf(MatchingField.SWR, MatchingField.GAMMA, MatchingField.RETURN_LOSS,
        MatchingField.RESISTANCE, MatchingField.REACTANCE, MatchingField.REFERENCE)
    fun encode(draft: MatchingDraft): ArrayList<String> = arrayListOf(draft.mode.name).apply {
        fields.forEach { add(boundedMatchingText(draft.fields.getValue(it))); add((it in draft.checked).toString()) }
    }
    fun decode(values: ArrayList<String>?): MatchingDraft? = try {
        if (values == null || values.size != 13) null else MatchingDraft(
            MatchingMode.valueOf(values[0]), fields.mapIndexed { i, f -> f to boundedMatchingText(values[1 + i * 2]) }.toMap(),
            fields.filterIndexed { i, _ -> values[2 + i * 2].toBooleanStrict() }.toSet(),
        )
    } catch (_: IllegalArgumentException) { null }
}
