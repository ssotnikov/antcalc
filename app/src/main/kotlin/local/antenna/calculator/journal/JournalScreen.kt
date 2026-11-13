package local.antenna.calculator.journal

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.UUID
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.ui.AppAlertDialog
import local.antenna.calculator.ui.cardListBounce

internal enum class JournalPage(val title: Int) {
    PROJECTS(R.string.journal_projects),
    HISTORY(R.string.journal_history),
    CREATE(R.string.journal_new_project),
    EDIT(R.string.journal_edit_project),
    RECORD(R.string.journal_record),
    ADD_RECORD(R.string.journal_new_record),
    COMPARE(R.string.journal_compare),
    ;

    fun parent(): JournalPage? = when (this) {
        PROJECTS -> null
        HISTORY, CREATE -> PROJECTS
        EDIT, RECORD, ADD_RECORD, COMPARE -> HISTORY
    }
}

private enum class ProjectAction(val label: Int) {
    EDIT(R.string.journal_edit_project),
    DUPLICATE(R.string.journal_duplicate),
    EXPORT(R.string.journal_export),
    DELETE(R.string.journal_delete),
}

@Composable
internal fun JournalScreen(
    model: JournalViewModel,
    page: JournalPage,
    navigate: (JournalPage) -> Unit,
    calculator: CalculatorUiState,
    currentCalculator: () -> CalculatorUiState,
    initialProjectId: String? = null,
    onOpenSweeps: ((SweepComparison) -> Unit)? = null,
    onMessage: (String) -> Unit,
) {
    val state by model.state.collectAsState()
    var projectId by rememberSaveable { mutableStateOf(initialProjectId) }
    val historyScroll = key(projectId) { rememberLazyListState() }
    var recordId by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by rememberSaveable(stateSaver = listSaver<List<String>, String>(
        save = { it }, restore = { it.toList() },
    )) { mutableStateOf(emptyList()) }
    var rejectedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rejectedKey, page, projectId) {
        if (rejectedKey != null && page == JournalPage.HISTORY) delay(850)
        rejectedKey = null
    }
    var projectToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSweepExport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRecordDeletion by rememberSaveable { mutableStateOf(false) }
    val project = state.projects.find { it.id.toString() == projectId }
    val record = project?.records?.find { it.id.toString() == recordId }
    val selected = project?.records.orEmpty().filter { it.id.toString() in picked }.sortedWith(journalChronology)
    val selectedSweep = project?.sweeps?.firstOrNull { it.selectionKey() in picked }
    val canCompare = project != null && picked.size == 2 &&
        (selected.size == 2 || (selected.size == 1 && selectedSweep != null)) &&
        project.comparisonRejection(picked) == null
    val context = LocalContext.current
    val presentation = JournalPresentation(context)
    val resources = LocalResources.current
    val scopeNote = stringResource(R.string.journal_scope)
    val appName = stringResource(R.string.app_name)
    val copied = stringResource(R.string.copied)
    val exported = stringResource(R.string.journal_exported)

    fun openProject(id: UUID) {
        projectId = id.toString()
        picked = emptyList()
        navigate(JournalPage.HISTORY)
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(appName, text))
        onMessage(copied)
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.previewImport(context.contentResolver, uri)
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) model.cancelExport() else model.writeExport(context.contentResolver, uri) { onMessage(exported) }
    }

    fun export(target: AntennaProject) {
        model.prepareExport(target) {
            try {
                createDocument.launch("antcalc-project.json")
            } catch (_: ActivityNotFoundException) {
                model.cancelExport()
                model.filePickerUnavailable()
            } catch (_: SecurityException) {
                model.cancelExport()
                model.filePickerUnavailable()
            }
        }
    }

    state.projects.find { it.id.toString() == pendingSweepExport }?.let { target ->
        AppAlertDialog(
            onDismissRequest = { pendingSweepExport = null },
            title = { Text(stringResource(R.string.journal_export)) },
            text = { Text(stringResource(R.string.sweep_export_notice)) },
            confirmButton = {
                TextButton(onClick = { pendingSweepExport = null; export(target) }) { Text(stringResource(R.string.journal_export)) }
            },
            dismissButton = { TextButton(onClick = { pendingSweepExport = null }) { Text(stringResource(R.string.sweep_cancel)) } },
        )
    }

    fun onProjectAction(target: AntennaProject, action: ProjectAction) {
        when (action) {
            ProjectAction.EDIT -> {
                if (projectId != target.id.toString()) picked = emptyList()
                projectId = target.id.toString()
                navigate(JournalPage.EDIT)
            }
            ProjectAction.DUPLICATE -> {
                val shortTitle = target.title.take(105).trimEnd().dropLastWhile(Char::isHighSurrogate)
                model.duplicate(target.id, resources.getString(R.string.journal_copy_name, shortTitle), ::openProject)
            }
            ProjectAction.EXPORT -> if (target.sweeps.isEmpty()) export(target)
                else pendingSweepExport = target.id.toString()
            ProjectAction.DELETE -> projectToDelete = target.id.toString()
        }
    }

    if (state.busy && !state.readable) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.journal_loading))
            }
        }
        return
    }

    if (!state.readable) {
        JournalColumn {
            Text(stringResource((state.problem ?: JournalProblem.READ).label()), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = model::reload, enabled = !state.busy) { Text(stringResource(R.string.journal_retry)) }
        }
        return
    }

    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.problem?.let { problem ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(problem.label()), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = model::dismissProblem) { Text(stringResource(R.string.journal_close)) }
                }
            }
        }
        val enabled = !state.busy
        when (page) {
            JournalPage.PROJECTS -> ProjectList(state.projects, presentation, enabled,
                onNew = { navigate(JournalPage.CREATE) }, onOpen = ::openProject, onAction = ::onProjectAction, onImport = {
                    try {
                        openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    } catch (_: ActivityNotFoundException) {
                        model.filePickerUnavailable()
                    } catch (_: SecurityException) {
                        model.filePickerUnavailable()
                    }
                })
            JournalPage.CREATE -> ProjectForm(null, enabled) {
                model.saveProject(it, create = true, done = ::openProject)
            }
            JournalPage.EDIT -> if (project != null) {
                ProjectForm(project, enabled) { model.saveProject(it, create = false) { navigate(JournalPage.HISTORY) } }
            } else MissingItem()
            JournalPage.HISTORY -> if (project != null) {
                ProjectHistory(
                    project, presentation, picked, enabled, canCompare, rejectedKey, scroll = historyScroll,
                    onPick = { key ->
                        val proposed = if (key in picked) picked - key else picked.takeLast(1) + key
                        val rejection = project.comparisonRejection(proposed)
                        if (rejection == null) {
                            rejectedKey = null
                            picked = proposed
                        } else if (rejectedKey != key) {
                            rejectedKey = key
                            onMessage(resources.getString(rejection))
                        }
                    },
                    onOpen = { id -> recordId = id.toString(); navigate(JournalPage.RECORD) },
                    onAdd = { navigate(JournalPage.ADD_RECORD) },
                    onCompare = { navigate(JournalPage.COMPARE) },
                    onAction = { onProjectAction(project, it) },
                    onOpenSweeps = onOpenSweeps,
                    onRemoveSweeps = {
                        picked = picked - it.selectionKey()
                        model.removeSweeps(project.id, it)
                    },
                )
            } else MissingItem()
            JournalPage.ADD_RECORD -> if (project != null) {
                RecordForm(project, calculator, currentCalculator, enabled) { entry ->
                    model.addRecord(project.id, entry) {
                        recordId = entry.id.toString()
                        navigate(JournalPage.RECORD)
                    }
                }
            } else MissingItem()
            JournalPage.RECORD -> if (record != null) {
                val details = presentation.recordDetails(record)
                JournalColumn(Modifier.testTag("journal-record-details")) {
                    JournalHeading(record.title)
                    JournalDetails(details)
                    JournalScope()
                    OutlinedButton(onClick = {
                        copy(listOf(record.title, presentation.formatFields(details), scopeNote).joinToString("\n"))
                    }, enabled = enabled) { Text(stringResource(R.string.journal_copy)) }
                    TextButton(onClick = { confirmRecordDeletion = true }, enabled = enabled) { Text(stringResource(R.string.journal_delete)) }
                }
            } else MissingItem()
            JournalPage.COMPARE -> if (project != null && canCompare) {
                val copyComparison: (String) -> Unit = { report -> copy("$report\n\n$scopeNote") }
                when {
                    selectedSweep != null && selected.size == 1 -> JournalSweepComparisonScreen(
                        project.title, selected.single(), selectedSweep, presentation, model::readSweep, copyComparison,
                    )
                    selected.count { it.measurement != null } == 1 -> JournalCalculationComparisonScreen(
                        project.title, selected.first { it.calculation != null },
                        selected.first { it.measurement != null }, presentation, copyComparison,
                    )
                    else -> JournalComparisonScreen(project.title, selected[0], selected[1], presentation, copyComparison)
                }
            } else MissingItem()
        }
    }

    state.preview?.let { preview ->
        AppAlertDialog(
            onDismissRequest = { if (!state.busy) model.clearPreview() },
            title = { Text(stringResource(R.string.journal_preview)) },
            text = {
                Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JournalHeading(preview.title)
                    JournalDetails(presentation.projectDetails(preview))
                    Text(stringResource(R.string.journal_count, preview.records.size))
                    Text(stringResource(R.string.journal_import_note))
                    state.problem?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { model.importCopy(::openProject) }, enabled = !state.busy) { Text(stringResource(R.string.journal_import_copy)) } },
            dismissButton = { TextButton(onClick = model::clearPreview, enabled = !state.busy) { Text(stringResource(R.string.journal_cancel)) } },
        )
    }

    val deleteProjectId = projectToDelete?.let(UUID::fromString)
    if (deleteProjectId != null || confirmRecordDeletion) {
        fun dismissDeletion() {
            projectToDelete = null
            confirmRecordDeletion = false
        }
        AppAlertDialog(
            onDismissRequest = ::dismissDeletion,
            title = {
                Text(stringResource(
                    if (deleteProjectId != null) R.string.journal_delete_project_title else R.string.journal_delete_record_title,
                ))
            },
            text = {
                Text(stringResource(if (deleteProjectId != null) R.string.journal_delete_project else R.string.journal_delete_record))
            },
            confirmButton = {
                TextButton(onClick = {
                    dismissDeletion()
                    if (deleteProjectId != null) {
                        model.removeProject(deleteProjectId) { navigate(JournalPage.PROJECTS) }
                    } else if (project != null && record != null) {
                        model.removeRecord(project.id, record.id) {
                            picked = picked - record.id.toString()
                            navigate(JournalPage.HISTORY)
                        }
                    }
                }, enabled = !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.journal_delete))
                }
            },
            dismissButton = { TextButton(onClick = ::dismissDeletion) { Text(stringResource(R.string.journal_cancel)) } },
        )
    }
}

