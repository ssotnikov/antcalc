package local.antenna.calculator.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.journal.MeasurementDifference
import local.antenna.calculator.core.journal.compareMeasurements

@Composable
internal fun JournalComparisonScreen(
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
        ComparisonLegend()
        if (changes.isNotEmpty()) {
            JournalHeading(changeLabel, MaterialTheme.colorScheme.secondary)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((name, value) in changes) {
                        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
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

private fun comparisonHighlightColor(dark: Boolean, followUp: Boolean): Color = when {
    followUp -> if (dark) Color(0xFF344640) else Color(0xFFEAF3EB)
    else -> if (dark) Color(0xFF493D47) else Color(0xFFF9EBEE)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparisonLegend() {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((followUp, label) in listOf(false to R.string.journal_diff_before, true to R.string.journal_diff_after)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Box(Modifier.size(14.dp).background(comparisonHighlightColor(dark, followUp), RoundedCornerShape(3.dp)))
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
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
                Column(Modifier.height(IntrinsicSize.Min), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparisonHeading(beforeLabel, beforeTitle, colors.secondary,
                        Modifier.weight(1f).fillMaxWidth().testTag("journal-before-heading"))
                    ComparisonHeading(afterLabel, afterTitle, colors.primary,
                        Modifier.weight(1f).fillMaxWidth().testTag("journal-after-heading"))
                }
            } else {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparisonHeading(beforeLabel, beforeTitle, colors.secondary,
                        Modifier.weight(1f).fillMaxHeight().testTag("journal-before-heading"))
                    ComparisonHeading(afterLabel, afterTitle, colors.primary,
                        Modifier.weight(1f).fillMaxHeight().testTag("journal-after-heading"))
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
        else -> comparisonHighlightColor(dark, followUp)
    }
    val foreground = when {
        !highlighted -> colors.onSurface
        followUp -> if (dark) Color(0xFFDFEFE4) else Color(0xFF2C4B3B)
        else -> if (dark) Color(0xFFF1DEE5) else Color(0xFF653846)
    }
    val displayed = value ?: stringResource(R.string.journal_comparison_missing)
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
            JournalLinkedText(displayed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
