package local.antenna.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

@Composable
internal fun ToolsScreen(
    onOpenMatching: () -> Unit,
    onOpenSmithChart: () -> Unit,
    onOpenCable: () -> Unit,
    onOpenAntennaCalculations: () -> Unit,
    onOpenJournal: (() -> Unit)? = null,
    onOpenFeedline: (() -> Unit)? = null,
    onOpenAdditional: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    Box(Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("tools-viewport")) {
        Column(Modifier.fillMaxSize().cardListBounce(scroll.isScrollInProgress)
            .verticalScroll(scroll, overscrollEffect = null).padding(20.dp).testTag("tools-cards"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onOpenJournal != null) ProjectsToolCard(onOpenJournal)
            ToolCard(R.string.matching_title, R.string.matching_description, "open-matching", onOpenMatching)
            ToolCard(R.string.smith_title, R.string.smith_description, "open-smith-chart", onOpenSmithChart)
            ToolCard(R.string.cable_title, R.string.cable_description, "open-cable", onOpenCable)
            if (onOpenFeedline != null) ToolCard(R.string.feedline_title, R.string.feedline_intro, "open-feedline", onOpenFeedline)
            ToolCard(R.string.antenna_parameter_tools, R.string.antenna_parameter_tools_hint,
                "open-antenna-parameter-tools", onOpenAntennaCalculations, showChevron = true)
            if (onOpenAdditional != null) ToolCard(R.string.additional_tools, R.string.bandwidth_additional_tools,
                "open-additional-tools", onOpenAdditional, showChevron = true, highlighted = true)
        }
    }
}

@Composable
internal fun AntennaParameterToolsScreen(onOpenInvertedV: () -> Unit, onOpenGroundPlane: () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().cardListBounce(scroll.isScrollInProgress)
            .verticalScroll(scroll, overscrollEffect = null).padding(20.dp).testTag("antenna-parameter-tools"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolCard(R.string.inverted_v_title, R.string.inverted_v_intro, "open-inverted-v", onOpenInvertedV, showChevron = true)
        ToolCard(R.string.ground_plane_title, R.string.ground_plane_intro, "open-ground-plane", onOpenGroundPlane, showChevron = true)
    }
}

@Composable
internal fun AdditionalToolsScreen(onOpenMeasurements: () -> Unit, onOpenBandwidth: () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().cardListBounce(scroll.isScrollInProgress)
            .verticalScroll(scroll, overscrollEffect = null).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolCard(R.string.sweep_tool_title, R.string.sweep_tool_description, "open-measurements", onOpenMeasurements, showChevron = true)
        ToolCard(R.string.bandwidth_title, R.string.bandwidth_description, "open-bandwidth", onOpenBandwidth, showChevron = true)
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
private fun ToolCard(
    title: Int, description: Int, tag: String, onClick: () -> Unit,
    showChevron: Boolean = false, highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = if (highlighted) CardDefaults.outlinedCardColors(
            containerColor = if (dark) Color(0xFF203D4E) else Color(0xFFDCECF4),
        ) else CardDefaults.outlinedCardColors(),
        border = if (highlighted) BorderStroke(1.dp, if (dark) Color(0xFF5A849A) else Color(0xFF6B92A5))
            else CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = colors.secondary)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
            if (showChevron) Icon(painterResource(R.drawable.ic_chevron_end), contentDescription = null,
                tint = colors.onSurfaceVariant)
        }
    }
}
