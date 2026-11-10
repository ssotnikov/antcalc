package local.antenna.calculator.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.journal.invertedVSnapshot
import local.antenna.calculator.state.CalculatorUiState

@Composable
internal fun InvertedVProjectPicker(
    journal: JournalViewModel,
    currentState: () -> CalculatorUiState,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val title = stringResource(R.string.inverted_v_title)
    val savedMessage = stringResource(R.string.inverted_v_saved)
    ProjectPicker(journal, AntennaType.SYMMETRIC_DIPOLE, "inverted-v",
        R.string.inverted_v_project_eligible, R.string.inverted_v_no_projects, onCancel,
        onSelect = { project ->
            val snapshot = currentState().draft.invertedV.invertedVSnapshot()
            if (snapshot != null) {
                journal.addRecord(project.id, JournalRecord(
                    id = UUID.randomUUID(),
                    title = title,
                    time = OffsetDateTime.now(),
                    antennaType = project.antennaType,
                    installation = project.installation,
                    feedline = project.feedline,
                    calculation = snapshot,
                )) { onSaved(savedMessage) }
            }
        })
}
