package local.antenna.calculator.measurements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import local.antenna.calculator.ui.FrequencyNumberFormat
import local.antenna.calculator.ui.ScrollableTable
import java.text.NumberFormat
import java.util.Locale
import local.antenna.calculator.R
import local.antenna.calculator.core.measurements.S11Point

internal class MeasurementNumberFormat(locale: Locale) {
    private val two = decimalFormat(locale, 2)
    private val four = decimalFormat(locale, 4)
    private val frequency = FrequencyNumberFormat(locale)

    fun measurement(value: Double?): String = format(value, two)
    fun magnitude(value: Double): String = format(value, four)

    fun bandMegahertz(hertz: Double): String = frequency.estimatedMegahertz(hertz)
    fun megahertz(hertz: Double): String = frequency.megahertz(hertz)

    private fun format(value: Double?, formatter: NumberFormat): String = when {
        value == null || value.isNaN() -> "—"
        value == Double.POSITIVE_INFINITY -> "∞"
        value == Double.NEGATIVE_INFINITY -> "−∞"
        else -> formatter.format(value).let { if (it == formatter.format(-0.0)) formatter.format(0) else it }
    }

    private fun decimalFormat(locale: Locale, digits: Int) = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        isGroupingUsed = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeasurementSummaries(
    entries: List<Pair<Int, MeasurementTrace>>,
    selected: List<S11Point?>,
    format: MeasurementNumberFormat,
    frequencyText: (Double) -> String,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val minimumCardWidth = 154.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val cardWidth = if (entries.size == 2 && maxWidth >= minimumCardWidth * 2 + gap) (maxWidth - gap) / 2 else maxWidth
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            entries.forEachIndexed { index, (slot, trace) ->
                val point = selected[index]
                OutlinedCard(
                    modifier = Modifier.width(cardWidth).testTag("sweep-summary-$slot"),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(measurementTraceColor(slot), CircleShape))
                            Text(
                                "${stringResource(traceLabel(slot))} · Z₀ ${stringResource(R.string.sweep_ohms, format.measurement(trace.sweep.referenceOhms))}",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (point == null) {
                            Text(stringResource(R.string.sweep_outside), style = MaterialTheme.typography.bodySmall)
                        } else {
                            val impedance = point.impedance(trace.sweep.referenceOhms)
                            SummaryValue(stringResource(R.string.sweep_frequency), frequencyText(point.hertz))
                            SummaryValue(stringResource(R.string.sweep_swr), format.measurement(point.swr))
                            SummaryValue(stringResource(R.string.sweep_magnitude), format.magnitude(point.magnitude))
                            SummaryValue(stringResource(R.string.sweep_db), stringResource(R.string.sweep_decibels, format.measurement(point.decibels)))
                            SummaryValue(stringResource(R.string.sweep_phase), format.measurement(point.phaseDegrees) + "°")
                            SummaryValue("R", impedance?.first?.let { stringResource(R.string.sweep_ohms, format.measurement(it)) } ?: "—")
                            SummaryValue("X", impedance?.second?.let { stringResource(R.string.sweep_ohms, format.measurement(it)) } ?: "—")
                            if (point.isOpen) Text(stringResource(R.string.sweep_open_load), style = MaterialTheme.typography.bodySmall)
                            if (point.swr == null) Text(stringResource(R.string.sweep_nonpassive), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}

@Composable
internal fun MeasurementTable(
    points: List<S11Point>, reference: Double, selected: Int, format: MeasurementNumberFormat, onSelect: (Int) -> Unit,
) {
    val minimumWidth = 310.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tableWidth = maxOf(maxWidth, minimumWidth)
        OutlinedCard(Modifier.fillMaxWidth().testTag("sweep-table")) {
            ScrollableTable(Modifier.width(tableWidth)) {
                TableRow(listOf(
                    stringResource(R.string.sweep_frequency_column), stringResource(R.string.sweep_swr),
                    stringResource(R.string.sweep_magnitude), stringResource(R.string.sweep_r_column),
                    stringResource(R.string.sweep_x_column),
                ), header = true)
                points.forEachIndexed { index, point ->
                    HorizontalDivider()
                    val isSelected = index == selected
                    val impedance = point.impedance(reference)
                    TableRow(
                        listOf(
                            format.megahertz(point.hertz), format.measurement(point.swr), format.magnitude(point.magnitude),
                            format.measurement(impedance?.first), format.measurement(impedance?.second),
                        ),
                        modifier = Modifier
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                            .testTag("sweep-table-row-$index"),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(values: List<String>, modifier: Modifier = Modifier, header: Boolean = false) {
    Row(
        modifier.fillMaxWidth().heightIn(min = if (header) 0.dp else 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.weight(if (index == 0) 1.55f else 1f),
                color = if (header) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End)
        }
    }
}
