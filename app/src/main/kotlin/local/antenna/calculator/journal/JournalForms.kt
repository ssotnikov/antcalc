package local.antenna.calculator.journal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import local.antenna.calculator.R
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.matching.MatchingField
import local.antenna.calculator.core.matching.parseMatchingNumber
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.Section
import local.antenna.calculator.state.validValue
import local.antenna.calculator.ui.ChoicePicker

private fun frequency(text: String) = QuantityInput.frequency(text, FrequencyUnit.MEGAHERTZ).validValue()
private fun length(text: String) = QuantityInput.physicalLength(text, LengthUnit.CENTIMETERS).validValue()
private fun decimal(value: Long?, divisor: Long): String = value?.let {
    BigDecimal.valueOf(it).divide(BigDecimal.valueOf(divisor)).stripTrailingZeros().toPlainString()
}.orEmpty()

@Composable
internal fun ProjectForm(initial: AntennaProject?, enabled: Boolean, onSave: (AntennaProject) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial?.antennaType ?: AntennaType.MONOPOLE) }
    var target by rememberSaveable { mutableStateOf(decimal(initial?.target?.hertz, 1_000_000)) }
    var initialLength by rememberSaveable { mutableStateOf(decimal(initial?.initialLength?.nanometers, 10_000_000)) }
    var installation by rememberSaveable { mutableStateOf(initial?.installation.orEmpty()) }
    var feedline by rememberSaveable { mutableStateOf(initial?.feedline.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsedTarget = frequency(target)
    val parsedLength = length(initialLength)
    val targetError = target.isNotBlank() && parsedTarget == null
    val lengthError = initialLength.isNotBlank() && parsedLength == null
    JournalColumn {
        JournalField(title, { title = it }, R.string.journal_name, "journal-name", enabled,
            JournalLimits.TITLE, error = R.string.journal_required.takeIf { attempted && title.isBlank() })
        ChoicePicker(stringResource(R.string.antenna_type), AntennaType.entries, type,
            { stringResource(it.journalLabel()) }, { if (enabled) type = it }, "journal-type")
        JournalNumberField(target, { target = it }, R.string.journal_target, "journal-target", enabled, targetError)
        JournalNumberField(initialLength, { initialLength = it }, R.string.journal_initial_length, "journal-initial-length", enabled, lengthError)
        JournalNotes(installation, { installation = it }, feedline, { feedline = it }, note, { note = it }, enabled)
        Button(onClick = {
            attempted = true
            if (title.isNotBlank() && !targetError && !lengthError) {
                onSave(AntennaProject(initial?.id ?: UUID.randomUUID(), title.trim(), type, parsedTarget,
                    parsedLength, note, installation, feedline, initial?.records.orEmpty()))
            }
        }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("journal-save")) { Text(stringResource(R.string.journal_save)) }
    }
}

@Composable
internal fun RecordForm(
    project: AntennaProject,
    calculator: CalculatorUiState,
    currentCalculator: () -> CalculatorUiState,
    enabled: Boolean,
    onSave: (JournalRecord) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var recordType by rememberSaveable { mutableStateOf(RecordType.MEASUREMENT) }
    var calculationSection by rememberSaveable { mutableStateOf(Section.SIZING) }
    var point by rememberSaveable { mutableStateOf(MeasurementPoint.UNSPECIFIED) }
    var timeText by rememberSaveable { mutableStateOf(OffsetDateTime.now().withNano(0).toString()) }
    val time = OffsetDateTime.parse(timeText)
    var measuredLength by rememberSaveable { mutableStateOf("") }
    var resonance by rememberSaveable { mutableStateOf("") }
    var testFrequency by rememberSaveable { mutableStateOf("") }
    var swr by rememberSaveable { mutableStateOf("") }
    var resistance by rememberSaveable { mutableStateOf("") }
    var reactance by rememberSaveable { mutableStateOf("") }
    var installation by rememberSaveable { mutableStateOf(project.installation) }
    var feedline by rememberSaveable { mutableStateOf(project.feedline) }
    var note by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val size = length(measuredLength)
    val resonanceValue = frequency(resonance)
    val testFrequencyValue = frequency(testFrequency)
    val swrValue = parseMatchingNumber(swr, MatchingField.SWR).value
    val resistanceValue = parseMatchingNumber(resistance, MatchingField.RESISTANCE).value
    val reactanceValue = parseMatchingNumber(reactance, MatchingField.REACTANCE).value
    val errors = listOf(
        measuredLength.invalid(size), resonance.invalid(resonanceValue), testFrequency.invalid(testFrequencyValue),
        swr.invalid(swrValue), resistance.invalid(resistanceValue), reactance.invalid(reactanceValue),
    )
    val hasMeasurement = (size != null || resonanceValue != null || swrValue != null || resistanceValue != null) &&
        (resistanceValue == null) == (reactanceValue == null)
    val preview = if (recordType == RecordType.CALCULATION) {
        journalSnapshot(calculator, project.antennaType, calculationSection)
    } else null
    val presentation = JournalPresentation(LocalContext.current)
    JournalColumn {
        ChoicePicker(stringResource(R.string.journal_record), RecordType.entries, recordType,
            { stringResource(it.label) }, { if (enabled) { recordType = it; attempted = false } }, "journal-record-type")
        JournalField(title, { title = it }, R.string.journal_name, "journal-record-name", enabled,
            JournalLimits.TITLE, error = R.string.journal_required.takeIf { attempted && title.isBlank() })
        JournalDateTime(time, enabled) { timeText = it.toString() }
        if (recordType == RecordType.MEASUREMENT) {
            JournalNumberField(measuredLength, { measuredLength = it }, R.string.journal_measure_length, "journal-length", enabled, errors[0])
            JournalNumberField(resonance, { resonance = it }, R.string.journal_resonance, "journal-resonance", enabled, errors[1])
            JournalNumberField(swr, { swr = it }, R.string.matching_swr, "journal-swr", enabled, errors[3])
            JournalNumberField(resistance, { resistance = it }, R.string.journal_resistance, "journal-resistance", enabled, errors[4])
            JournalNumberField(reactance, { reactance = it }, R.string.journal_reactance, "journal-reactance", enabled, errors[5], signed = true)
            JournalNumberField(testFrequency, { testFrequency = it }, R.string.journal_test_frequency, "journal-test-frequency", enabled, errors[2])
            ChoicePicker(stringResource(R.string.journal_point), MeasurementPoint.entries, point,
                { stringResource(it.journalLabel()) }, { if (enabled) point = it }, "journal-point")
            if (attempted && !hasMeasurement) Text(stringResource(R.string.journal_measurement_required), color = MaterialTheme.colorScheme.error)
        } else {
            ChoicePicker(stringResource(R.string.journal_choose_calc), listOf(Section.SIZING, Section.TRIMMING), calculationSection,
                { stringResource(if (it == Section.SIZING) R.string.sizes else R.string.trimming) },
                { if (enabled) calculationSection = it }, "journal-calculation")
            if (preview == null) {
                Text(stringResource(R.string.journal_missing_calc), color = MaterialTheme.colorScheme.error)
            } else {
                JournalDetails(presentation.snapshotDetails(preview))
            }
        }
        JournalNotes(installation, { installation = it }, feedline, { feedline = it }, note, { note = it }, enabled)
        Button(onClick = {
            attempted = true
            if (title.isBlank()) return@Button
            val measurement = if (recordType == RecordType.MEASUREMENT) {
                if (!hasMeasurement || errors.any { it }) return@Button
                JournalMeasurement(size, resonanceValue, testFrequencyValue, swrValue, resistanceValue, reactanceValue, point)
            } else null
            val snapshot = if (recordType == RecordType.CALCULATION) {
                journalSnapshot(currentCalculator(), project.antennaType, calculationSection) ?: return@Button
            } else null
            onSave(JournalRecord(UUID.randomUUID(), title.trim(), time, project.antennaType, note, installation, feedline, measurement, snapshot))
        }, enabled = enabled && (recordType == RecordType.MEASUREMENT || preview != null),
            modifier = Modifier.fillMaxWidth().testTag("journal-save-record")) { Text(stringResource(R.string.journal_save)) }
    }
}

private enum class RecordType(val label: Int) {
    MEASUREMENT(R.string.journal_measurement), CALCULATION(R.string.journal_calculation),
}

private fun String.invalid(value: Any?) = isNotBlank() && value == null

@Composable
private fun JournalDateTime(time: OffsetDateTime, enabled: Boolean, onChange: (OffsetDateTime) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val dialogContext = remember(activity, configuration) {
        activity?.let { ContextThemeWrapper(it, 0).apply { applyOverrideConfiguration(configuration) } }
    }
    Text(stringResource(R.string.journal_date), style = MaterialTheme.typography.labelLarge)
    Text(JournalPresentation(context).date(time))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled, modifier = Modifier.weight(1f), onClick = {
            val host = dialogContext ?: return@OutlinedButton
            DatePickerDialog(host, { _, year, month, day ->
                val date = java.time.LocalDate.of(year, month + 1, day)
                onChange(date.atTime(time.toLocalTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime())
            }, time.year, time.monthValue - 1, time.dayOfMonth).show()
        }) { Text(stringResource(R.string.journal_date_change)) }
        OutlinedButton(enabled = enabled, modifier = Modifier.weight(1f), onClick = {
            val host = dialogContext ?: return@OutlinedButton
            TimePickerDialog(host, { _, hour, minute ->
                onChange(time.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toOffsetDateTime())
            }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
        }) { Text(stringResource(R.string.journal_time_change)) }
    }
}

@Composable
private fun JournalNotes(
    installation: String, onInstallation: (String) -> Unit,
    feedline: String, onFeedline: (String) -> Unit,
    note: String, onNote: (String) -> Unit, enabled: Boolean,
) {
    JournalField(installation, onInstallation, R.string.journal_installation, "journal-installation", enabled, JournalLimits.NOTE, multiline = true)
    JournalField(feedline, onFeedline, R.string.journal_feedline, "journal-feedline", enabled, JournalLimits.NOTE, multiline = true)
    JournalField(note, onNote, R.string.journal_note, "journal-note", enabled, JournalLimits.NOTE, multiline = true)
}

@Composable
private fun JournalNumberField(
    value: String, onChange: (String) -> Unit, label: Int, tag: String, enabled: Boolean, invalid: Boolean, signed: Boolean = false,
) {
    val limit = PositiveDecimalParser.MAX_CHARACTERS
    JournalField(value, { input -> onChange(if (input.length <= limit) input else input.take(limit) + "!") },
        label, tag, enabled, Int.MAX_VALUE,
        error = R.string.journal_number_invalid.takeIf { invalid },
        keyboard = if (signed) KeyboardType.Text else KeyboardType.Decimal)
}

@Composable
private fun JournalField(
    value: String, onChange: (String) -> Unit, label: Int, tag: String, enabled: Boolean, limit: Int,
    multiline: Boolean = false, error: Int? = null, keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(value, { input -> if (input.length <= limit) onChange(input) },
        label = { Text(stringResource(label)) }, enabled = enabled, singleLine = !multiline,
        maxLines = if (multiline) 4 else 1, isError = error != null,
        supportingText = error?.let { id -> { Text(stringResource(id)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth().testTag(tag))
}
