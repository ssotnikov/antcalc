package local.antenna.calculator.ui

import android.content.res.Resources
import local.antenna.calculator.R
import local.antenna.calculator.core.Cantenna
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import java.text.NumberFormat

internal class CantennaText(private val resources: Resources) {
    private val format = NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
        maximumFractionDigits = 9
        isGroupingUsed = false
    }
    private fun text(id: Int) = resources.getString(id)
    private val missing get() = text(R.string.sweep_unavailable)
    fun number(value: Double) = format.format(value)
    fun mm(value: Double?) = value?.let { "${number(it * 1000)} ${text(R.string.hb9cv_aerial_mm)}" } ?: missing
    private fun mhz(value: Double) = "${number(value / 1e6)} ${text(R.string.mhz)}"
    fun marginRows(result: Cantenna.Result) = listOf(
        text(R.string.cantenna_low_margin) to mhz(result.lowMarginHz),
        text(R.string.cantenna_high_margin) to mhz(result.highMarginHz),
    )
    fun name(can: CantennaCandidate, index: Int) = can.name.trim().ifBlank { resources.getString(R.string.cantenna_entry, index + 1) }

    private fun mode(mode: Cantenna.Mode) = text(when (mode) {
        Cantenna.Mode.BELOW_CUTOFF -> R.string.cantenna_below
        Cantenna.Mode.TE11 -> R.string.cantenna_single
        Cantenna.Mode.HIGHER_MODES -> R.string.cantenna_higher
    })

    fun resultRows(r: Cantenna.Result, can: Cantenna.Can, published: Boolean) = buildList {
        fun row(label: Int, value: String) { add(text(label) to value) }
        add("TE₁₁" to mhz(r.te11Hz))
        add("TM₀₁" to mhz(r.tm01Hz))
        row(R.string.cantenna_mode, mode(r.mode))
        row(R.string.cantenna_wave, mm(r.wavelength))
        row(R.string.cantenna_guide, mm(r.guideWavelength))
        addAll(marginRows(r))
        if (!published) {
            row(R.string.cantenna_band, text(if (r.bandFits) R.string.cantenna_fits else R.string.cantenna_misses))
            row(R.string.cantenna_suggest, mm(r.suggestedDepth))
            row(R.string.cantenna_probe, mm(r.probe))
            row(R.string.cantenna_offset, if (r.clearance?.let { it <= 0 } == true) text(R.string.cantenna_short) else mm(r.offset))
            if (can.depth != null) row(R.string.cantenna_clearance, mm(r.clearance))
            if (can.contact != null) row(R.string.cantenna_added, r.addedProbe?.let(::mm) ?: text(R.string.cantenna_contact_long))
            if (can.tolerance > 0) row(R.string.cantenna_offset_range,
                r.toleranceOffsets?.let { "${mm(it.start)} – ${mm(it.endInclusive)}" } ?: text(R.string.cantenna_offset_loss))
        }
    }

    fun publishedRows(profile: CantennaDesign, ring: Double?, focal: Int, gain: Boolean) = buildList {
        fun row(label: Int, value: String) { add(text(label) to value) }
        row(R.string.frequency, mhz(profile.mhz * 1e6))
        row(R.string.cantenna_diameter, mm(profile.can.diameter))
        row(R.string.cantenna_depth, mm(profile.can.depth))
        row(R.string.cantenna_probe, mm(profile.probe))
        row(R.string.cantenna_offset, mm(profile.offset))
        if (profile.can.probeDiameter != null) row(R.string.cantenna_probe_d, mm(profile.can.probeDiameter))
        when (profile) {
            CantennaDesign.MIT -> Unit
            CantennaDesign.VE4MA -> {
                add("f/D" to number(Cantenna.focalRatios[focal]))
                row(R.string.cantenna_ring_d, mm(CantennaDesign.RING_DIAMETER))
                row(R.string.cantenna_ring_l, mm(CantennaDesign.RING_DEPTH))
                row(R.string.cantenna_ring_p, mm(ring))
                add("f/D · ${number(Cantenna.focalRatios[focal])}" to text(if (gain) R.string.cantenna_gain else R.string.cantenna_noise))
            }
            CantennaDesign.HORN -> {
                row(R.string.cantenna_pin_h, mm(CantennaDesign.TUNING_PIN_HEIGHT))
                row(R.string.cantenna_pin_spacing, mm(CantennaDesign.TUNING_PIN_SPACING))
            }
        }
    }

    fun sectorRows(sector: Cantenna.Sector?, seam: Double?) = if (sector == null) emptyList() else buildList {
        add(text(R.string.cantenna_slant) to mm(sector.slant))
        add(text(R.string.cantenna_inner) to mm(sector.innerRadius))
        add(text(R.string.cantenna_outer) to mm(sector.outerRadius))
        add(text(R.string.cantenna_angle) to "${number(sector.angle)}°")
        seam?.let { add(text(R.string.cantenna_seam) to mm(it)) }
    }

    fun comparison(cans: List<CantennaCandidate>, hz: Double, band: Cantenna.Band) = buildList {
        add(listOf(text(R.string.cantenna_name), text(R.string.cantenna_diameter),
            text(R.string.cantenna_depth), text(R.string.cantenna_tolerance), "TE₁₁", "TM₀₁",
            text(R.string.cantenna_band), text(R.string.cantenna_offset), text(R.string.cantenna_offset_range),
            text(R.string.cantenna_clearance)))
        cans.forEachIndexed { index, candidate ->
            val can = candidate.geometry()
            val result = can?.let { Cantenna.analyze(it, hz, band) }
            add(listOf(name(candidate, index), mm(can?.diameter), mm(can?.depth), mm(can?.tolerance),
                result?.let { mhz(it.te11Hz) } ?: missing, result?.let { mhz(it.tm01Hz) } ?: missing,
                result?.let { text(if (it.bandFits) R.string.cantenna_fits else R.string.cantenna_misses) } ?: missing,
                mm(result?.offset?.takeUnless { result.clearance?.let { clearance -> clearance <= 0 } == true }),
                result?.toleranceOffsets?.let { "${mm(it.start)} – ${mm(it.endInclusive)}" } ?: missing,
                mm(result?.clearance)))
        }
    }

    fun report(header: String, hz: Double, band: Cantenna.Band, can: Cantenna.Can,
               rows: List<Pair<String, String>>, source: CantennaSource) = buildString {
        appendLine(header)
        appendLine("${text(R.string.frequency)} · ${mhz(hz)}")
        if (band.lowHz != band.highHz) appendLine("${mhz(band.lowHz)} – ${mhz(band.highHz)}")
        appendLine("${text(R.string.cantenna_diameter)} · ${mm(can.diameter)}")
        appendLine("${text(R.string.cantenna_depth)} · ${mm(can.depth)}")
        if (can.tolerance > 0) appendLine("${text(R.string.cantenna_tolerance)} · ${mm(can.tolerance)}")
        can.contact?.let { appendLine("${text(R.string.cantenna_contact)} · ${mm(it)}") }
        can.probeDiameter?.let { appendLine("${text(R.string.cantenna_probe_d)} · ${mm(it)}") }
        rows.forEach { (label, value) -> appendLine("$label · $value") }
        appendLine("${source.author} · ${source.url}")
    }
}

