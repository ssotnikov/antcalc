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
import local.antenna.calculator.core.journal.AntennaProject
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
import local.antenna.calculator.journal.outputsForAntenna
import local.antenna.calculator.journal.sizingSnapshot
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.Section

private enum class TransferSource(val model: String, val title: Int, val frequencyKey: String) {
    SIZING("sizing", R.string.sizes, "frequency"),
    TRIMMING("trimming", R.string.trimming, "target"),
    INVERTED_V("inverted_v", R.string.inverted_v_title, "frequency"),
    GROUND_PLANE("ground_plane", R.string.ground_plane_title, "frequency"),
    J_POLE("j_pole", R.string.jpole_title, "frequency"),
    YAGI("yagi_r3udk", R.string.yagi_title, "frequency"),
    FULL_WAVE_LOOP("full_wave_loop", R.string.loop_title, "loop_frequency"),
}

internal class PendingProjectCalculation(
    val snapshot: CalculationSnapshot,
    private val projectId: UUID = UUID.randomUUID(),
    val recordId: UUID = UUID.randomUUID(),
    private val time: OffsetDateTime = OffsetDateTime.now(),
    private val note: String = "",
) {
    private val source = TransferSource.entries.first { it.model == snapshot.model }
    val needsAntennaChoice get() = source == TransferSource.SIZING
    val isInvertedV get() = source == TransferSource.INVERTED_V
    val isGroundPlane get() = source == TransferSource.GROUND_PLANE
    val isYagi get() = source == TransferSource.YAGI
    val isJPole get() = source == TransferSource.J_POLE
    val isFullWaveLoop get() = source == TransferSource.FULL_WAVE_LOOP
    val title get() = source.title
    val target = (if (isFullWaveLoop) snapshot.outputs else snapshot.inputs)
        .firstOrNull { it.key == source.frequencyKey }
        ?.let { if (isFullWaveLoop) it.value.roundToLong() else it.value.toLong() }
        ?.takeIf { it in Frequency.MIN_HERTZ..Frequency.MAX_HERTZ }
        ?.let(Frequency::ofHertz)
    val recommendedLengthNanometers get() = if (source == TransferSource.TRIMMING)
        outputLengthNanometers("new_length") else null

    fun initialLength(type: AntennaType): PhysicalLength? {
        val nanometers = when (source) {
            TransferSource.YAGI -> return null
            TransferSource.J_POLE -> outputLengthNanometers("jpole_long")
            TransferSource.GROUND_PLANE -> outputLengthNanometers("gp_whip")
            TransferSource.FULL_WAVE_LOOP -> outputLengthNanometers("loop_perimeter")
            TransferSource.INVERTED_V -> outputLengthNanometers("dipole_total")
            TransferSource.SIZING -> outputLengthNanometers(if (type == AntennaType.MONOPOLE) "monopole_length" else "dipole_total")
            TransferSource.TRIMMING -> snapshot.inputs.first { it.key == "length" }.value.toLong()
        }
        return nanometers.takeIf { it in PhysicalLength.MIN_NANOMETERS..PhysicalLength.MAX_NANOMETERS }
            ?.let(PhysicalLength::ofNanometers)
    }

    private fun outputLengthNanometers(key: String) =
        (snapshot.outputs.first { it.key == key }.value * 1_000_000_000).roundToLong()

    fun newProject(type: AntennaType, name: String) =
        AntennaProject(projectId, name, type, target, initialLength(type))

    fun record(type: AntennaType, title: String) =
        JournalRecord(recordId, title, time, type, note = note, calculation = snapshot.copy(outputs = snapshot.outputsForAntenna(type)))

    companion object {
        fun capture(state: CalculatorUiState, section: Section, type: AntennaType): PendingProjectCalculation? {
            val snapshot = if (section == Section.SIZING) sizingSnapshot(state) else journalSnapshot(state, type, section)
            return snapshot?.let { PendingProjectCalculation(it) }
        }

        val saver = Saver<PendingProjectCalculation?, Bundle>(
            save = { pending ->
                pending?.let {
                    Bundle().apply {
                        putByteArray("snapshot", JournalJson.encodePendingSnapshot(it.snapshot))
                        putString("note", it.note)
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
                        bundle.getString("note").orEmpty(),
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
        ChoicePicker(stringResource(R.string.antenna_type), AntennaType.straightConductors, type,
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
            val title = stringResource(pending.title)
            val summary = pending.target?.let {
                stringResource(R.string.journal_property, title, presentation.frequency(it.hertz))
            } ?: title
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            baseline?.let { Text(stringResource(R.string.journal_property, stringResource(R.string.project_transfer_initial_length),
                presentation.length(it.nanometers)), style = MaterialTheme.typography.bodyMedium) }
            pending.recommendedLengthNanometers?.let { length ->
                Text(stringResource(R.string.journal_property, stringResource(R.string.project_transfer_recommended_length),
                    presentation.length(length)), style = MaterialTheme.typography.bodyMedium)
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
        if (!pending.isYagi && pending.initialLength(type) == null) HelpText(R.string.project_transfer_length_outside_range)
        Text(stringResource(R.string.project_transfer_first_entry), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        JournalDetails(JournalPresentation(LocalContext.current).snapshotDetails(pending.snapshot, type))
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
