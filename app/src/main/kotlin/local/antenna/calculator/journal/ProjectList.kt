package local.antenna.calculator.journal

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.ui.CardReorderEdgeScroll
import local.antenna.calculator.ui.ProjectSearchField
import local.antenna.calculator.ui.inputExampleColor
import local.antenna.calculator.ui.cardListBounce
import local.antenna.calculator.ui.rememberPageLazyListState
import local.antenna.calculator.ui.reorderCardAt
import local.antenna.calculator.ui.reorderTarget

private data class ProjectCardText(val title: String, val summary: String)
private data class ProjectDrag(
    val id: UUID, val grabOffset: Float, val height: Int, val y: Float, val moved: Boolean = false,
)
private data class ProjectDrop(val drag: ProjectDrag, val target: UUID)

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
    var drop by remember(ids) { mutableStateOf<ProjectDrop?>(null) }
    val move by rememberUpdatedState(onReorder)
    val density = LocalDensity.current
    val margin = with(density) { 16.dp.toPx() }
    val cardSpacing = 12.dp
    val spacingPixels = with(density) { cardSpacing.roundToPx() }
    val moveUp = stringResource(R.string.journal_project_move_up)
    val moveDown = stringResource(R.string.journal_project_move_down)
    val actionsLabel = stringResource(R.string.journal_project_actions)
    val idByKey: Map<Any, UUID> = ids.associateBy(UUID::toString)
    val indexById = ids.withIndex().associate { (index, id) -> id to index }
    val positions = ids.withIndex().associate { (index, id) -> id.toString() to index }
    fun targetAt(dragged: ProjectDrag): UUID = idByKey.getValue(
        scroll.layoutInfo.reorderTarget(dragged.id.toString(), dragged.y - dragged.grabOffset, dragged.height, positions),
    )

    val insertionTarget by remember(ids, enabled) {
        derivedStateOf { drag?.takeIf { it.moved }?.let(::targetAt) }
    }
    val preview = drag?.takeIf { it.moved }?.let { ProjectDrop(it, insertionTarget ?: it.id) } ?: drop
    val previewFrom = preview?.let { indexById.getValue(it.drag.id) }
    val previewTo = preview?.let { indexById.getValue(it.target) }
    LaunchedEffect(drop, enabled) {
        if (drop != null && enabled) {
            delay(250)
            drop = null
        }
    }
    CardReorderEdgeScroll(scroll, drag?.id, ids) { drag?.takeIf { it.moved }?.y }

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
                        val item = layout.reorderCardAt(down.position.y, positions) ?: return@awaitEachGesture
                        val id = idByKey.getValue(item.key)
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val initial = ProjectDrag(id, y - item.offset, item.size, held.position.y)
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
                                    else {
                                        val target = targetAt(current)
                                        if (target != current.id) {
                                            drop = ProjectDrop(current, target)
                                            move(ids, current.id, target)
                                        }
                                    }
                                    break
                                }
                            }
                        } finally {
                            drag = null
                        }
                    }
                },
            state = scroll, overscrollEffect = null, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(cardSpacing),
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
                    Modifier.fillMaxWidth().testTag("journal-project-search"), maxLength = 32, clearable = true)
            }
            if (ids.size > 1) item("project-reorder-help") {
                Text(stringResource(R.string.journal_project_reorder_hint), style = MaterialTheme.typography.bodySmall,
                    color = inputExampleColor())
            }
            if (filtered.isEmpty()) item("project-empty") {
                Text(stringResource(if (projects.isEmpty()) R.string.journal_no_projects else R.string.journal_project_no_match))
            }
            items(filtered, key = { it.id.toString() }) { project ->
                val index = indexById.getValue(project.id)
                val shift = if (preview != null && previewFrom != null && previewTo != null) {
                    val distance = preview.drag.height + spacingPixels
                    when {
                        index > previewFrom && index <= previewTo -> -distance
                        index < previewFrom && index >= previewTo -> distance
                        else -> 0
                    }
                } else 0
                val offset = key(ids) { animateIntAsState(shift, tween(160), label = "project insertion gap") }
                val lifted = preview?.drag?.id == project.id
                Box(Modifier.animateItem(placementSpec = null).offset { IntOffset(0, offset.value) }) {
                    OutlinedCard(Modifier.fillMaxWidth().alpha(if (lifted) 0f else 1f)) {
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
        preview?.drag?.let { current ->
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