private fun SweepComparison.selectionKey() = traces.joinToString(separator = ":", prefix = "sweep:") { it.id }

private fun AntennaProject.comparisonRejection(keys: List<String>): Int? {
    if (keys.size != 2) return null
    val records = records.filter { it.id.toString() in keys }
    val sweepCount = keys.size - records.size
    if (sweepCount == 2) return R.string.journal_reject_two_sweeps
    if (sweepCount == 1) {
        return R.string.journal_sweep_compare_select_manual.takeIf { records.single().measurement == null }
    }
    if (records[0].antennaType != records[1].antennaType) return R.string.journal_reject_antenna_types
    val calculations = records.filter { it.calculation != null }
    if (calculations.isEmpty()) return null
    if (calculations.size == 1) {
        val calculation = calculations.single()
        val measurement = records.first { it.measurement != null }
        return R.string.journal_reject_geometry.takeIf { compareSizingLength(calculation, measurement) == null }
    }
    val first = checkNotNull(calculations[0].calculation)
    val second = checkNotNull(calculations[1].calculation)
    return when {
        first.model != second.model -> R.string.journal_reject_calculation_models
        first.revision != second.revision -> R.string.journal_reject_calculation_revisions
        first.outputs.none { a -> second.outputs.any { b -> a.key == b.key && a.unit == b.unit } } ->
            R.string.journal_reject_geometry
        else -> null
    }
}

