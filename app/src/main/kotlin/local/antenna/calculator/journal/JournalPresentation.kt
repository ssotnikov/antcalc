package local.antenna.calculator.journal

import android.content.Context
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.trimming.AntennaType

internal fun AntennaType.journalLabel(): Int =
    if (this == AntennaType.SYMMETRIC_DIPOLE) R.string.dipole else R.string.monopole

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
    private fun text(id: Int): String = context.getString(id)

    fun date(value: OffsetDateTime): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(value) + " (UTC" +
            (if (value.offset.totalSeconds == 0) "+00:00" else value.offset.id) + ")"

    fun number(value: Number, digits: Int = 9): String = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = digits
    }.format(value)

    fun frequency(hertz: Long): String = withUnit(hertz / 1_000_000.0, R.string.mhz)
    fun length(nanometers: Long): String = withUnit(nanometers / 10_000_000.0, R.string.cm)
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
            m.resistanceOhms?.let { add(text(R.string.journal_r_label) to withUnit(it, R.string.journal_ohms)) }
            m.reactanceOhms?.let { add(text(R.string.journal_x_label) to withUnit(it, R.string.journal_ohms)) }
        }
        r.calculation?.let { addAll(snapshotDetails(it)) }
        if (r.note.isNotBlank()) add(text(R.string.journal_note) to r.note)
    }

    fun snapshotDetails(s: CalculationSnapshot): List<Pair<String, String>> = buildList {
            val label = when (s.model) {
                "sizing" -> text(R.string.journal_sizing_model)
                "trimming" -> text(R.string.journal_trim_model)
                "inverted_v" -> text(R.string.inverted_v_title)
                else -> s.model
            }
            add(text(R.string.journal_calculation) to context.getString(R.string.journal_model, label, s.revision))
            for (metric in s.inputs) add(text(R.string.journal_calculation_input) + " · " + metricLabel(metric.key) to metricValue(metric))
            for (metric in s.outputs) add(text(R.string.journal_calculation_output) + " · " + metricLabel(metric.key) to metricValue(metric))
            for (warning in s.warnings) {
                val message = when (warning) {
                    "LARGE_FREQUENCY_CHANGE" -> text(R.string.large_change_note)
                    "RESULT_OUTSIDE_INPUT_LENGTH_RANGE" -> text(R.string.outside_length_note)
                    "LENGTH_DIFFERS_FROM_MODEL" -> text(R.string.trimming_model_warning)
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

    private fun metricLabel(key: String): String = when (key) {
        "frequency" -> text(R.string.frequency)
        "factor" -> text(R.string.factor)
        "target" -> text(R.string.target_frequency)
        "resonance" -> text(R.string.measured_frequency)
        "length" -> text(R.string.journal_length)
        "wavelength" -> text(R.string.wavelength)
        "ideal_quarter" -> text(R.string.ideal_quarter)
        "ideal_half" -> text(R.string.ideal_half)
        "ideal_five_eighths" -> text(R.string.ideal_five_eighths)
        "monopole_length" -> text(R.string.radiator_length)
        "dipole_total" -> text(R.string.dipole_total)
        "dipole_arm" -> text(R.string.dipole_arm)
        "new_length" -> text(R.string.new_length)
        "new_arm" -> text(R.string.new_arm)
        "adjustment" -> text(R.string.adjustment)
        "arm_adjustment" -> text(R.string.arm_adjustment)
        "apex_height" -> text(R.string.inverted_v_height)
        "included_angle" -> text(R.string.inverted_v_angle)
        "end_span" -> text(R.string.inverted_v_span)
        "end_height" -> text(R.string.inverted_v_end_height)
        else -> key
    }

    private fun metricValue(metric: SnapshotValue): String = when (metric.unit) {
        SnapshotUnit.HERTZ -> withUnit(metric.value / 1_000_000, R.string.mhz)
        SnapshotUnit.NANOMETERS -> withUnit(metric.value / 10_000_000, R.string.cm)
        SnapshotUnit.METERS -> withUnit(metric.value, R.string.m)
        SnapshotUnit.FACTOR -> number(metric.value)
        SnapshotUnit.DEGREES -> context.getString(R.string.inverted_v_degrees, number(metric.value))
    }
}
