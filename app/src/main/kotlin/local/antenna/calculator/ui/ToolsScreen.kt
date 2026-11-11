package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

@Composable
internal fun ToolsScreen(
    onOpenMatching: () -> Unit,
    onOpenCable: () -> Unit,
    onOpenInvertedV: () -> Unit,
    onOpenGroundPlane: () -> Unit,
    onOpenJournal: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    Box(Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("tools-viewport")) {
        Column(Modifier.fillMaxSize().cardListBounce(scroll.isScrollInProgress)
            .verticalScroll(scroll, overscrollEffect = null).padding(20.dp).testTag("tools-cards"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onOpenJournal != null) ProjectsToolCard(onOpenJournal)
            ToolCard(R.string.matching_title, R.string.matching_description, "open-matching", onOpenMatching)
            ToolCard(R.string.cable_title, R.string.cable_description, "open-cable", onOpenCable)
            ToolCard(R.string.inverted_v_title, R.string.inverted_v_intro, "open-inverted-v", onOpenInvertedV)
            ToolCard(R.string.ground_plane_title, R.string.ground_plane_intro, "open-ground-plane", onOpenGroundPlane)
        }
    }
}

@Composable
private fun ProjectsToolCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(containerColor = lerp(colors.surface, colors.primary, 0.10f)),
        modifier = Modifier.fillMaxWidth().testTag("open-journal"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.titleLarge,
                    color = colors.secondary)
                Text(stringResource(R.string.journal_tools_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(painterResource(R.drawable.ic_chevron_end), contentDescription = null, tint = colors.onSurfaceVariant)
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
