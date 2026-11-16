package local.antenna.calculator.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.ui.ProjectSearchField
import local.antenna.calculator.ui.cardListBounce
import local.antenna.calculator.ui.rememberPageLazyListState

private data class ProjectCardText(val title: String, val summary: String)
private data class ProjectDrag(val id: UUID, val grabOffset: Float, val y: Float, val moved: Boolean = false)

@Composable
internal fun ProjectList(
    projects: List<AntennaProject>, presentation: JournalPresentation, enabled: Boolean,
    onNew: () -> Unit, onOpen: (UUID) -> Unit, onImport: () -> Unit,
    onAction: (AntennaProject, ProjectAction) -> Unit,
    onReorder: (List<UUID>, UUID, UUID) -> Unit,
) {
    val scroll = rememberPageLazyListState()
    val resources = LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val cards = projects.associate { project ->
        val type = resources.getString(project.antennaType.journalLabel())
        val summary = project.target?.let {
            resources.getString(R.string.journal_project_summary, type, presentation.frequency(it.hertz))
        } ?: type
        project.id to ProjectCardText(project.title, summary)
    }
    val filtered = projects.filter {
        val text = cards.getValue(it.id)
        text.title.contains(search, ignoreCase = true) || text.summary.searchForm().contains(search.searchForm(), ignoreCase = true)
    }
    val ids = filtered.map { it.id }
    var menuId by remember { mutableStateOf<UUID?>(null) }
    var drag by remember(ids, enabled) { mutableStateOf<ProjectDrag?>(null) }
    val move by rememberUpdatedState(onReorder)
    val density = LocalDensity.current
    val margin = with(density) { 16.dp.toPx() }
    val scrollEdge = with(density) { 54.dp.toPx() }
    val scrollStep = with(density) { 12.dp.toPx() }
    val moveUp = stringResource(R.string.journal_project_move_up)
    val moveDown = stringResource(R.string.journal_project_move_down)
    val actionsLabel = stringResource(R.string.journal_project_actions)
    val idByKey: Map<Any, UUID> = ids.associateBy(UUID::toString)
    fun targetAt(y: Float): UUID? {
        val layout = scroll.layoutInfo
        val pointer = y + layout.viewportStartOffset
        return layout.visibleItemsInfo
            .filter { it.key in idByKey }
            .minByOrNull { abs(pointer - (it.offset + it.size / 2f)) }
            ?.let { idByKey[it.key] }
    }

    val draggingId = drag?.id
    LaunchedEffect(draggingId, ids) {
        if (draggingId == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val position = drag?.takeIf { it.moved }?.y ?: continue
            val height = scroll.layoutInfo.viewportSize.height
            val step = when {
                position < scrollEdge -> -scrollStep * ((scrollEdge - position) / scrollEdge).coerceIn(0f, 1f)
                position > height - scrollEdge -> scrollStep * ((position - height + scrollEdge) / scrollEdge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) scroll.scrollBy(step)
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            Modifier.fillMaxSize().cardListBounce(scroll.isScrollInProgress).testTag("journal-projects")
                .pointerInput(ids, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x !in margin..(size.width - margin)) return@awaitEachGesture
                        val layout = scroll.layoutInfo
                        val y = down.position.y + layout.viewportStartOffset
                        val item = layout.visibleItemsInfo.firstOrNull {
                            it.key in idByKey && y >= it.offset && y < it.offset + it.size
                        } ?: return@awaitEachGesture
                        val id = idByKey.getValue(item.key)
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val initial = ProjectDrag(id, y - item.offset, held.position.y)
                        drag = initial
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == held.id } ?: break
                                if (change.isConsumed) break
                                val changed = change.positionChanged()
                                change.consume()
                                if (changed) drag = initial.copy(y = change.position.y, moved = true)
                                if (!change.pressed) {
                                    val current = drag ?: break
                                    if (!current.moved) menuId = current.id
                                    else targetAt(current.y)?.takeIf { it != current.id }?.let { move(ids, current.id, it) }
                                    break
                                }
                            }
                        } finally {
                            drag = null
                        }
                    }
                },
            state = scroll, overscrollEffect = null, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("project-actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNew, enabled = enabled, modifier = Modifier.weight(1f).testTag("journal-new-project")) {
                        Text(stringResource(R.string.journal_new_project))
                    }
                    OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1f).testTag("journal-import")) {
                        Text(stringResource(R.string.journal_import))
                    }
                }
            }
            item("project-search") {
                ProjectSearchField(query, { query = it }, R.string.journal_project_search,
                    Modifier.fillMaxWidth().testTag("journal-project-search"))
            }
            if (ids.size > 1) item("project-reorder-help") {
                Text(stringResource(R.string.journal_project_reorder_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (filtered.isEmpty()) item("project-empty") {
                Text(stringResource(if (projects.isEmpty()) R.string.journal_no_projects else R.string.journal_project_no_match))
            }
            items(filtered, key = { it.id.toString() }) { project ->
                val index = ids.indexOf(project.id)
                val selected = drag?.takeIf { it.moved }?.let { targetAt(it.y) } == project.id && draggingId != project.id
                val faded = drag?.moved == true && draggingId == project.id
                val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else CardDefaults.outlinedCardBorder()
                Box(Modifier.animateItem()) {
                    OutlinedCard(Modifier.fillMaxWidth().alpha(if (faded) .3f else 1f), border = border) {
                        ProjectCardContent(project, cards.getValue(project.id), search,
                            Modifier.fillMaxWidth().testTag("journal-project")
                                .clickable(enabled = enabled) { onOpen(project.id) }
                                .semantics {
                                    if (enabled) {
                                        onLongClick(actionsLabel) { menuId = project.id; true }
                                        customActions = buildList {
                                            ids.getOrNull(index - 1)?.let { target ->
                                                add(CustomAccessibilityAction(moveUp) { move(ids, project.id, target); true })
                                            }
                                            ids.getOrNull(index + 1)?.let { target ->
                                                add(CustomAccessibilityAction(moveDown) { move(ids, project.id, target); true })
                                            }
                                        }
                                    }
                                })
                    }
                    ProjectActionsMenu(menuId == project.id && enabled, { menuId = null }) { onAction(project, it) }
                }
            }
        }
        drag?.takeIf { it.moved }?.let { current ->
            val project = projects.first { it.id == current.id }
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .offset { IntOffset(0, (current.y - current.grabOffset).roundToInt()) }
                .clearAndSetSemantics {}) {
                ProjectCardContent(project, cards.getValue(project.id), search, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun String.searchForm() = replace(',', '.')

private fun String.highlight(query: String, normalizeDecimal: Boolean = false): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(this)
    val searchable = if (normalizeDecimal) searchForm() else this
    val needle = if (normalizeDecimal) query.searchForm() else query
    return buildAnnotatedString {
        append(this@highlight)
        var start = searchable.indexOf(needle, ignoreCase = true)
        while (start >= 0) {
            val end = start + needle.length
            addStyle(SpanStyle(color = Color(0xFF2B2008), background = Color(0xFFFFDC73)), start, end)
            start = searchable.indexOf(needle, startIndex = end, ignoreCase = true)
        }
    }
}

@Composable
private fun ProjectCardContent(project: AntennaProject, text: ProjectCardText, search: String, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text.title.highlight(search), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text.summary.highlight(search, normalizeDecimal = true))
        Text(stringResource(R.string.journal_count, project.records.size), style = MaterialTheme.typography.bodySmall)
    }
}
