package local.antenna.calculator.ui

import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal
import kotlin.math.roundToLong
import local.antenna.calculator.R
import local.antenna.calculator.core.HaloCb
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue

internal const val HALO_CB_MODEL = "halo_cb"

internal enum class HaloVariant(val title: Int, val shape: HaloCb.Shape?) {
    ROUND(R.string.halo_round, HaloCb.Shape.ROUND),
    SQUARE(R.string.halo_square, HaloCb.Shape.SQUARE),
    CSQ11(R.string.halo_csq11, null),
}

internal enum class HaloGammaField(val key: String, val label: Int, val unit: SnapshotUnit = SnapshotUnit.METERS) {
    ELEMENT("halo_element", R.string.halo_element),
    ROD("halo_rod", R.string.halo_rod),
    LENGTH("halo_rod_length", R.string.halo_rod_length),
    SPACING("halo_spacing", R.string.halo_spacing),
    TAP("halo_tap", R.string.halo_tap),
    CAPACITANCE("halo_capacitance", R.string.halo_capacitance, SnapshotUnit.PICOFARADS);

    val maximum get() = if (unit == SnapshotUnit.PICOFARADS) 1000.0 else 3000.0
}

private fun haloNumber(raw: String, min: Double, max: Double): Double? =
    (PositiveDecimalParser.parse(raw) as? FieldState.Valid)?.value?.toDouble()?.takeIf { it in min..max }

internal data class HaloDraft(
    val variant: HaloVariant = HaloVariant.ROUND,
    val frequency: String = "27.185",
    val factor: String = "1.02",
    val gap: String = "200",
    val gamma: List<String> = List(HaloGammaField.entries.size) { "" },
) {
    val hertz get() = haloNumber(frequency, 26.0, 28.0)?.times(1_000_000)?.roundToLong()
    val k get() = haloNumber(factor, .90, 1.10)
    val gapMm get() = haloNumber(gap, 1.0, 1000.0)
    val geometry get(): HaloCb.Geometry? {
        return HaloCb.calculate(variant.shape ?: return null,
            (hertz ?: return null) / 1_000_000.0, k ?: return null, gapMm ?: return null)
    }

    fun gammaValue(field: HaloGammaField) = haloNumber(gamma[field.ordinal], .1, field.maximum)

    fun gammaErrors(): Set<HaloGammaField> = buildSet {
        HaloGammaField.entries.filterTo(this) { gamma[it.ordinal].isNotBlank() && gammaValue(it) == null }
        val element = gammaValue(HaloGammaField.ELEMENT)
        val rod = gammaValue(HaloGammaField.ROD)
        val spacing = gammaValue(HaloGammaField.SPACING)
        if (element != null && rod != null && spacing != null && spacing <= (element + rod) / 2) add(HaloGammaField.SPACING)
        val tap = gammaValue(HaloGammaField.TAP)
        val length = gammaValue(HaloGammaField.LENGTH)
        if (tap != null && length != null && tap > length) add(HaloGammaField.TAP)
    }

    fun snapshot(): CalculationSnapshot? {
        val result = geometry ?: return null
        if (gammaErrors().isNotEmpty()) return null
        val inputs = buildList {
            add(SnapshotValue("frequency", checkNotNull(hertz).toDouble(), SnapshotUnit.HERTZ))
            add(SnapshotValue("halo_shape", if (variant == HaloVariant.ROUND) 1.0 else 2.0, SnapshotUnit.FACTOR))
            add(SnapshotValue("halo_factor", checkNotNull(k), SnapshotUnit.FACTOR))
            add(SnapshotValue("halo_gap", checkNotNull(gapMm) / 1000, SnapshotUnit.METERS))
            for (field in HaloGammaField.entries) {
                val value = gammaValue(field) ?: continue
                add(SnapshotValue(field.key, if (field.unit == SnapshotUnit.METERS) value / 1000 else value, field.unit))
            }
        }
        val outputs = resultRows(result).map { (key, value) -> SnapshotValue(key, value, SnapshotUnit.METERS) }
        return CalculationSnapshot(HALO_CB_MODEL, 1, inputs, outputs)
    }

    fun resultRows(result: HaloCb.Geometry) = listOf(
        "halo_wavelength" to result.wavelength,
        "halo_perimeter" to result.perimeter,
        (if (variant == HaloVariant.ROUND) "halo_diameter" else "halo_side") to result.span,
        "halo_conductor" to result.conductor,
    )

    companion object {
        val saver = listSaver<HaloDraft, String>(
            save = { listOf(it.variant.name, it.frequency, it.factor, it.gap) + it.gamma },
            restore = { HaloDraft(HaloVariant.valueOf(it[0]), it[1], it[2], it[3], it.drop(4)) },
        )

        fun fromSnapshot(snapshot: CalculationSnapshot): HaloDraft? {
            if (snapshot.model != HALO_CB_MODEL || snapshot.revision != 1) return null
            val inputs = snapshot.inputs.associateBy { it.key }
            fun field(key: String, unit: SnapshotUnit, decimalShift: Int = 0): String =
                inputs[key]?.takeIf { it.unit == unit }?.let {
                    BigDecimal.valueOf(it.value).scaleByPowerOfTen(decimalShift).stripTrailingZeros().toPlainString()
                }.orEmpty()
            val variant = when (inputs["halo_shape"]?.value) {
                1.0 -> HaloVariant.ROUND
                2.0 -> HaloVariant.SQUARE
                else -> return null
            }
            return HaloDraft(variant, field("frequency", SnapshotUnit.HERTZ, -6),
                field("halo_factor", SnapshotUnit.FACTOR), field("halo_gap", SnapshotUnit.METERS, 3),
                HaloGammaField.entries.map { field(it.key, it.unit, if (it.unit == SnapshotUnit.METERS) 3 else 0) },
            ).takeIf { it.snapshot() != null }
        }
    }
}

internal fun haloMetricLabel(key: String): Int? = when (key) {
    "halo_shape" -> R.string.halo_variant
    "halo_factor" -> R.string.halo_factor
    "halo_gap" -> R.string.halo_gap
    "halo_wavelength" -> R.string.halo_wavelength
    "halo_perimeter" -> R.string.halo_perimeter
    "halo_diameter" -> R.string.halo_diameter
    "halo_side" -> R.string.halo_side
    "halo_conductor" -> R.string.halo_conductor
    else -> HaloGammaField.entries.firstOrNull { it.key == key }?.label
}
