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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.UUID
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.state.CalculatorUiState
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
    onMessage: (String) -> Unit,
) {
    val state by model.state.collectAsState()
    var projectId by rememberSaveable { mutableStateOf(initialProjectId) }
    var recordId by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by rememberSaveable(stateSaver = listSaver<List<String>, String>(
        save = { it }, restore = { it.toList() },
    )) { mutableStateOf(emptyList()) }
    var projectToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRecordDeletion by rememberSaveable { mutableStateOf(false) }
    val project = state.projects.find { it.id.toString() == projectId }
    val record = project?.records?.find { it.id.toString() == recordId }
    val selected = project?.records.orEmpty().filter { it.id.toString() in picked }.sortedWith(journalChronology)
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
            ProjectAction.EXPORT -> model.prepareExport(target) {
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
                    project, presentation, picked, enabled,
                    onPick = { id ->
                        val key = id.toString()
                        picked = if (key in picked) picked - key else picked.takeLast(1) + key
                    },
                    onOpen = { id -> recordId = id.toString(); navigate(JournalPage.RECORD) },
                    onAdd = { navigate(JournalPage.ADD_RECORD) },
                    onCompare = { navigate(JournalPage.COMPARE) },
                    onAction = { onProjectAction(project, it) },
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
            JournalPage.COMPARE -> if (project != null && selected.size == 2) {
                CompareJournalRecords(project.title, selected[0], selected[1], presentation) { report ->
                    copy("$report\n\n$scopeNote")
                }
            } else MissingItem()
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
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
        AlertDialog(
            onDismissRequest = ::dismissDeletion,
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
                }, enabled = !state.busy) { Text(stringResource(R.string.journal_delete)) }
            },
            dismissButton = { TextButton(onClick = ::dismissDeletion) { Text(stringResource(R.string.journal_cancel)) } },
        )
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
    project: AntennaProject, presentation: JournalPresentation, picked: List<String>, enabled: Boolean,
    onPick: (UUID) -> Unit, onOpen: (UUID) -> Unit, onAdd: () -> Unit, onCompare: () -> Unit,
    onAction: (ProjectAction) -> Unit,
) {
    var actions by remember { mutableStateOf(false) }
    val records = remember(project.records) { project.records.sortedWith(journalChronology.reversed()) }
    val count = records.count { it.id.toString() in picked }
    val scroll = rememberLazyListState()
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
                OutlinedButton(onClick = onCompare, enabled = enabled && count == 2, modifier = Modifier.weight(1f).testTag("journal-compare")) {
                    Text(stringResource(R.string.journal_compare))
                }
            }
            if (records.size >= 2) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.journal_selected, count), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(R.string.journal_compare_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (records.isEmpty()) item { Text(stringResource(R.string.journal_no_records)) }
        items(records, key = { it.id.toString() }) { record ->
            val description = stringResource(R.string.journal_select, record.title)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = record.id.toString() in picked, onCheckedChange = { onPick(record.id) }, enabled = enabled,
                        modifier = Modifier.testTag("journal-select-record").semantics { contentDescription = description })
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
}

