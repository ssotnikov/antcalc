package local.antenna.calculator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.matching.matchingFromSwr
import local.antenna.calculator.data.CableCatalog

@Composable
internal fun SmithReferencePanels(palette: SmithPalette, format: (Double) -> String) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    SmithPanel {
        SmithHeading(R.string.smith_anatomy)
        val labels = listOf(R.string.smith_inductive, R.string.smith_capacitive, R.string.smith_short, R.string.smith_open,
            R.string.smith_inductor, R.string.smith_capacitor, R.string.smith_r_increases).map { stringResource(it) }
        SmithAnatomyGraphic(palette, stringResource(R.string.smith_anatomy_description), labels, format)
        SmithHint(R.string.smith_anatomy_axis)
        SmithHint(R.string.smith_anatomy_circles)
        SmithHint(R.string.smith_anatomy_arcs)
        Spacer(Modifier.height(4.dp))
        SmithHeading(R.string.smith_grid_details)
        SmithGridDetailsGraphic(
            palette,
            stringResource(R.string.smith_grid_details_description),
            listOf(stringResource(R.string.smith_inductive), stringResource(R.string.smith_capacitive)),
        )
        SmithHint(R.string.smith_grid_resistance)
        SmithHint(R.string.smith_grid_reactance)
        SmithHint(R.string.smith_grid_reference)
        SmithHint(R.string.smith_grid_details_note)
    }
    SmithPanel {
        val state = stringResource(if (expanded) R.string.smith_expanded else R.string.smith_collapsed)
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .testTag("smith-tables").semantics { stateDescription = state }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.smith_tables), Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium)
            Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.secondary)
        }
        if (expanded) {
            SmithHeading(R.string.smith_table_swr)
            SmithReferenceTable {
                SmithTableRow(listOf(stringResource(R.string.smith_swr), stringResource(R.string.smith_table_rl),
                    stringResource(R.string.smith_table_power)), header = true)
                for (swr in listOf(1.0, 1.1, 1.2, 1.5, 2.0, 2.5, 3.0, 5.0, 10.0)) {
                    val result = matchingFromSwr(swr)
                    SmithTableRow(listOf(format(swr), stringResource(R.string.matching_db, smithMeasure(context, result.returnLoss, 1)),
                        stringResource(R.string.matching_percent, format(result.reflectedFraction * 100))))
                }
            }
            Spacer(Modifier.height(5.dp))
            SmithHeading(R.string.smith_table_cables)
            SmithReferenceTable {
                SmithTableRow(listOf(stringResource(R.string.smith_cable), stringResource(R.string.smith_table_z0), "VF"), header = true)
                for (cable in CableCatalog.read(context).references) {
                    SmithTableRow(listOf(cable.name, cable.impedance.toString(), matchingNumber(context, cable.velocityFactor, 2)))
                }
            }
            SmithHint(R.string.smith_vf_note)
        }
    }
}

@Composable
private fun SmithReferenceTable(content: @Composable ColumnScope.() -> Unit) {
    val minimumWidth = 260.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tableWidth = maxOf(maxWidth, minimumWidth)
        ScrollableTable(Modifier.width(tableWidth), content = content)
    }
}

@Composable
private fun SmithTableRow(values: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.weight(if (index == 0) 1.2f else 1f),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                color = if (header) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End)
        }
    }
    HorizontalDivider()
}
