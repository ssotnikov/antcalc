package local.antenna.calculator.state

import java.math.BigDecimal
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.core.layout.groundPlaneLayout
import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor

enum class GroundPlaneMode { ESTIMATED, MANUAL }

enum class GroundPlaneField(val initial: String) {
    FREQUENCY("145"), WHIP_FACTOR("0,95"), RADIAL_FACTOR("0,95"),
    WHIP("0,5"), RADIAL("0,5"), HEIGHT("2"), ANGLE("45"),
}

data class GroundPlaneDraft(
    val mode: GroundPlaneMode = GroundPlaneMode.ESTIMATED,
    val lengthUnit: LengthUnit = LengthUnit.METERS,
    val frequencyUnit: FrequencyUnit = FrequencyUnit.MEGAHERTZ,
    val fields: Map<GroundPlaneField, String> = GroundPlaneField.entries.associateWith { it.initial },
) {
    val visibleFields: List<GroundPlaneField>
        get() = when (mode) {
            GroundPlaneMode.ESTIMATED -> listOf(GroundPlaneField.FREQUENCY, GroundPlaneField.WHIP_FACTOR, GroundPlaneField.RADIAL_FACTOR)
            GroundPlaneMode.MANUAL -> listOf(GroundPlaneField.WHIP, GroundPlaneField.RADIAL)
        } + listOf(GroundPlaneField.HEIGHT, GroundPlaneField.ANGLE)

    fun number(field: GroundPlaneField): FieldState<Long> {
        val text = fields.getValue(field)
        return when (field) {
            GroundPlaneField.WHIP, GroundPlaneField.RADIAL ->
                QuantityInput.physicalLength(text, lengthUnit).mapValue { it.nanometers }
            GroundPlaneField.FREQUENCY -> QuantityInput.frequency(text, frequencyUnit).mapValue { it.hertz }
            GroundPlaneField.WHIP_FACTOR, GroundPlaneField.RADIAL_FACTOR ->
                QuantityInput.shorteningFactor(text).mapValue { it.tenThousandths.toLong() }
            GroundPlaneField.HEIGHT -> QuantityInput.supportHeightNanometers(text, lengthUnit)
            GroundPlaneField.ANGLE -> QuantityInput.radialAngleTenths(text)
        }
    }

    fun value(field: GroundPlaneField): Long? = number(field).validValue()

    fun edit(field: GroundPlaneField, text: String): GroundPlaneDraft =
        copy(fields = fields + (field to text.boundedNumericText()))

    fun changeUnits(length: LengthUnit = lengthUnit, frequency: FrequencyUnit = frequencyUnit): GroundPlaneDraft {
        val updated = copy(lengthUnit = length, frequencyUnit = frequency)
        return updated.copy(fields = fields.mapValues { (field, raw) ->
            if (multiplier(field) == updated.multiplier(field)) raw
            else value(field)?.let { updated.display(field, it) }.orEmpty()
        })
    }

    fun canonical(previous: GroundPlaneDraft): GroundPlaneDraft = copy(
        fields = GroundPlaneField.entries.associateWith { field ->
            display(field, value(field) ?: checkNotNull(previous.value(field)))
        },
    )

    fun result(): GroundPlaneResult? {
        val height = value(GroundPlaneField.HEIGHT) ?: return null
        val angle = value(GroundPlaneField.ANGLE)?.toInt() ?: return null
        return when (mode) {
            GroundPlaneMode.ESTIMATED -> {
                val frequency = value(GroundPlaneField.FREQUENCY) ?: return null
                val whip = value(GroundPlaneField.WHIP_FACTOR)?.toInt() ?: return null
                val radial = value(GroundPlaneField.RADIAL_FACTOR)?.toInt() ?: return null
                groundPlaneLayout(SizingInput(Frequency.ofHertz(frequency), ShorteningFactor.ofTenThousandths(whip)),
                    ShorteningFactor.ofTenThousandths(radial), height, angle)
            }
            GroundPlaneMode.MANUAL -> {
                val whip = value(GroundPlaneField.WHIP) ?: return null
                val radial = value(GroundPlaneField.RADIAL) ?: return null
                groundPlaneLayout(PhysicalLength.ofNanometers(whip), PhysicalLength.ofNanometers(radial), height, angle)
            }
        }
    }

    private fun multiplier(field: GroundPlaneField): Long = when (field) {
        GroundPlaneField.WHIP, GroundPlaneField.RADIAL, GroundPlaneField.HEIGHT -> lengthUnit.nanometersPerUnit
        GroundPlaneField.FREQUENCY -> frequencyUnit.hertzPerUnit
        GroundPlaneField.WHIP_FACTOR, GroundPlaneField.RADIAL_FACTOR -> 10_000
        GroundPlaneField.ANGLE -> 10
    }

    private fun display(field: GroundPlaneField, value: Long): String =
        BigDecimal.valueOf(value).divide(BigDecimal.valueOf(multiplier(field))).inputText()
}

internal object GroundPlaneDraftCodec {
    const val KEY = "ground-plane.draft.v1"

    fun encode(draft: GroundPlaneDraft): ArrayList<String> =
        arrayListOf(draft.mode.name, draft.lengthUnit.name, draft.frequencyUnit.name).apply {
            GroundPlaneField.entries.forEach { add(draft.fields.getValue(it).boundedNumericText()) }
        }

    fun decode(values: List<String>?): GroundPlaneDraft? {
        if (values == null || values.size != 3 + GroundPlaneField.entries.size) return null
        if (values.any { it.length > PositiveDecimalParser.MAX_CHARACTERS + 1 }) return null
        return try {
            GroundPlaneDraft(
                GroundPlaneMode.valueOf(values[0]),
                LengthUnit.valueOf(values[1]),
                FrequencyUnit.valueOf(values[2]),
                GroundPlaneField.entries.associateWith { values[3 + it.ordinal] },
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