internal fun cantennaSnapshot(
    hz: Double, band: Cantenna.Band, can: Cantenna.Can, r: Cantenna.Result,
    design: CantennaDesign?, ring: Double?, focal: Int, gain: Boolean,
    throat: Double?, mouth: Double?, flare: Double?, sector: Cantenna.Sector?, seam: Double?,
): CalculationSnapshot {
    fun value(key: String, number: Double, unit: SnapshotUnit = SnapshotUnit.METERS) =
        SnapshotValue(if (key == "frequency") key else "cantenna_$key", number, unit)
    val inputs = buildList {
        add(value("frequency", hz, SnapshotUnit.HERTZ))
        add(value("low", band.lowHz, SnapshotUnit.HERTZ))
        add(value("high", band.highHz, SnapshotUnit.HERTZ))
        add(value("diameter", can.diameter))
        can.depth?.let { add(value("depth", it)) }
        if (can.tolerance > 0) add(value("tolerance", can.tolerance))
        can.contact?.let { add(value("contact", it)) }
        can.probeDiameter?.let { add(value("probe_d", it)) }
        if (design == CantennaDesign.VE4MA) {
            add(value("focal", Cantenna.focalRatios[focal], SnapshotUnit.FACTOR))
            add(value("objective", if (gain) 1.0 else 0.0, SnapshotUnit.FACTOR))
        }
        throat?.let { add(value("throat", it)) }
        mouth?.let { add(value("mouth", it)) }
        flare?.let { add(value("flare_l", it)) }
        seam?.let { add(value("seam", it)) }
    }
    val outputs = buildList {
        add(value("te11", r.te11Hz, SnapshotUnit.HERTZ))
        add(value("tm01", r.tm01Hz, SnapshotUnit.HERTZ))
        add(value("wave", r.wavelength))
        r.guideWavelength?.let { add(value("guide", it)) }
        add(value("low_margin", r.lowMarginHz, SnapshotUnit.HERTZ))
        add(value("high_margin", r.highMarginHz, SnapshotUnit.HERTZ))
        add(value("probe", design?.probe ?: r.probe))
        val drilling = design?.offset ?: r.offset?.takeUnless { r.clearance?.let { it <= 0 } == true }
        drilling?.let { add(value("offset", it)) }
        if (design == null) {
            r.suggestedDepth?.let { add(value("suggest", it)) }
            r.clearance?.let { add(value("clearance", it)) }
            r.addedProbe?.let { add(value("added", it)) }
            if (can.tolerance > 0) r.toleranceOffsets?.let {
                add(value("offset_min", it.start))
                add(value("offset_max", it.endInclusive))
            }
        }
        if (design == CantennaDesign.VE4MA) {
            add(value("ring_d", CantennaDesign.RING_DIAMETER))
            add(value("ring_l", CantennaDesign.RING_DEPTH))
            ring?.let { add(value("ring_p", it)) }
        }
        if (design == CantennaDesign.HORN) {
            add(value("pin_h", CantennaDesign.TUNING_PIN_HEIGHT))
            add(value("pin_spacing", CantennaDesign.TUNING_PIN_SPACING))
        }
        if (sector != null) {
            add(value("slant", sector.slant))
            add(value("inner", sector.innerRadius))
            add(value("outer", sector.outerRadius))
            add(value("angle", sector.angle, SnapshotUnit.DEGREES))
        }
    }
    val warnings = buildList {
        if (r.mode == Cantenna.Mode.BELOW_CUTOFF) add("CANTENNA_BELOW")
        if (r.mode == Cantenna.Mode.HIGHER_MODES) add("CANTENNA_HIGHER")
        if (design == null && !r.bandFits) add("CANTENNA_BAND")
        if (design == null && r.clearance?.let { it <= 0 } == true) add("CANTENNA_SHORT")
        if (design == null && can.contact != null && r.addedProbe == null) add("CANTENNA_CONTACT")
        if (design == null && can.tolerance > 0 && r.toleranceOffsets == null) add("CANTENNA_TOLERANCE")
    }
    return CalculationSnapshot(design?.model ?: "cantenna_calculated", 1, inputs, outputs, warnings)
}
