package local.antenna.calculator.ui

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.roundToLong
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.JournalLimits
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.journal.JournalColumn
import local.antenna.calculator.journal.JournalDetails
import local.antenna.calculator.journal.JournalJson
import local.antenna.calculator.journal.JournalPresentation
import local.antenna.calculator.journal.JournalTextField
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.journal.journalLabel
import local.antenna.calculator.journal.journalSnapshot
import local.antenna.calculator.journal.label
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.Section

internal data class PendingProjectCalculation(
    val snapshot: CalculationSnapshot,
    val projectId: UUID = UUID.randomUUID(),
    val recordId: UUID = UUID.randomUUID(),
    val time: OffsetDateTime = OffsetDateTime.now(),
) {
    val isSizing get() = snapshot.model == "sizing"
    val title get() = if (isSizing) R.string.sizes else R.string.trimming
    val target get() = Frequency.ofHertz(snapshot.inputs.first { it.key == if (isSizing) "frequency" else "target" }.value.toLong())

    fun initialLength(type: AntennaType): PhysicalLength? {
        val nanometers = if (isSizing) {
            val key = if (type == AntennaType.MONOPOLE) "monopole_length" else "dipole_total"
            (snapshot.outputs.first { it.key == key }.value * 1_000_000_000).roundToLong()
        } else snapshot.inputs.first { it.key == "length" }.value.toLong()
        return nanometers.takeIf { it in PhysicalLength.MIN_NANOMETERS..PhysicalLength.MAX_NANOMETERS }
            ?.let(PhysicalLength::ofNanometers)
    }

    fun record(type: AntennaType, title: String) =
        JournalRecord(recordId, title, time, type, calculation = snapshot)

    companion object {
        fun capture(state: CalculatorUiState, section: Section, type: AntennaType): PendingProjectCalculation? =
            journalSnapshot(state, type, section)?.let { PendingProjectCalculation(it) }

        val saver = Saver<PendingProjectCalculation?, Bundle>(
            save = { pending ->
                pending?.let {
                    Bundle().apply {
                        putByteArray("snapshot", JournalJson.encodePendingSnapshot(it.snapshot))
                        putString("project", it.projectId.toString())
                        putString("record", it.recordId.toString())
                        putString("time", it.time.toString())
                    }
                }
            },
            restore = { bundle ->
                runCatching {
                    PendingProjectCalculation(
                        JournalJson.decodePendingSnapshot(requireNotNull(bundle.getByteArray("snapshot"))),
                        UUID.fromString(bundle.getString("project")),
                        UUID.fromString(bundle.getString("record")),
                        OffsetDateTime.parse(bundle.getString("time")),
                    )
                }.getOrNull()
            },
        )
    }
}

@Composable
internal fun TransferAntennaType(
    pending: PendingProjectCalculation, type: AntennaType, onType: (AntennaType) -> Unit, onContinue: () -> Unit,
) {
    JournalColumn(Modifier.widthIn(max = 680.dp)) {
        HelpText(R.string.project_transfer_type_hint)
        ChoicePicker(stringResource(R.string.antenna_type), AntennaType.entries, type,
            { stringResource(it.journalLabel()) }, onType, "project-transfer-type")
        TransferSummary(pending, type)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().testTag("project-transfer-continue")) {
            Text(stringResource(R.string.project_transfer_show_projects))
        }
    }
}

@Composable
internal fun TransferSummary(pending: PendingProjectCalculation, type: AntennaType) {
    val presentation = JournalPresentation(LocalContext.current)
    val baseline = pending.initialLength(type)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(type.journalLabel()), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.journal_property, stringResource(pending.title), presentation.frequency(pending.target.hertz)),
                style = MaterialTheme.typography.bodyMedium)
            baseline?.let { Text(stringResource(R.string.journal_property, stringResource(R.string.project_transfer_initial_length),
                presentation.length(it.nanometers)), style = MaterialTheme.typography.bodyMedium) }
            if (!pending.isSizing) {
                val length = pending.snapshot.outputs.first { it.key == "new_length" }.value
                Text(stringResource(R.string.journal_property, stringResource(R.string.project_transfer_recommended_length),
                    presentation.length((length * 1_000_000_000).roundToLong())), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun TransferNewProject(
    pending: PendingProjectCalculation, type: AntennaType, journal: JournalViewModel, onSave: (String) -> Unit,
) {
    val status by journal.state.collectAsState()
    val name = rememberTextFieldState()
    val nameLocation = remember { BringIntoViewRequester() }
    var invalidAttempts by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(invalidAttempts) {
        if (invalidAttempts > 0 && name.text.isBlank()) nameLocation.bringIntoView()
    }
    JournalColumn(Modifier.widthIn(max = 680.dp).testTag("project-transfer-new-form")) {
        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status.problem?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
        JournalTextField(name, R.string.journal_project_name, R.string.journal_example_name,
            "project-transfer-name", !status.busy, modifier = Modifier.bringIntoViewRequester(nameLocation),
            limit = JournalLimits.TITLE, error = R.string.journal_required.takeIf { invalidAttempts > 0 && name.text.isBlank() })
        TransferSummary(pending, type)
        if (pending.initialLength(type) == null) HelpText(R.string.project_transfer_length_outside_range)
        Text(stringResource(R.string.project_transfer_first_entry), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        JournalDetails(JournalPresentation(LocalContext.current).snapshotDetails(pending.snapshot))
        Button(
            enabled = status.readable && !status.busy,
            onClick = {
                val title = name.text.toString().trim()
                if (title.isEmpty()) invalidAttempts++ else onSave(title)
            },
            modifier = Modifier.fillMaxWidth().testTag("project-transfer-save-new"),
        ) { Text(stringResource(R.string.project_transfer_save_new)) }
    }
}
