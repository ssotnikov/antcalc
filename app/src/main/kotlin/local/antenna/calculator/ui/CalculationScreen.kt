package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.state.FieldKey
import local.antenna.calculator.state.Section
import local.antenna.calculator.state.validValue
import local.antenna.calculator.state.CalculatorEvent
import local.antenna.calculator.state.CalculatorUiState

@Composable
internal fun CalculationScreen(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    currentState: () -> CalculatorUiState,
    onLeaveInput: () -> Unit,
    onSaveToProject: (() -> Unit)? = null,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val appName = stringResource(R.string.app_name)
    val presentation = remember(state, configuration, context) { presentResults(context, state) }
    fun copyResult() {
        onLeaveInput()
        // Re-check the live state even if a click arrives before the next rendered frame.
        val text = presentResults(context, currentState()).copyText ?: return
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(appName, text))
        onCopied()
    }
    val settings = state.draft.settings
    val scroll = rememberPageScrollState()
    BoxWithConstraints(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp).testTag("calculator-scroll"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StorageNotice(state, onEvent)
            if (settings.section == Section.SIZING) {
                Image(
                    painterResource(R.drawable.sizing_antennas),
                    contentDescription = stringResource(R.string.sizing_antennas_description),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(MaterialTheme.shapes.large),
                )
            }
            val inputs: @Composable ColumnScope.() -> Unit = { CalculatorInputs(state, onEvent) }
            val results: @Composable ColumnScope.() -> Unit = {
                if (presentation.groups.isEmpty()) {
                    if (settings.section == Section.TRIMMING) {
                        val required = listOf(
                            FieldKey.ACTUAL_LENGTH to if (settings.antennaType == AntennaType.MONOPOLE)
                                R.string.actual_length else R.string.dipole_actual_length,
                            FieldKey.MEASURED to R.string.measured_frequency,
                            FieldKey.TARGET to R.string.target_frequency,
                        )
                        HelpText(R.string.trimming_pending_fields)
                        required.filter { (key, _) ->
                            val field = if (key == FieldKey.ACTUAL_LENGTH) state.trimming.actualLength else state.draft[key]
                            field.state(key).validValue() == null
                        }
                            .forEach { (_, label) -> Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) }
                    } else HelpText(R.string.result_pending)
                }
                presentation.groups.forEach { ResultPanel(it) }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (onSaveToProject != null) {
                        Button(
                            onClick = onSaveToProject,
                            enabled = state.editingEnabled && presentation.copyText != null,
                            modifier = Modifier.fillMaxWidth().testTag("copy-to-project"),
                        ) { Text(stringResource(R.string.project_transfer_title)) }
                    }
                    OutlinedButton(
                        onClick = ::copyResult,
                        enabled = presentation.copyText != null,
                        modifier = Modifier.fillMaxWidth().testTag("copy-result"),
                    ) { Text(stringResource(R.string.copy_result)) }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = inputs)
                    Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(20.dp), content = results)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = inputs)
                Column(verticalArrangement = Arrangement.spacedBy(20.dp), content = results)
            }
        }
    }
}

@Composable
private fun ResultPanel(group: ResultGroup) {
    val colors = MaterialTheme.colorScheme
    val valueColor = if (group.title == R.string.ideal_sizes) {
        if (colors.surface.luminance() < .5f) Color(0xFFF3BA80) else Color(0xFF824410)
    } else colors.primary
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                group.warning?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                group.rows.forEachIndexed { index, row ->
                    if (group.diagrams && row.label == R.string.radiator_length) {
                        DisableSelection { AntennaDiagram(dipole = false) }
                    }
                    if (group.diagrams && row.label == R.string.dipole_total) {
                        DisableSelection { AntennaDiagram(dipole = true) }
                    }
                    Column(Modifier.fillMaxWidth().testTag(row.tag).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(row.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.value, style = if (index == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            color = valueColor)
                    }
                    if (index != group.rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                group.notes.forEach {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.tuningDirection?.let { DisableSelection { TuningDiagram(it) } }
            }
        }
    }
}
