package local.antenna.calculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    antennaType: AntennaType?,
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
    val projects = status.projects.filter { antennaType == null || it.antennaType == antennaType }
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
        ProjectSearchField(
            query, {
                query = it
                selectedId = null
            },
            R.string.project_transfer_search,
            modifier = Modifier.fillMaxWidth().testTag("project-transfer-project-search"),
        )
        if (antennaType != null) Text(
            stringResource(R.string.project_transfer_eligible),
            Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val scroll = rememberPageLazyListState()
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
                    HelpText(when {
                        projects.isNotEmpty() -> R.string.project_transfer_no_match
                        antennaType == null -> R.string.journal_no_projects
                        else -> R.string.project_transfer_no_projects
                    })
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

@Composable
internal fun ProjectSearchField(
    query: String, onQueryChange: (String) -> Unit, @StringRes label: Int, modifier: Modifier = Modifier,
    maxLength: Int? = null,
    clearable: Boolean = true,
) {
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    val background = if (dark) Color(0xFF243342) else Color(0xFFE8EDF2)
    val muted = if (dark) Color(0xFFA5B1BD) else Color(0xFF626F7B)
    val hint = stringResource(label)
    BasicTextField(
        value = query,
        onValueChange = { value ->
            if (maxLength == null) {
                if (value.length <= JournalLimits.TITLE) onQueryChange(value)
            } else {
                val count = value.codePointCount(0, value.length).coerceAtMost(maxLength)
                onQueryChange(value.substring(0, value.offsetByCodePoints(0, count)))
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = hint },
        decorationBox = { input ->
            Row(
                Modifier.fillMaxWidth().background(background, RoundedCornerShape(28.dp))
                    .heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null,
                    modifier = Modifier.size(20.dp), tint = muted)
                Box(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (query.isEmpty()) Text(hint, color = muted,
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    input()
                }
                if (clearable && query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp).testTag("journal-clear-search")) {
                        Icon(painterResource(R.drawable.ic_close),
                            stringResource(R.string.journal_clear_search),
                            modifier = Modifier.size(20.dp), tint = muted)
                    }
                } else Spacer(Modifier.width(12.dp))
            }
        },
    )
}
