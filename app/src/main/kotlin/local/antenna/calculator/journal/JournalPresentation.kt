package local.antenna.calculator.journal

import android.content.Context
import local.antenna.calculator.ui.FrequencyNumberFormat
import local.antenna.calculator.ui.labelResource
import local.antenna.calculator.ui.formatLength
import local.antenna.calculator.core.units.LengthUnit
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.LoopFeed
import local.antenna.calculator.core.layout.LoopModel
import local.antenna.calculator.core.layout.LoopShape
import local.antenna.calculator.ui.loopLabel
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.trimming.AntennaType

internal fun AntennaType.journalLabel(): Int = when (this) {
    AntennaType.MONOPOLE -> R.string.monopole
    AntennaType.SYMMETRIC_DIPOLE -> R.string.dipole
    AntennaType.YAGI -> R.string.yagi_title
    AntennaType.J_POLE -> R.string.jpole_title
    AntennaType.FULL_WAVE_LOOP -> R.string.loop_title
}

internal fun MeasurementPoint.journalLabel(): Int = when (this) {
    MeasurementPoint.FEED_POINT -> R.string.journal_point_feed
    MeasurementPoint.TRANSCEIVER -> R.string.journal_point_radio
    MeasurementPoint.UNSPECIFIED -> R.string.journal_point_unknown
}

internal fun JournalProblem.label(): Int = when (this) {
    JournalProblem.INVALID -> R.string.journal_problem_invalid
    JournalProblem.UNSUPPORTED -> R.string.journal_problem_unsupported
    JournalProblem.LIMIT -> R.string.journal_problem_limit
    JournalProblem.READ -> R.string.journal_problem_read
    JournalProblem.WRITE -> R.string.journal_problem_write
    JournalProblem.MISSING -> R.string.journal_problem_missing
}

internal class JournalPresentation(private val context: Context) {
    private val locale = context.resources.configuration.locales[0]
    private val frequencies = FrequencyNumberFormat(locale)
    private fun text(id: Int): String = context.getString(id)

    fun date(value: OffsetDateTime): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(value) + " (UTC" +
            (if (value.offset.totalSeconds == 0) "+00:00" else value.offset.id) + ")"

