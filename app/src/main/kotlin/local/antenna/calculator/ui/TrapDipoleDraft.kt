package local.antenna.calculator.ui

import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal
import kotlin.math.roundToLong
import local.antenna.calculator.R
import local.antenna.calculator.core.TrapDipole.Band
import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotUnit.*
import local.antenna.calculator.core.journal.SnapshotValue

internal const val TRAP_DIPOLE_MODEL = "hf_trap_dipole"
internal val trapSectionNames = listOf("A", "B", "C")
internal val trapPublications = listOf(
    "https://eax.me/2019/2019-02-25-diy-trap-dipole.html",
    "https://earc.org.au/?mdocs-file=696",
    "https://boringhamradiopart.blogspot.com/2020/09/804020m-trap-dipole.html",
)
internal val trapAuthors = listOf(R.string.trap_author_r2auk, R.string.trap_author_vk2evb, R.string.trap_author_aa4lr)
internal val trapInstallation = listOf(R.string.trap_install_r2auk, R.string.trap_install_vk2evb, R.string.trap_install_aa4lr)
internal val trapMeasurements = listOf(R.string.trap_measured_r2auk, R.string.trap_measured_vk2evb, R.string.trap_measured_aa4lr)
internal const val TRAP_WHEELER_URL = "https://doi.org/10.1109/JRPROC.1928.221309"
internal const val TRAP_ARRL_URL = "https://www.arrl.org/hf-trap-antennas"
internal const val TRAP_ZL1BJQ_URL = "https://www.zl1bjq.co.nz/dipole1.html"

internal fun trapNumber(raw: String, range: ClosedFloatingPointRange<Double>): Double? =
    (PositiveDecimalParser.parse(raw, allowZero = range.start == 0.0) as? FieldState.Valid)?.value?.toDouble()?.takeIf { it in range }

internal data class TrapDraft(
    val allowance: String = "",
    val targets: List<String> = List(Band.entries.size) { "" },
    val dimensions: List<String> = List(6) { "" },
) {
    val allowanceMm get() = if (allowance.isBlank()) 0.0 else trapNumber(allowance, 0.0..1000.0)
    fun target(band: Band) = trapNumber(targets[band.ordinal], band.lowerMhz..band.upperMhz)
    fun dimension(index: Int) = trapNumber(dimensions[index], .001..30.0)

    fun invalidTraps(profile: Profile): Set<Int> = buildSet {
        profile.traps.forEachIndexed { index, trap ->
            val start = index * 3
            val known = listOf(trap.blankMeters, trap.stripNearMeters, trap.stripFarMeters)
            val applicable = if (profile.coaxial) 0..2 else 0..0
            for (field in applicable) {
                if (known[field] == null && dimensions[start + field].isNotBlank() && dimension(start + field) == null) add(index)
            }
            val blank = known[0] ?: dimension(start)
            val near = known[1] ?: dimension(start + 1)
            val far = known[2] ?: dimension(start + 2)
            if (profile.coaxial && blank != null && (near ?: 0.0) + (far ?: 0.0) >= blank) add(index)
        }
    }

    fun snapshot(profile: Profile, band: Band): CalculationSnapshot? {
        val allowanceValue = allowanceMm ?: return null
        val blanks = profile.blanks(allowanceValue) ?: return null
        val f = target(band)
        if (f == null && targets[band.ordinal].isNotBlank() || invalidTraps(profile).isNotEmpty()) return null
        val inputs = buildList {
            add(SnapshotValue("trap_profile", profile.ordinal.toDouble(), FACTOR))
            add(SnapshotValue("trap_band", band.meters.toDouble(), FACTOR))
            add(SnapshotValue("trap_allowance", allowanceValue / 1000, METERS))
            f?.let { add(SnapshotValue("frequency", (it * 1e6).roundToLong().toDouble(), HERTZ)) }
            profile.traps.forEachIndexed { index, trap ->
                val known = listOf(trap.blankMeters, trap.stripNearMeters, trap.stripFarMeters)
                for (field in 0 until if (profile.coaxial) 3 else 1) {
                    if (known[field] == null) dimension(index * 3 + field)?.let {
                        add(SnapshotValue("trap_user_${index}_${field}", it, METERS))
                    }
                }
            }
        }
        val outputs = buildList {
            trapSectionNames.forEachIndexed { index, name ->
                add(SnapshotValue("trap_nominal_${name.lowercase()}", profile.sections[index], METERS))
                add(SnapshotValue("trap_blank_${name.lowercase()}", blanks[index], METERS))
            }
            add(SnapshotValue("trap_wire", 2 * blanks.sum(), METERS))
            profile.traps.forEachIndexed { index, trap ->
                val prefix = "trap_published_${index}_"
                add(SnapshotValue(prefix + "diameter", trap.diameterMm / 1000, METERS))
                add(SnapshotValue(prefix + "turns", trap.turns, FACTOR))
                trap.formerLengthMm?.let { add(SnapshotValue(prefix + "former", it / 1000, METERS)) }
                trap.measuredMhz?.let { add(SnapshotValue(prefix + "frequency", it * 1e6, HERTZ)) }
                trap.capacitancePf?.let { add(SnapshotValue(prefix + "capacitor", it, PICOFARADS)) }
                listOf(trap.blankMeters, trap.stripNearMeters, trap.stripFarMeters).forEachIndexed { field, value ->
                    value?.let { add(SnapshotValue(prefix + field, it, METERS)) }
                }
                (trap.blankMeters ?: dimension(index * 3))?.let {
                    add(SnapshotValue("trap_material_${index}", 2 * it, METERS))
                }
            }
        }
        return CalculationSnapshot(TRAP_DIPOLE_MODEL, 1, inputs, outputs)
    }

    fun saved() = listOf(allowance) + targets + dimensions

    companion object {
        val saver = listSaver<List<TrapDraft>, String>(
            save = { it.flatMap(TrapDraft::saved) },
            restore = { it.chunked(1 + Band.entries.size + 6).map { values ->
                TrapDraft(values[0], values.drop(1).take(Band.entries.size), values.drop(1 + Band.entries.size))
            } },
        )

        fun restore(snapshot: CalculationSnapshot): Triple<Profile, Band, TrapDraft>? {
            if (snapshot.model != TRAP_DIPOLE_MODEL || snapshot.revision != 1) return null
            val inputs = snapshot.inputs.associateBy { it.key }
            val profile = Profile.entries.firstOrNull { it.ordinal.toDouble() == inputs["trap_profile"]?.value } ?: return null
            val band = Band.entries.firstOrNull { it.meters.toDouble() == inputs["trap_band"]?.value } ?: return null
            fun field(key: String, unit: SnapshotUnit, shift: Int = 0) =
                inputs[key]?.takeIf { it.unit == unit }?.let {
                    BigDecimal.valueOf(it.value).scaleByPowerOfTen(shift).stripTrailingZeros().toPlainString()
                }.orEmpty()
            val draft = TrapDraft(field("trap_allowance", METERS, 3),
                Band.entries.map { if (it == band) field("frequency", HERTZ, -6) else "" },
                List(6) { field("trap_user_${it / 3}_${it % 3}", METERS) })
            if (draft.snapshot(profile, band) == null) return null
            return Triple(profile, band, draft)
        }
    }
}