@Composable
private fun CompareJournalRecords(
    projectTitle: String, before: JournalRecord, after: JournalRecord, presentation: JournalPresentation,
    copy: (String) -> Unit,
) {
    val beforeLabel = stringResource(R.string.journal_before)
    val afterLabel = stringResource(R.string.journal_after)
    val beforeDetails = presentation.recordDetails(before)
    val afterDetails = presentation.recordDetails(after)
    val a = before.measurement
    val b = after.measurement
    val sameAntennaType = before.antennaType == after.antennaType
    val comparison = if (sameAntennaType && a != null && b != null) compareMeasurements(a, b) else null
    val changes = comparison?.let(presentation::changes).orEmpty()
    val notice = when {
        !sameAntennaType -> R.string.journal_compare_types
        a == null && b == null -> R.string.journal_compare_calculations
        comparison == null -> R.string.journal_compare_mixed
        MeasurementDifference.DIFFERENT_POINT in comparison.differences ||
            MeasurementDifference.UNKNOWN_POINT in comparison.differences -> R.string.journal_compare_points
        comparison.differences.isNotEmpty() &&
            (a?.swr != null || a?.resistanceOhms != null) && (b?.swr != null || b?.resistanceOhms != null) ->
            R.string.journal_not_comparable
        else -> null
    }
    val noticeLabel = stringResource(R.string.journal_compare_notice)
    val changeLabel = stringResource(R.string.journal_delta)
    val projectLabel = stringResource(R.string.journal_report_project)
    val warning = notice?.let { stringResource(it) }
    JournalColumn {
        JournalHeading(projectTitle, MaterialTheme.colorScheme.secondary)
        warning?.let { ComparisonNotice(it) }
        ComparisonFields(before.title, after.title, beforeDetails, afterDetails, beforeLabel, afterLabel)
        Text(stringResource(R.string.journal_diff_legend), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (changes.isNotEmpty()) {
            JournalHeading(changeLabel, MaterialTheme.colorScheme.secondary)
            JournalDetails(changes)
        }
        JournalScope()
        Button(onClick = {
            val sections = buildList {
                add(listOf(projectLabel to projectTitle))
                add(listOf(beforeLabel to before.title) + beforeDetails)
                add(listOf(afterLabel to after.title) + afterDetails +
                    changes.map { (name, value) -> "$changeLabel · $name" to value })
                warning?.let { add(listOf(noticeLabel to it)) }
            }
            copy(sections.joinToString("\n\n", transform = presentation::formatFields))
        }, modifier = Modifier.testTag("journal-copy-comparison")) { Text(stringResource(R.string.journal_copy)) }
    }
}

@Composable
private fun ComparisonNotice(message: String) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("journal-comparison-notice"),
        shape = MaterialTheme.shapes.small,
        color = if (dark) Color(0xFF332B20) else Color(0xFFFFF1D2),
        contentColor = if (dark) Color(0xFFF1CE86) else Color(0xFF684505),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.journal_compare_notice), style = MaterialTheme.typography.labelLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ComparisonFields(
    beforeTitle: String, afterTitle: String,
    beforeDetails: List<Pair<String, String>>, afterDetails: List<Pair<String, String>>,
    beforeLabel: String, afterLabel: String,
) {
    val beforeFields = beforeDetails.groupBy({ it.first }, { it.second })
    val afterFields = afterDetails.groupBy({ it.first }, { it.second })
    val dateLabel = stringResource(R.string.journal_date)
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stacked) {
                ComparisonHeading(beforeLabel, beforeTitle, colors.secondary, Modifier.fillMaxWidth())
                ComparisonHeading(afterLabel, afterTitle, colors.primary, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparisonHeading(beforeLabel, beforeTitle, colors.secondary, Modifier.weight(1f))
                    ComparisonHeading(afterLabel, afterTitle, colors.primary, Modifier.weight(1f))
                }
            }
            for (label in beforeFields.keys + afterFields.keys) {
                val oldValues = beforeFields[label].orEmpty()
                val newValues = afterFields[label].orEmpty()
                for (index in 0 until maxOf(oldValues.size, newValues.size)) {
                    val old = oldValues.getOrNull(index)
                    val new = newValues.getOrNull(index)
                    val changed = label != dateLabel && old != new
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.secondary)
                        if (stacked) {
                            ComparisonValue(old, changed, false, beforeLabel, Modifier.fillMaxWidth(), showRole = true)
                            ComparisonValue(new, changed, true, afterLabel, Modifier.fillMaxWidth(), showRole = true)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ComparisonValue(old, changed, false, beforeLabel, Modifier.weight(1f), showRole = false)
                                ComparisonValue(new, changed, true, afterLabel, Modifier.weight(1f), showRole = false)
                            }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeading(label: String, title: String, accent: Color, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = accent)
            Text(title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
        }
    }
}

@Composable
private fun ComparisonValue(
    value: String?, changed: Boolean, followUp: Boolean, role: String, modifier: Modifier, showRole: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val highlighted = changed && value != null
    val background = when {
        !highlighted -> Color.Transparent
        followUp -> if (dark) Color(0xFF344640) else Color(0xFFEAF3EB)
        else -> if (dark) Color(0xFF493D47) else Color(0xFFF9EBEE)
    }
    val foreground = when {
        !highlighted -> colors.onSurface
        followUp -> if (dark) Color(0xFFDFEFE4) else Color(0xFF2C4B3B)
        else -> if (dark) Color(0xFFF1DEE5) else Color(0xFF653846)
    }
    val displayed = when {
        value == null -> stringResource(R.string.journal_comparison_missing)
        !highlighted -> value
        followUp -> "+ $value"
        else -> "− $value"
    }
    Surface(
        modifier = modifier.testTag(if (highlighted) "journal-diff-value" else "journal-comparison-value"),
        shape = MaterialTheme.shapes.extraSmall,
        color = background,
        contentColor = foreground,
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                if (!showRole) contentDescription = role
            }) {
            if (showRole) Text(role, style = MaterialTheme.typography.labelSmall)
            Text(displayed, style = MaterialTheme.typography.bodyMedium)
        }
    }
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
private fun JournalHeading(text: String, color: Color = Color.Unspecified) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = color,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun JournalScope() {
    Text(stringResource(R.string.journal_scope), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MissingItem() {
    JournalColumn { Text(stringResource(R.string.journal_problem_missing)) }
}