private val journalChronology = compareBy<JournalRecord> { it.time.toInstant() }

@Composable
private fun ProjectList(
    projects: List<AntennaProject>, presentation: JournalPresentation, enabled: Boolean,
    onNew: () -> Unit, onOpen: (UUID) -> Unit, onImport: () -> Unit,
    onAction: (AntennaProject, ProjectAction) -> Unit,
) {
    val scroll = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().cardListBounce(scroll.isScrollInProgress).testTag("journal-projects"),
        state = scroll, overscrollEffect = null, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNew, enabled = enabled, modifier = Modifier.weight(1f).testTag("journal-new-project")) {
                    Text(stringResource(R.string.journal_new_project))
                }
                OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1f).testTag("journal-import")) {
                    Text(stringResource(R.string.journal_import))
                }
            }
        }
        if (projects.isEmpty()) item { Text(stringResource(R.string.journal_no_projects)) }
        items(projects, key = { it.id.toString() }) { project ->
            var actions by remember { mutableStateOf(false) }
            val actionsLabel = stringResource(R.string.journal_project_actions)
            Box {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().testTag("journal-project")
                            .combinedClickable(
                                enabled = enabled,
                                onLongClickLabel = actionsLabel,
                                onLongClick = { actions = true },
                                onClick = { onOpen(project.id) },
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        JournalHeading(project.title, MaterialTheme.colorScheme.secondary)
                        val antenna = stringResource(project.antennaType.journalLabel())
                        Text(project.target?.let {
                            stringResource(R.string.journal_project_summary, antenna, presentation.frequency(it.hertz))
                        } ?: antenna)
                        Text(stringResource(R.string.journal_count, project.records.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
                ProjectActionsMenu(actions && enabled, { actions = false }) { onAction(project, it) }
            }
        }
    }
}

@Composable
private fun ProjectActionsMenu(expanded: Boolean, onDismiss: () -> Unit, onAction: (ProjectAction) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ProjectAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.label)) },
                onClick = { onDismiss(); onAction(action) },
            )
        }
    }
}