    fun number(value: Number, digits: Int = 9): String = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = digits
    }.format(value)

    fun frequency(hertz: Number): String = context.getString(R.string.value_unit, frequencies.megahertz(hertz), text(R.string.mhz))
    fun length(nanometers: Long): String = formatLength(context, nanometers / 1e9, LengthUnit.CENTIMETERS)
    private fun withUnit(value: Number, unit: Int) = context.getString(R.string.value_unit, number(value), text(unit))

    fun formatFields(details: List<Pair<String, String>>): String =
        details.joinToString("\n") { (label, value) -> context.getString(R.string.label_value, label, value) }

    fun projectDetails(p: AntennaProject, includeNote: Boolean = true): List<Pair<String, String>> = buildList {
        add(text(R.string.antenna_type) to text(p.antennaType.journalLabel()))
        p.target?.let { add(text(R.string.target_frequency) to frequency(it.hertz)) }
        p.initialLength?.let { add(text(R.string.journal_length) to length(it.nanometers)) }
        if (p.installation.isNotBlank()) add(text(R.string.journal_installation) to p.installation)
        if (p.feedline.isNotBlank()) add(text(R.string.journal_feedline) to p.feedline)
        if (includeNote && p.note.isNotBlank()) add(text(R.string.journal_note) to p.note)
    }

    fun recordDetails(r: JournalRecord): List<Pair<String, String>> = buildList {
        add(text(R.string.journal_date) to date(r.time))
        add(text(R.string.antenna_type) to text(r.antennaType.journalLabel()))
        if (r.installation.isNotBlank()) add(text(R.string.journal_installation) to r.installation)
        if (r.feedline.isNotBlank()) add(text(R.string.journal_feedline) to r.feedline)
        r.measurement?.let { m ->
            add(text(R.string.journal_point) to text(m.point.journalLabel()))
            m.length?.let { add(text(R.string.journal_length) to length(it.nanometers)) }
            m.resonance?.let { add(text(R.string.measured_frequency) to frequency(it.hertz)) }
            m.testFrequency?.let { add(text(R.string.journal_test_frequency_label) to frequency(it.hertz)) }
            m.swr?.let { add(text(R.string.matching_swr) to number(it)) }
            m.referenceOhms?.let { add(text(R.string.journal_reference) to withUnit(it, R.string.journal_ohms)) }
            m.resistanceOhms?.let { add(text(R.string.journal_r_label) to withUnit(it, R.string.journal_ohms)) }
            m.reactanceOhms?.let { add(text(R.string.journal_x_label) to withUnit(it, R.string.journal_ohms)) }
        }
        r.calculation?.let { addAll(snapshotDetails(it, r.antennaType)) }
        if (r.note.isNotBlank()) add(text(R.string.journal_note) to r.note)
    }

    fun snapshotDetails(s: CalculationSnapshot, type: AntennaType): List<Pair<String, String>> = buildList {
            val label = when (s.model) {
                "sizing" -> text(R.string.journal_sizing_model)
                "trimming" -> text(R.string.journal_trim_model)
                "ground_plane" -> text(R.string.ground_plane_title)
                "yagi_r3udk" -> text(R.string.yagi_record_model)
                "j_pole" -> text(R.string.jpole_title)
                "full_wave_loop" -> text(R.string.loop_title)
                "inverted_v" -> text(R.string.inverted_v_title)
                else -> s.model
            }
            add(text(R.string.journal_calculation) to context.getString(R.string.journal_model, label, s.revision))
            for (metric in s.inputs) add(text(R.string.journal_calculation_input) + " · " + metricLabel(metric.key, type) to metricValue(metric))
            for (metric in s.outputsForAntenna(type)) {
                add(text(R.string.journal_calculation_output) + " · " + metricLabel(metric.key, type) to metricValue(metric))
            }
            for (warning in s.warnings) {
                val message = when (warning) {
                    "LARGE_FREQUENCY_CHANGE" -> text(R.string.large_change_note)
                    "RESULT_OUTSIDE_INPUT_LENGTH_RANGE" -> text(R.string.outside_length_note)
                    "LENGTH_DIFFERS_FROM_MODEL" -> text(R.string.trimming_model_warning)
                    "RADIALS_TOUCH_GROUND" -> text(R.string.ground_plane_contact)
                    "ENDS_TOUCH_GROUND" -> text(R.string.inverted_v_ground_contact)
                    else -> warning
                }
                add(text(R.string.approximate) to message)
            }
    }

    fun changes(c: JournalComparison): List<Pair<String, String>> = buildList {
        c.resonanceDeltaHz?.let { add(text(R.string.measured_frequency) to signed(it.toDouble(), frequency(it))) }
        c.lengthDeltaNm?.let { add(text(R.string.journal_length) to signed(it.toDouble(), length(it))) }
        c.swrDelta?.let { add(text(R.string.matching_swr) to signed(it, number(it))) }
    }

    private fun signed(value: Double, formatted: String): String = if (value > 0) "+$formatted" else formatted

    private fun metricLabel(key: String, type: AntennaType): String = when (key) {
        "frequency" -> text(R.string.frequency)
        "factor" -> text(R.string.factor)
        "target" -> text(R.string.target_frequency)
        "resonance" -> text(R.string.measured_frequency)
        "length" -> text(R.string.journal_length)
        "wavelength" -> text(R.string.wavelength)
        "ideal_quarter" -> text(if (type == AntennaType.MONOPOLE) R.string.journal_ideal_quarter_monopole else R.string.journal_ideal_quarter_arm)
        "ideal_half" -> text(R.string.ideal_half)
        "ideal_five_eighths" -> text(R.string.ideal_five_eighths)
        "monopole_length" -> text(R.string.radiator_length)
        "dipole_total" -> text(R.string.dipole_total)
        "dipole_arm" -> text(R.string.dipole_arm)
        "new_length" -> text(if (type == AntennaType.MONOPOLE) R.string.new_length else R.string.new_total)
        "new_arm" -> text(R.string.new_arm)
        "adjustment" -> text(R.string.adjustment)
        "arm_adjustment" -> text(R.string.arm_adjustment)
        "loop_shape" -> text(R.string.loop_shape)
        "loop_model" -> text(R.string.loop_model)
        "loop_feed" -> text(R.string.loop_feed)
        "loop_factor" -> text(R.string.loop_factor)
        "loop_frequency" -> text(R.string.loop_calculated_frequency)
        "jpole_long" -> text(R.string.jpole_long)
        "jpole_stub" -> text(R.string.jpole_stub)
        "jpole_radiator" -> text(R.string.jpole_radiator)
        "jpole_spacing" -> text(R.string.jpole_spacing)
        "jpole_tap" -> text(R.string.jpole_tap)
        "loop_perimeter" -> text(R.string.loop_perimeter)
        "loop_side" -> text(R.string.loop_side)
        "loop_width" -> text(R.string.loop_width)
        "loop_height" -> text(R.string.loop_height)
        "gp_base" -> text(R.string.ground_plane_height)
        "gp_slope" -> text(R.string.ground_plane_angle)
        "gp_whip_factor" -> text(R.string.ground_plane_whip_factor)
        "gp_radial_factor" -> text(R.string.ground_plane_radial_factor)
        "gp_whip" -> text(R.string.ground_plane_whip)
        "gp_radial" -> text(R.string.ground_plane_radial)
        "gp_radius" -> text(R.string.ground_plane_radius)
        "gp_diameter" -> text(R.string.ground_plane_diameter)
        "gp_drop" -> text(R.string.ground_plane_drop)
        "gp_end_height" -> text(R.string.ground_plane_end_height)
        "gp_tip" -> text(R.string.ground_plane_tip)
        "gp_wire" -> text(R.string.ground_plane_wire)
        "apex_height" -> text(R.string.inverted_v_height)
        "included_angle" -> text(R.string.inverted_v_angle)
        "end_span" -> text(R.string.inverted_v_span)
        "end_height" -> text(R.string.inverted_v_end_height)
        "yagi_unit" -> text(R.string.yagi_record_unit)
        "yagi_allowance" -> text(R.string.yagi_allowance)
        "yagi_rear" -> text(R.string.yagi_rear)
        "yagi_front" -> text(R.string.yagi_front)
        "yagi_span" -> text(R.string.yagi_span)
        "yagi_boom" -> text(R.string.yagi_boom)
        else -> yagiMetricLabel(key) ?: key
    }

    private fun yagiMetricLabel(key: String): String? {
        if (!key.startsWith("yagi_")) return null
        val element = when (key.removePrefix("yagi_").substringBefore('_')) {
            "r" -> R.string.construction_yagi_reflector
            "de" -> R.string.construction_yagi_driven
            "d1" -> R.string.construction_yagi_director_1
            "d2" -> R.string.construction_yagi_director_2
            else -> return null
        }
        val column = when (key.substringAfterLast('_')) {
            "position" -> R.string.yagi_position
            "overall" -> R.string.yagi_overall
            "diameter" -> R.string.yagi_diameter
            "blank" -> R.string.yagi_record_blank
            else -> return null
        }
        return context.getString(R.string.yagi_record_column, text(element), text(column))
    }

    private fun metricValue(metric: SnapshotValue): String {
        if (metric.key == "yagi_unit") {
            LengthUnit.entries.firstOrNull { it.ordinal.toDouble() == metric.value }?.let { return text(it.labelResource()) }
        }
        val label = when (metric.key) {
            "loop_shape" -> LoopShape.entries.firstOrNull { it.sides.toDouble() == metric.value }?.loopLabel()
            "loop_model" -> LoopModel.entries.firstOrNull { it.ordinal.toDouble() == metric.value }?.loopLabel()
            "loop_feed" -> LoopFeed.entries.firstOrNull { it.ordinal.toDouble() == metric.value }?.loopLabel()
            else -> null
        }
        if (label != null) return text(label)
        return when (metric.unit) {
        SnapshotUnit.HERTZ -> frequency(metric.value)
        SnapshotUnit.NANOMETERS -> formatLength(context, metric.value / 1e9, LengthUnit.CENTIMETERS)
        SnapshotUnit.METERS -> formatLength(context, metric.value, LengthUnit.METERS)
        SnapshotUnit.FACTOR -> number(metric.value)
        SnapshotUnit.DEGREES -> context.getString(R.string.inverted_v_degrees, number(metric.value))
        }
    }
}
