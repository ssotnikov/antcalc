package local.antenna.calculator.state

import local.antenna.calculator.core.matching.*

private fun boundedMatchingText(text: String): String {
    val trimmed = text.trim()
    // An overflow marker cannot disappear when the numeric parser trims whitespace.
    return if (trimmed.length > 32) trimmed.take(32) + "!" else trimmed
}

data class MatchingDraft(
    val mode: MatchingMode = MatchingMode.SWR,
    val fields: Map<MatchingField, String> = MatchingField.entries.zip(listOf("2", "0,333", "9,54", "100", "0", "50")).toMap(),
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
        if (active.any { number(it).value == null }) return null
        fun value(key: MatchingField) = number(key).value!!
        return when (mode) {
            MatchingMode.SWR -> matchingFromSwr(value(MatchingField.SWR))
            MatchingMode.GAMMA -> matchingFromMagnitude(value(MatchingField.GAMMA))
            MatchingMode.RETURN_LOSS -> matchingFromReturnLoss(value(MatchingField.RETURN_LOSS))
            MatchingMode.IMPEDANCE -> matchingFromImpedance(value(MatchingField.RESISTANCE), value(MatchingField.REACTANCE), value(MatchingField.REFERENCE))
        }
    }
}

/** Small, explicit wire layout, independent of enum ordering. */
object MatchingDraftCodec {
    const val KEY = "matching.draft.v1"
    val fields = listOf(MatchingField.SWR, MatchingField.GAMMA, MatchingField.RETURN_LOSS,
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
