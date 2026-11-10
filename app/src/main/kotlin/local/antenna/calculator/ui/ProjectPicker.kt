package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.UUID
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.JournalLimits
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalPresentation
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.journal.label

@Composable
internal fun ProjectPicker(
    journal: JournalViewModel,
    antennaType: AntennaType,
    onSelect: (AntennaProject) -> Unit,
    targetHertz: Long?,
    onCreate: () -> Unit,
    header: @Composable () -> Unit,
) {
    val status by journal.state.collectAsState()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val unspecified = stringResource(R.string.project_transfer_no_frequency)
    val presentation = remember(context) { JournalPresentation(context) }
    var query by rememberSaveable(antennaType) { mutableStateOf("") }
    var selectedId by rememberSaveable(antennaType) { mutableStateOf<UUID?>(null) }
    val projects = status.projects.filter { it.antennaType == antennaType }
    val search = query.trim().replace(',', '.')
    val matches = projects.filter { project ->
        project.title.contains(query.trim(), ignoreCase = true) ||
            project.target?.let { frequency ->
                presentation.frequency(frequency.hertz).replace(',', '.').contains(search, ignoreCase = true)
            } == true
    }
    val selected = matches.firstOrNull { it.id == selectedId }

    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().padding(horizontal = 20.dp)
            .testTag("project-transfer-project-picker"),
    ) {
        header()
        OutlinedTextField(
            query, {
                if (it.length <= JournalLimits.TITLE) {
                    query = it
                    selectedId = null
                }
            },
            label = { Text(stringResource(R.string.project_transfer_search)) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("project-transfer-project-search"),
        )
        Text(
            stringResource(R.string.project_transfer_eligible),
            Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val scroll = rememberLazyListState()
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().cardListBounce(scroll.isScrollInProgress)
                .selectableGroup().testTag("project-transfer-project-list"),
            state = scroll, overscrollEffect = null,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            status.problem?.let { problem ->
                item {
                    HelpText(problem.label())
                    if (!status.readable) {
                        TextButton(onClick = journal::reload, enabled = !status.busy) {
                            Text(stringResource(R.string.journal_retry))
                        }
                    }
                }
            }
            if (status.readable && !status.busy && matches.isEmpty()) {
                item {
                    HelpText(if (projects.isEmpty()) R.string.project_transfer_no_projects else R.string.project_transfer_no_match)
                }
            }
            if (status.readable) items(matches, key = { it.id }) { project ->
                val isSelected = project.id == selectedId
                OutlinedCard(colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                )) {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(isSelected, enabled = !status.busy, role = Role.RadioButton, onClick = {
                                selectedId = project.id
                                focus.clearFocus()
                            })
                            .testTag("project-transfer-project").padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary)
                            Text(project.target?.let { presentation.frequency(it.hertz) } ?: unspecified,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        RadioButton(selected = isSelected, onClick = null, enabled = !status.busy)
                    }
                }
            }
        }
        if (targetHertz != null && selected?.target?.hertz?.let { it != targetHertz } == true) {
            Text(stringResource(R.string.project_transfer_frequency_mismatch, presentation.frequency(targetHertz)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 6.dp).testTag("project-transfer-frequency-warning"))
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Button(
                enabled = selected != null && status.readable && !status.busy,
                onClick = { selected?.let(onSelect) },
                modifier = Modifier.fillMaxWidth().testTag("project-transfer-confirm-project"),
            ) { Text(stringResource(R.string.project_transfer_save_existing)) }
            OutlinedButton(onClick = onCreate, enabled = status.readable && !status.busy,
                modifier = Modifier.fillMaxWidth().testTag("project-transfer-create")) {
                Text(stringResource(R.string.project_transfer_create))
            }
        }
    }
}
