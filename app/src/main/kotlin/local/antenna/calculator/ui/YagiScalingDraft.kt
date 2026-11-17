package local.antenna.calculator.ui

import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.layout.YagiScaling
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.layout.YagiScaling.Geometry
import local.antenna.calculator.core.layout.YagiScaling.Mode
import local.antenna.calculator.core.units.LengthUnit

internal const val YAGI_SCALING_URL = "https://www.dg7ybn.de/Scaling/Scaling_Yagis.htm"
internal const val YAGI_SCALING_BOOK = "https://www.qsl.net/vu2msy/homebrew/Yagi_Design_Basics_by_DL6WU.pdf"
internal const val YAGI_SCALING_TERMS = "https://www.dg7ybn.de/"

internal data class ScalingRow(val position: String, val length: String, val diameter: String = "", val target: String = "") {
    fun fields() = listOf(position, length, diameter, target)
}

internal data class YagiScalingDraft(
    val mode: Mode = Mode.FREQUENCY,
    val geometry: Geometry = Geometry.FREE_ELEMENTS,
    val source: String = "",
    val target: String = "",
    val unit: LengthUnit = LengthUnit.MILLIMETERS,
    val diameter: String = "",
    val targetDiameter: String = "",
    val gap: String = "",
    val rows: List<ScalingRow> = listOf(ScalingRow("0", ""), ScalingRow("", "")),
) {
    private val metresPerUnit get() = unit.nanometersPerUnit / 1e9
    val sourceHz get() = scalingFrequency(source)
    val targetHz get() = if (mode == Mode.DIAMETER) sourceHz else scalingFrequency(target)
    val split get() = (scalingDecimal(gap) ?: 0.0) > 0
    val enteredGap get() = scalingDecimal(gap)?.times(metresPerUnit)

    fun hasMissingRequiredData(): Boolean {
        if (source.isBlank() || mode != Mode.DIAMETER && target.isBlank()) return true
        return rows.withIndex().any { (index, row) ->
            val needsTargetDiameter = mode != Mode.FREQUENCY && !(index == 1 && split)
            row.position.isBlank() || row.length.isBlank() || row.diameter.ifBlank { diameter }.isBlank() ||
                needsTargetDiameter && row.target.ifBlank { targetDiameter }.isBlank()
        }
    }

    fun element(row: ScalingRow, index: Int): Element? {
        fun dimension(raw: String) = scalingDecimal(raw)?.times(metresPerUnit)
        val from = dimension(row.diameter.ifBlank { diameter }) ?: return null
        val to = when {
            mode == Mode.FREQUENCY -> null
            index == 1 && split -> {
                val f1 = sourceHz ?: return null
                val f2 = targetHz ?: return null
                from * (f1.toDouble() / f2)
            }
            else -> dimension(row.target.ifBlank { targetDiameter }) ?: return null
        }
        return Element(dimension(row.position) ?: return null, dimension(row.length) ?: return null, from, to)
    }

    fun result(): YagiScaling.Result? {
        val f1 = sourceHz ?: return null
        val f2 = targetHz ?: return null
        val elements = rows.mapIndexed { index, row -> element(row, index) ?: return null }
        val feedGap = if (gap.isBlank()) null else enteredGap ?: return null
        return YagiScaling.calculate(mode, geometry, f1, f2, elements, feedGap)
    }

    fun inUnit(next: LengthUnit): YagiScalingDraft {
        if (next == unit) return this
        fun convert(raw: String): String {
            val number = scalingDecimal(raw) ?: return raw
            return plain(number * unit.nanometersPerUnit / next.nanometersPerUnit)
        }
        return copy(unit = next, diameter = convert(diameter), targetDiameter = convert(targetDiameter), gap = convert(gap),
            rows = rows.map { ScalingRow(convert(it.position), convert(it.length), convert(it.diameter), convert(it.target)) })
    }

    fun snapshot(result: YagiScaling.Result): CalculationSnapshot {
        fun values(rows: List<Element>) = rows.flatMapIndexed { i, row ->
            listOf(SnapshotValue("ys_pos_${i + 1}", row.position, SnapshotUnit.METERS),
                SnapshotValue("ys_len_${i + 1}", row.length, SnapshotUnit.METERS),
                SnapshotValue("ys_dia_${i + 1}", row.diameter, SnapshotUnit.METERS))
        }
        return CalculationSnapshot(YagiScaling.MODEL, 1, buildList {
            add(SnapshotValue("ys_source", requireNotNull(sourceHz).toDouble(), SnapshotUnit.HERTZ))
            add(SnapshotValue("frequency", requireNotNull(targetHz).toDouble(), SnapshotUnit.HERTZ))
            add(SnapshotValue("ys_mode", mode.ordinal.toDouble(), SnapshotUnit.FACTOR))
            add(SnapshotValue("ys_geometry", geometry.ordinal.toDouble(), SnapshotUnit.FACTOR))
            add(SnapshotValue("ys_unit", unit.ordinal.toDouble(), SnapshotUnit.FACTOR))
            enteredGap?.let { add(SnapshotValue("ys_gap", it, SnapshotUnit.METERS)) }
            addAll(values(rows.mapIndexed { i, row -> requireNotNull(element(row, i)) }))
        }, buildList {
            add(SnapshotValue("ys_factor", result.factor, SnapshotUnit.FACTOR))
            result.feedGap?.let { add(SnapshotValue("ys_gap", it, SnapshotUnit.METERS)) }
            addAll(values(result.elements))
        })
    }

    companion object {
        val saver = listSaver<YagiScalingDraft, String>(
            save = { listOf(it.mode.name, it.geometry.name, it.source, it.target, it.unit.name,
                it.diameter, it.targetDiameter, it.gap) + it.rows.flatMap(ScalingRow::fields) },
            restore = { values ->
                YagiScalingDraft(Mode.valueOf(values[0]), Geometry.valueOf(values[1]), values[2], values[3],
                    LengthUnit.valueOf(values[4]), values[5], values[6], values[7],
                    values.drop(8).chunked(4).map { ScalingRow(it[0], it[1], it[2], it[3]) })
            },
        )

        fun example() = YagiScalingDraft(source = "145", target = "175", diameter = "8", targetDiameter = "6",
            rows = listOf(ScalingRow("0", "1040"), ScalingRow("215", "970"), ScalingRow("415", "945"), ScalingRow("810", "908")))

        fun restore(snapshot: CalculationSnapshot): YagiScalingDraft? {
            if (snapshot.model != YagiScaling.MODEL || snapshot.revision != 1) return null
            val inputs = snapshot.inputs.associateBy { it.key }
            val outputs = snapshot.outputs.associateBy { it.key }
            fun Map<String, SnapshotValue>.value(key: String, unit: SnapshotUnit) =
                get(key)?.takeIf { it.unit == unit }?.value
            fun MHz(key: String) = inputs.value(key, SnapshotUnit.HERTZ)?.let { plain(it / 1e6) }
            fun mm(values: Map<String, SnapshotValue>, key: String) =
                values.value(key, SnapshotUnit.METERS)?.let { plain(it * 1000) }
            val mode = Mode.entries.firstOrNull { it.ordinal.toDouble() == inputs.value("ys_mode", SnapshotUnit.FACTOR) } ?: return null
            val geometry = Geometry.entries.firstOrNull { it.ordinal.toDouble() == inputs.value("ys_geometry", SnapshotUnit.FACTOR) } ?: return null
            val rows = (1..YagiScaling.MAX_ELEMENTS).takeWhile { "ys_pos_$it" in inputs }.map { i ->
                ScalingRow(mm(inputs, "ys_pos_$i") ?: return null, mm(inputs, "ys_len_$i") ?: return null,
                    mm(inputs, "ys_dia_$i") ?: return null, mm(outputs, "ys_dia_$i") ?: return null)
            }
            val unit = LengthUnit.entries.firstOrNull { it.ordinal.toDouble() == inputs.value("ys_unit", SnapshotUnit.FACTOR) }
                ?: LengthUnit.MILLIMETERS
            val draft = YagiScalingDraft(mode, geometry, MHz("ys_source") ?: return null, MHz("frequency") ?: return null,
                gap = mm(inputs, "ys_gap").orEmpty(), rows = rows).inUnit(unit)
            return draft.takeIf { it.result() != null }
        }
    }
}

internal fun scalingDecimal(raw: String): Double? = raw.trim().takeIf { it.length <= 32 && scalingNumber.matches(it) }
    ?.replace(',', '.')?.toDoubleOrNull()?.takeIf(Double::isFinite)

private val scalingNumber = Regex("[0-9]+(?:[.,][0-9]+)?")

private fun scalingFrequency(raw: String): Long? {
    if (scalingDecimal(raw) == null) return null
    return runCatching { BigDecimal(raw.trim().replace(',', '.')).movePointRight(6).longValueExact() }.getOrNull()
        ?.takeIf { it in 100_000L..6_000_000_000L }
}

private fun plain(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal fun scalingElementName(index: Int) = when (index) {
    0 -> "R"
    1 -> "DE"
    else -> "D${index - 1}"
}