@Composable
private fun ProjectHistory(
    project: AntennaProject, presentation: JournalPresentation, picked: List<String>, enabled: Boolean, canCompare: Boolean,
    rejectedKey: String?,
    scroll: LazyListState,
    onPick: (String) -> Unit, onOpen: (UUID) -> Unit, onAdd: () -> Unit, onCompare: () -> Unit,
    onAction: (ProjectAction) -> Unit,
    onOpenSweeps: ((SweepComparison) -> Unit)?,
    onRemoveSweeps: (SweepComparison) -> Unit,
) {
    var actions by remember { mutableStateOf(false) }
    val records = remember(project.records) { project.records.sortedWith(journalChronology.reversed()) }
    val sweeps = project.sweeps.asReversed().distinctBy { it.selectionKey() }
    val count = records.count { it.id.toString() in picked } + sweeps.count { it.selectionKey() in picked }
    val recordGroups = records.groupBy { it.measurement != null }
    fun LazyListScope.recordGroup(isMeasurement: Boolean) {
        val group = recordGroups[isMeasurement] ?: return
        item {
            JournalHeading(stringResource(if (isMeasurement) R.string.journal_manual_records else R.string.journal_saved_calculations),
                MaterialTheme.colorScheme.secondary)
        }
        items(group, key = { it.id.toString() }) { record ->
            val description = stringResource(R.string.journal_select, record.title)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val key = record.id.toString()
                    JournalSelectionCheckbox(key in picked, rejectedKey == key, enabled, description, "journal-select-record") {
                        onPick(key)
                    }
                    TextButton(
                        onClick = { onOpen(record.id) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(record.title, style = MaterialTheme.typography.titleMedium,
                                color = if (enabled) MaterialTheme.colorScheme.secondary else LocalContentColor.current)
                            Text(presentation.date(record.time), style = MaterialTheme.typography.bodySmall)
                            record.measurement?.let { m ->
                                val summary = listOfNotNull(m.resonance?.let { presentation.frequency(it.hertz) },
                                    m.swr?.let { stringResource(R.string.matching_swr) + " " + presentation.number(it) })
                                if (summary.isNotEmpty()) Text(summary.joinToString(" · "))
                                Text(stringResource(m.point.journalLabel()), style = MaterialTheme.typography.bodySmall)
                            }
                            if (record.calculation != null) Text(stringResource(R.string.journal_calculation))
                            if (record.note.isNotBlank()) Text(record.note, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().cardListBounce(scroll.isScrollInProgress).testTag("journal-history"),
        state = scroll, overscrollEffect = null, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    JournalHeading(project.title, MaterialTheme.colorScheme.secondary)
                }
                Box {
                    IconButton(onClick = { actions = true }, enabled = enabled, modifier = Modifier.testTag("journal-project-actions")) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_menu), stringResource(R.string.journal_project_actions))
                    }
                    ProjectActionsMenu(actions && enabled, { actions = false }, onAction)
                }
            }
            JournalAntennaIllustration(project.antennaType, Modifier.padding(vertical = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((label, value) in presentation.projectDetails(project, includeNote = false)) {
                    Text(stringResource(R.string.journal_property, label, value),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (project.note.isNotBlank()) {
                Surface(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.journal_note), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary)
                        Text(project.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, enabled = enabled, modifier = Modifier.weight(1f).testTag("journal-add")) {
                    Text(stringResource(R.string.journal_add_record))
                }
                OutlinedButton(onClick = onCompare, enabled = enabled && canCompare, modifier = Modifier.weight(1f).testTag("journal-compare")) {
                    Text(stringResource(R.string.journal_compare))
                }
            }
            if (records.size + sweeps.size >= 2) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.journal_selected, count), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                    val help = if (sweeps.isNotEmpty()) R.string.journal_sweep_compare_help else R.string.journal_compare_help
                    Text(stringResource(help), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        recordGroup(true)
        if (project.sweeps.isNotEmpty()) {
            item { JournalHeading(stringResource(R.string.sweep_saved_traces), MaterialTheme.colorScheme.secondary) }
            items(sweeps, key = { it.selectionKey() }) { sweep ->
                val description = stringResource(R.string.journal_select, sweep.traces.joinToString { it.name })
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val key = sweep.selectionKey()
                            JournalSelectionCheckbox(key in picked, rejectedKey == key, enabled, description, "journal-select-sweep") {
                                onPick(key)
                            }
                            Column(Modifier.weight(1f)) { sweep.traces.forEach { Text(it.name) } }
                        }
                        Row {
                            if (onOpenSweeps != null) TextButton(enabled = enabled, onClick = { onOpenSweeps(sweep) }) {
                                Text(stringResource(R.string.sweep_open_saved))
                            }
                            var confirm by remember { mutableStateOf(false) }
                            TextButton(enabled = enabled, onClick = { confirm = true }) { Text(stringResource(R.string.sweep_remove)) }
                            if (confirm) AppAlertDialog(
                                onDismissRequest = { confirm = false },
                                title = { Text(stringResource(R.string.sweep_remove_saved)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = { confirm = false; onRemoveSweeps(sweep) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) {
                                        Text(stringResource(R.string.sweep_remove))
                                    }
                                },
                                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.sweep_cancel)) } },
                            )
                        }
                    }
                }
            }
        }
        if (records.isEmpty() && sweeps.isEmpty()) item { Text(stringResource(R.string.journal_no_records)) }
        recordGroup(false)
    }
}

@Composable
private fun JournalSelectionCheckbox(
    checked: Boolean, rejected: Boolean, enabled: Boolean, description: String, tag: String, onPick: () -> Unit,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = { onPick() },
        enabled = enabled,
        colors = if (rejected) CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.error)
            else CheckboxDefaults.colors(),
        modifier = Modifier.testTag(tag).semantics { contentDescription = description },
    )
}

@Composable
internal fun JournalColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun JournalDetails(details: List<Pair<String, String>>) {
    for ((label, value) in details) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun JournalHeading(text: String, color: Color = Color.Unspecified) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = color,
        modifier = Modifier.semantics { heading() })
}

@Composable
internal fun JournalScope() {
    Text(stringResource(R.string.journal_scope), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MissingItem() {
    JournalColumn { Text(stringResource(R.string.journal_problem_missing)) }
}
