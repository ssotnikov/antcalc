package local.antenna.calculator.ui

import android.content.res.Resources
import local.antenna.calculator.R
import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue

internal fun trapMetricLabel(resources: Resources, key: String): String {
    fun text(id: Int) = resources.getString(id)
    return when {
        key == "frequency" -> text(R.string.trap_target)
        key == "trap_profile" -> text(R.string.trap_construction)
        key == "trap_band" -> text(R.string.trap_window)
        key == "trap_allowance" -> text(R.string.trap_allowance)
        key == "trap_wire" -> text(R.string.trap_cut)
        key == "trap_material_0" -> "T20 · " + text(R.string.trap_pair_material)
        key == "trap_material_1" -> "T40 · " + text(R.string.trap_pair_material)
        key.startsWith("trap_nominal_") -> text(R.string.trap_original_length) + " " + key.last().uppercase()
        key.startsWith("trap_blank_") -> text(R.string.trap_piece_length) + " " + key.last().uppercase() + " × 2"
        key.startsWith("trap_user_") || key.startsWith("trap_published_") -> {
            val fields = key.split('_')
            val trap = when (fields.getOrNull(2)) { "0" -> "T20"; "1" -> "T40"; else -> return key }
            val label = when (fields.getOrNull(3)) {
                "diameter" -> R.string.trap_former
                "turns" -> R.string.trap_turns
                "former" -> R.string.trap_former_length
                "frequency" -> R.string.trap_f
                "capacitor" -> R.string.trap_c
                "0" -> R.string.trap_piece_length
                "1" -> R.string.trap_strip_near
                "2" -> R.string.trap_strip_far
                else -> return key
            }
            val origin = if (fields[1] == "user") " · " + text(R.string.trap_my_build) else ""
            "$trap · ${text(label)}$origin"
        }
        else -> key
    }
}

internal fun trapMetricDisplay(resources: Resources, metric: SnapshotValue, format: (Double, Int) -> String): String? {
    val value = metric.value
    return when {
        metric.key == "trap_profile" -> Profile.entries.firstOrNull { it.ordinal.toDouble() == value }
            ?.let { resources.getString(trapAuthors[it.ordinal]) }
        metric.key == "trap_band" -> resources.getString(R.string.trap_band, value.toInt())
        metric.key == "trap_allowance" || metric.key.endsWith("_diameter") || metric.key.endsWith("_former") ->
            "${format(value * 1000, 6)} ${resources.getString(R.string.hb9cv_aerial_mm)}"
        metric.unit == SnapshotUnit.METERS -> "${format(value, 9)} ${resources.getString(R.string.m)}"
        metric.unit == SnapshotUnit.HERTZ -> resources.getString(R.string.sweep_mhz, format(value / 1e6, 6))
        metric.unit == SnapshotUnit.PICOFARADS -> "${format(value, 6)} ${resources.getString(R.string.halo_pf)}"
        else -> null
    }
}
