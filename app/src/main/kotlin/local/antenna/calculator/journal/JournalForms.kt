package local.antenna.calculator.journal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
internal fun ProjectForm(
    initial: AntennaProject?, enabled: Boolean, problem: JournalProblem? = null, onSave: (AntennaProject) -> Unit,
) {
    val title = rememberTextFieldState(initial?.title.orEmpty())
    var type by rememberSaveable { mutableStateOf(initial?.antennaType ?: AntennaType.MONOPOLE) }
    val target = rememberTextFieldState(decimal(initial?.target?.hertz, 1_000_000))
    val initialLength = rememberTextFieldState(decimal(initial?.initialLength?.nanometers, 10_000_000))
    val installation = rememberTextFieldState(initial?.installation.orEmpty())
    val feedline = rememberTextFieldState(initial?.feedline.orEmpty())
    val note = rememberTextFieldState(initial?.note.orEmpty())
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsedTarget = frequency(target.text.toString())
    val parsedLength = length(initialLength.text.toString())
    val targetError = target.text.isNotBlank() && parsedTarget == null
    val lengthError = initialLength.text.isNotBlank() && parsedLength == null
    val titleLocation = remember { BringIntoViewRequester() }
    val targetLocation = remember { BringIntoViewRequester() }
    val lengthLocation = remember { BringIntoViewRequester() }
    var scrollTarget by remember { mutableStateOf<BringIntoViewRequester?>(null) }
    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        try {
            target.bringIntoView()
        } finally {
            if (scrollTarget === target) scrollTarget = null
        }
    }
    JournalColumn {
        problem?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
        JournalTextField(title, R.string.journal_project_name, R.string.journal_example_name, "journal-name", enabled,
            modifier = Modifier.bringIntoViewRequester(titleLocation), limit = JournalLimits.TITLE, error = R.string.journal_required.takeIf { attempted && title.text.isBlank() })
        ChoicePicker(stringResource(R.string.antenna_type), AntennaType.entries, type,
            { stringResource(it.journalLabel()) }, { if (enabled) type = it }, "journal-type")
        JournalTextField(target, R.string.journal_target, R.string.journal_example_target, "journal-target", enabled,
            modifier = Modifier.bringIntoViewRequester(targetLocation),
            limit = PositiveDecimalParser.MAX_CHARACTERS, keyboard = KeyboardType.Decimal,
            error = R.string.journal_number_invalid.takeIf { targetError }, megahertz = true)
        JournalTextField(initialLength, R.string.journal_initial_length, R.string.journal_example_length, "journal-initial-length", enabled,
            modifier = Modifier.bringIntoViewRequester(lengthLocation),
            limit = PositiveDecimalParser.MAX_CHARACTERS, keyboard = KeyboardType.Decimal,
            error = R.string.journal_number_invalid.takeIf { lengthError })
        JournalSuggestionField(installation, JournalSuggestion.INSTALLATION, enabled)
        JournalSuggestionField(feedline, JournalSuggestion.FEEDLINE, enabled)
        JournalTextField(note, R.string.journal_note, R.string.journal_example_note, "journal-note", enabled, multiline = true)
        Button(onClick = {
            attempted = true
            scrollTarget = when {
                title.text.isBlank() -> titleLocation
                targetError -> targetLocation
                lengthError -> lengthLocation
                else -> null
            }
            if (scrollTarget == null) {
                onSave(AntennaProject(initial?.id ?: UUID.randomUUID(), title.text.toString().trim(), type,
                    parsedTarget, parsedLength, note.text.toString(), installation.text.toString(),
                    feedline.text.toString(), initial?.records.orEmpty()))
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
    val title = rememberTextFieldState()
    var recordType by rememberSaveable { mutableStateOf(RecordType.MEASUREMENT) }
    var calculationSection by rememberSaveable { mutableStateOf(Section.SIZING) }
    var point by rememberSaveable { mutableStateOf(MeasurementPoint.UNSPECIFIED) }
    var timeText by rememberSaveable { mutableStateOf(OffsetDateTime.now().withNano(0).toString()) }
    val time = OffsetDateTime.parse(timeText)
    val measuredLength = rememberTextFieldState()
    val resonance = rememberTextFieldState()
    val testFrequency = rememberTextFieldState()
    val swr = rememberTextFieldState()
    val reference = rememberTextFieldState()
    val resistance = rememberTextFieldState()
    val reactance = rememberTextFieldState()
    val installation = rememberTextFieldState(project.installation)
    val feedline = rememberTextFieldState(project.feedline)
    val note = rememberTextFieldState()
    var attempted by rememberSaveable { mutableStateOf(false) }

    val size = length(measuredLength.text.toString())
    val resonanceValue = frequency(resonance.text.toString())
    val testFrequencyValue = frequency(testFrequency.text.toString())
    val referenceValue = parseMatchingNumber(reference.text.toString(), MatchingField.REFERENCE).value
    val swrValue = parseMatchingNumber(swr.text.toString(), MatchingField.SWR).value
    val resistanceValue = parseMatchingNumber(resistance.text.toString(), MatchingField.RESISTANCE).value
    val reactanceValue = parseMatchingNumber(reactance.text.toString(), MatchingField.REACTANCE).value
    val lengthError = measuredLength.text.invalid(size)
    val resonanceError = resonance.text.invalid(resonanceValue)
    val referenceError = reference.text.invalid(referenceValue)
    val swrError = swr.text.invalid(swrValue)
    val resistanceError = resistance.text.invalid(resistanceValue)
    val reactanceError = reactance.text.invalid(reactanceValue)
    val testFrequencyError = testFrequency.text.invalid(testFrequencyValue)
    val missingResistance = reactanceValue != null && resistance.text.isBlank()
    val missingReactance = resistanceValue != null && reactance.text.isBlank()
    val missingMeasurement = size == null && resonanceValue == null && swrValue == null &&
        resistanceValue == null && reactanceValue == null &&
        !lengthError && !resonanceError && !swrError && !resistanceError && !reactanceError
    val titleLocation = remember { BringIntoViewRequester() }
    val lengthLocation = remember { BringIntoViewRequester() }
    val resonanceLocation = remember { BringIntoViewRequester() }
    val referenceLocation = remember { BringIntoViewRequester() }
    val swrLocation = remember { BringIntoViewRequester() }
    val resistanceLocation = remember { BringIntoViewRequester() }
    val reactanceLocation = remember { BringIntoViewRequester() }
    val testFrequencyLocation = remember { BringIntoViewRequester() }
    var scrollTarget by remember { mutableStateOf<BringIntoViewRequester?>(null) }
    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        try {
            target.bringIntoView()
        } finally {
            if (scrollTarget === target) scrollTarget = null
        }
    }
    val preview = if (recordType == RecordType.CALCULATION) {
        journalSnapshot(calculator, project.antennaType, calculationSection)
    } else null
    val presentation = JournalPresentation(LocalContext.current)
    JournalColumn {
        ChoicePicker(stringResource(R.string.journal_record), RecordType.entries, recordType,
            { stringResource(it.label) }, { if (enabled) { recordType = it; attempted = false } }, "journal-record-type")
        val nameExample = when (recordType) {
            RecordType.MEASUREMENT -> R.string.journal_example_record_name
            RecordType.CALCULATION -> R.string.journal_example_calculation_name
        }
        JournalTextField(title, R.string.journal_name, nameExample, "journal-record-name", enabled,
            limit = JournalLimits.TITLE, error = R.string.journal_required.takeIf { attempted && title.text.isBlank() },
            modifier = Modifier.bringIntoViewRequester(titleLocation))
        JournalDateTime(time, enabled) { timeText = it.toString() }
        if (recordType == RecordType.MEASUREMENT) {
            JournalNumberField(measuredLength, R.string.journal_measure_length, R.string.journal_example_length, "journal-length", enabled,
                lengthError, lengthLocation, requiredError = R.string.journal_measurement_required.takeIf { attempted && missingMeasurement })
            JournalNumberField(resonance, R.string.journal_resonance, R.string.journal_example_resonance, "journal-resonance", enabled,
                resonanceError, resonanceLocation, megahertz = true)
            JournalNumberField(swr, R.string.matching_swr, R.string.journal_example_swr, "journal-swr", enabled, swrError, swrLocation)
            JournalNumberField(resistance, R.string.journal_resistance, R.string.journal_example_resistance, "journal-resistance", enabled,
                resistanceError, resistanceLocation, requiredError = R.string.journal_impedance_pair_required.takeIf { attempted && missingResistance })
            JournalNumberField(reactance, R.string.journal_reactance, R.string.journal_example_reactance, "journal-reactance", enabled,
                reactanceError, reactanceLocation, requiredError = R.string.journal_impedance_pair_required.takeIf { attempted && missingReactance },
                signed = true)
            JournalNumberField(testFrequency, R.string.journal_test_frequency, R.string.journal_example_target, "journal-test-frequency", enabled,
                testFrequencyError, testFrequencyLocation, megahertz = true)
            JournalNumberField(reference, R.string.journal_reference_optional, R.string.journal_example_reference,
                "journal-reference", enabled, referenceError, referenceLocation)
            ChoicePicker(stringResource(R.string.journal_point), MeasurementPoint.entries, point,
                { stringResource(it.journalLabel()) }, { if (enabled) point = it }, "journal-point")
        } else {
            ChoicePicker(stringResource(R.string.journal_choose_calc), listOf(Section.SIZING, Section.TRIMMING), calculationSection,
                { stringResource(if (it == Section.SIZING) R.string.sizes else R.string.trimming) },
                { if (enabled) calculationSection = it }, "journal-calculation")
            if (preview == null) {
                Text(stringResource(R.string.journal_missing_calc), color = MaterialTheme.colorScheme.error)
            } else {
                JournalDetails(presentation.snapshotDetails(preview, project.antennaType))
            }
        }
        JournalSuggestionField(installation, JournalSuggestion.INSTALLATION, enabled)
        JournalSuggestionField(feedline, JournalSuggestion.FEEDLINE, enabled)
        JournalTextField(note, R.string.journal_note, R.string.journal_example_note, "journal-note", enabled, multiline = true)
        Button(onClick = {
            attempted = true
            scrollTarget = when {
                title.text.isBlank() -> titleLocation
                recordType != RecordType.MEASUREMENT -> null
                lengthError -> lengthLocation
                resonanceError -> resonanceLocation
                swrError -> swrLocation
                resistanceError || missingResistance -> resistanceLocation
                reactanceError || missingReactance -> reactanceLocation
                missingMeasurement -> lengthLocation
                testFrequencyError -> testFrequencyLocation
                referenceError -> referenceLocation
                else -> null
            }
            if (scrollTarget != null) return@Button
            val measurement = if (recordType == RecordType.MEASUREMENT) {
                JournalMeasurement(size, resonanceValue, testFrequencyValue, swrValue, resistanceValue, reactanceValue, point, referenceValue)
            } else null
            val snapshot = if (recordType == RecordType.CALCULATION) {
                journalSnapshot(currentCalculator(), project.antennaType, calculationSection) ?: return@Button
            } else null
            onSave(JournalRecord(UUID.randomUUID(), title.text.toString().trim(), time, project.antennaType,
                note.text.toString(), installation.text.toString(), feedline.text.toString(), measurement, snapshot))
        }, enabled = enabled && (recordType == RecordType.MEASUREMENT || preview != null),
            modifier = Modifier.fillMaxWidth().testTag("journal-save-record")) { Text(stringResource(R.string.journal_save)) }
    }
}

private enum class RecordType(val label: Int) {
    MEASUREMENT(R.string.journal_measurement), CALCULATION(R.string.journal_calculation),
}

private fun CharSequence.invalid(value: Any?) = isNotBlank() && value == null

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
private fun JournalNumberField(
    state: TextFieldState, label: Int, example: Int, tag: String, enabled: Boolean, invalid: Boolean,
    location: BringIntoViewRequester, requiredError: Int? = null, signed: Boolean = false, megahertz: Boolean = false,
) {
    JournalTextField(
        state, label, example, tag, enabled,
        modifier = Modifier.bringIntoViewRequester(location),
        limit = PositiveDecimalParser.MAX_CHARACTERS,
        keyboard = if (signed) KeyboardType.Text else KeyboardType.Decimal,
        error = if (invalid) R.string.journal_number_invalid else requiredError,
        numeric = true, megahertz = megahertz,
    )
}
