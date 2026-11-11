package local.antenna.calculator.state

import local.antenna.calculator.core.feedline.AttenuationUnit
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.inUnit
import java.math.BigDecimal
import java.math.RoundingMode

data class FeedlineDraft(
    val profileId: String? = "times-lmr-400",
    val frequency: String = "900",
    val frequencyUnit: FrequencyUnit = FrequencyUnit.MEGAHERTZ,
    val length: String = "",
    val power: String = "",
    val attenuation: String = "",
    val attenuationUnit: AttenuationUnit = AttenuationUnit.PER_100_METRES,
    val enteredAttenuationUnit: AttenuationUnit = attenuationUnit,
    val attenuationHertz: Long? = null,
) {
    internal fun hertz() = (QuantityInput.frequency(frequency, frequencyUnit) as? FieldState.Valid)?.value?.hertz
    internal fun metres() = decimal(length, 10000)
    internal fun watts() = decimal(power, 100000)
    internal fun manualAttenuation() = decimal(attenuation, 1000)

    internal fun changeFrequencyUnit(unit: FrequencyUnit): FeedlineDraft =
        copy(frequencyUnit = unit, frequency = hertz()?.let { frequencyText(it, unit) } ?: "")

    internal fun chooseFrequency(hertz: Long) = copy(frequency = frequencyText(hertz, frequencyUnit))

    internal fun editAttenuation(value: String) = copy(
        attenuation = value.boundedNumericText(),
        enteredAttenuationUnit = attenuationUnit,
        attenuationHertz = hertz(),
    )

    internal fun attenuationText(): String {
        if (attenuationUnit == enteredAttenuationUnit) return attenuation
        val value = manualAttenuation() ?: return ""
        return (value * attenuationUnit.referenceMetres)
            .divide(enteredAttenuationUnit.referenceMetres, 12, RoundingMode.HALF_UP).inputText()
    }

    private fun frequencyText(hertz: Long, unit: FrequencyUnit) =
        Frequency.ofHertz(hertz).inUnit(unit).inputText()

    private fun decimal(text: String, max: Long): BigDecimal? =
        (PositiveDecimalParser.parse(text, allowZero = true) as? FieldState.Valid)?.value
            ?.takeIf { it <= BigDecimal.valueOf(max) }
}

internal object FeedlineDraftCodec {
    const val KEY = "feedline.draft.v1"
    private val idFormat = Regex("[a-z0-9-]{1,48}")

    fun encode(draft: FeedlineDraft): ArrayList<String> = with(draft) {
        arrayListOf(profileId.orEmpty(), frequency, frequencyUnit.name, length, power,
            attenuation, attenuationUnit.name, enteredAttenuationUnit.name, attenuationHertz?.toString().orEmpty())
    }

    fun decode(values: List<String>?): FeedlineDraft? = try {
        if (values == null || values.size != 9) null else {
            val profileId = values[0].ifEmpty { null }
            require(profileId == null || idFormat.matches(profileId))
            require(listOf(1, 3, 4, 5).all { values[it].length <= PositiveDecimalParser.MAX_CHARACTERS + 1 })
            FeedlineDraft(profileId, values[1], FrequencyUnit.valueOf(values[2]), values[3], values[4],
                values[5], AttenuationUnit.valueOf(values[6]), AttenuationUnit.valueOf(values[7]),
                values[8].ifEmpty { null }?.toLong()?.also(Frequency::ofHertz))
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}
