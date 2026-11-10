package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import local.antenna.calculator.R

@Composable
internal fun ToolsScreen(onOpenMatching: () -> Unit, onOpenCable: () -> Unit, onOpenJournal: (() -> Unit)? = null) {
    val scroll = rememberScrollState()
    val fits = scroll.maxValue == 0
    val limit = with(LocalDensity.current) { 32.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val displacement by animateFloatAsState(
        if (fits) pull else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 700f), label = "tools-pull")
    LaunchedEffect(fits, scroll.isScrollInProgress) {
        if (!fits || !scroll.isScrollInProgress) pull = 0f
    }
    val bounce = remember(scroll, limit) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (scroll.maxValue != 0 || source != NestedScrollSource.UserInput) return Offset.Zero
                pull = (pull + available.y * .3f).coerceIn(-limit, limit)
                return Offset(0f, available.y)
            }
        }
    }
    Box(Modifier.widthIn(max = 680.dp).fillMaxSize().clipToBounds().testTag("tools-viewport")
        .nestedScroll(bounce)) {
        Column(Modifier.fillMaxSize().offset { IntOffset(0, displacement.roundToInt()) }
            .verticalScroll(scroll).padding(20.dp).testTag("tools-cards"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ToolCard(R.string.matching_title, R.string.matching_description, "open-matching", onOpenMatching)
            ToolCard(R.string.cable_title, R.string.cable_description, "open-cable", onOpenCable)
            if (onOpenJournal != null) ToolCard(R.string.journal_title, R.string.journal_tools_hint, "open-journal", onOpenJournal)
        }
    }
}

@Composable
private fun ToolCard(title: Int, description: Int, tag: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
