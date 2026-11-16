package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit

@Composable
internal fun HaloCbScreen(
    initial: CalculationSnapshot?,
    onMeasurements: (() -> Unit)?,
    onBandwidth: (() -> Unit)?,
    onSave: ((CalculationSnapshot) -> Unit)?,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = resources.configuration.locales[0]
    val decimal = remember(locale) { NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false } }
    fun format(value: Double, digits: Int, fixed: Boolean = false): String {
        decimal.minimumFractionDigits = if (fixed) digits else 0
        decimal.maximumFractionDigits = digits
        return decimal.format(value)
    }
    var draft by rememberSaveable(stateSaver = HaloDraft.saver) {
        mutableStateOf(initial?.let(HaloDraft::fromSnapshot) ?: HaloDraft(frequency = format(27.185, 3, true), factor = format(1.02, 2)))
    }
    val geometry = draft.geometry
    val gammaErrors = draft.gammaErrors()
    val snapshot = draft.snapshot()
    val variant = draft.variant
    val title = stringResource(variant.title)
    val vector = remember(variant) { haloVector(variant) }
    val metres = stringResource(R.string.m)
    fun label(key: String): String = resources.getString(checkNotNull(haloMetricLabel(key)))
    fun report(result: CalculationSnapshot): String = buildString {
        appendLine("Halo CB · $title")
        appendLine(resources.getString(R.string.halo_estimate))
        appendLine("${resources.getString(R.string.halo_frequency)} · ${format(checkNotNull(draft.hertz) / 1e6, 6)}")
        for (metric in result.inputs.drop(2) + result.outputs) {
            val shown = when (metric.unit) {
                SnapshotUnit.METERS -> if (HaloGammaField.entries.any { it.key == metric.key } || metric.key == "halo_gap") {
                    "${format(metric.value * 1000, 9)} ${resources.getString(R.string.hb9cv_aerial_mm)}"
                } else "${format(metric.value, 9)} $metres"
                SnapshotUnit.PICOFARADS -> "${format(metric.value, 9)} ${resources.getString(R.string.halo_pf)}"
                else -> format(metric.value, 9)
            }
            appendLine("${label(metric.key)} · $shown")
        }
        append(resources.getString(R.string.halo_factor_help))
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberPageScrollState()).padding(20.dp).testTag("halo-cb-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RotatingAntenna("halo-${variant.name}", title, Modifier.testTag("halo-model"), geometry = vector)
        var choicesOpen by remember { mutableStateOf(false) }
        HaloHeading(R.string.halo_variant)
        Box {
            OutlinedButton({ choicesOpen = true }, Modifier.fillMaxWidth().testTag("halo-variant")) {
                Text(title, Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(choicesOpen, { choicesOpen = false }) {
                HaloVariant.entries.forEach { choice ->
                    DropdownMenuItem(
                        { Text(stringResource(choice.title)) },
                        { choicesOpen = false; draft = draft.copy(variant = choice) },
                        Modifier.testTag("halo-variant-${choice.name}"),
                    )
                }
            }
        }
        if (variant == HaloVariant.CSQ11) {
            HaloHistory()
        } else {
            HaloSectionSurface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloHeading(R.string.halo_inputs)
                    HaloField(draft.frequency, R.string.halo_frequency, draft.hertz == null,
                        "halo-frequency", error = R.string.halo_frequency_error) { draft = draft.copy(frequency = it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(26_965_000L, 27_185_000L, 27_405_000L).forEach { hz ->
                            val frequency = format(hz / 1e6, 3, true)
                            FilterChip(draft.hertz == hz, { draft = draft.copy(frequency = frequency) }, { Text(frequency) })
                        }
                    }
                    HaloField(draft.factor, R.string.halo_factor, draft.k == null, "halo-factor", error = R.string.halo_factor_error) {
                        draft = draft.copy(factor = it)
                    }
                    HelpText(R.string.halo_factor_help)
                    val gapInvalid = draft.gapMm == null || (draft.hertz != null && draft.k != null && geometry == null)
                    HaloField(draft.gap, R.string.halo_gap_mm, gapInvalid, "halo-gap", error = R.string.halo_gap_error) {
                        draft = draft.copy(gap = it)
                    }
                    HelpText(R.string.halo_gap_help)
                }
            }
            HaloHeading(R.string.halo_general)
            HaloDiagram(if (variant == HaloVariant.ROUND) HaloDiagramType.ROUND else HaloDiagramType.SQUARE)
            HaloSectionSurface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloHeading(R.string.halo_results)
                    HelpText(R.string.halo_estimate)
                    geometry?.let { dimensions ->
                        val rows = draft.resultRows(dimensions).toMutableList()
                        rows.add(rows.lastIndex, "halo_gap" to checkNotNull(draft.gapMm) / 1000)
                        rows.forEach { (key, value) ->
                            HaloRow(label(key), "${format(value, 3, true)} $metres", Modifier.testTag(key))
                        }
                    }
                }
            }
            HaloDisclosure(R.string.halo_formulas) {
                SelectableText("λ = 299.792458 / f\nP = kλ / 2\nk = P / (λ / 2)")
                SelectableText(if (variant == HaloVariant.ROUND) "D = P / π\nΔ = D · asin(g / D)\nL = P − Δ" else "S = P / 4\nL = P − g")
                HelpText(R.string.halo_formula_units)
            }
            HaloDisclosure(R.string.halo_gamma) {
                HaloDiagram(HaloDiagramType.GAMMA)
                HelpText(R.string.halo_gamma_diagram)
                HelpText(R.string.halo_gamma_help)
                HaloGammaField.entries.forEach { field ->
                    HaloField(draft.gamma[field.ordinal], field.label, field in gammaErrors, field.key,
                        suffix = if (field.unit == SnapshotUnit.PICOFARADS) R.string.halo_pf else R.string.hb9cv_aerial_mm) { value ->
                        draft = draft.copy(gamma = draft.gamma.toMutableList().apply { set(field.ordinal, value) })
                    }
                }
            }
            if (gammaErrors.isNotEmpty()) Text(stringResource(R.string.halo_gamma_error), color = MaterialTheme.colorScheme.error)
            if (onSave != null) Button({ snapshot?.let(onSave) }, Modifier.fillMaxWidth().testTag("halo-save"), enabled = snapshot != null) {
                Text(stringResource(R.string.jpole_copyProject))
            }
            OutlinedButton({
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                if (clipboard != null && snapshot != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Halo CB", report(snapshot)))
                    onCopied()
                }
            }, Modifier.fillMaxWidth().testTag("halo-copy"), enabled = snapshot != null) { Text(stringResource(R.string.halo_copy)) }
            HaloDisclosure(R.string.halo_tuning) {
                HelpText(R.string.halo_tuning_help)
                if (onMeasurements != null) TextButton(onMeasurements, Modifier.testTag("halo-open-measurements")) {
                    Text(stringResource(R.string.sweep_tool_title))
                }
                if (onBandwidth != null) TextButton(onBandwidth, Modifier.testTag("halo-open-bandwidth")) {
                    Text(stringResource(R.string.bandwidth_title))
                }
            }
        }
        HaloDisclosure(R.string.halo_how) {
            HelpText(R.string.halo_how_text)
            HelpText(R.string.halo_w2eey_note)
        }
        HaloDisclosure(R.string.halo_compare) { HaloComparison() }
        HaloHeading(R.string.halo_sources)
        HelpText(R.string.halo_sources_note)
        HaloPublicationLinks()
    }
}

@Composable
private fun HaloHistory() {
    HaloSectionSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloHeading(R.string.halo_published)
            HaloRow(stringResource(R.string.halo_side), stringResource(R.string.halo_csq_side))
            HaloRow(stringResource(R.string.halo_feedline), stringResource(R.string.halo_ohms))
            HaloRow(stringResource(R.string.halo_match), "Reddi Match")
            HaloRow(stringResource(R.string.halo_mount), stringResource(R.string.halo_three_supports))
            HaloRow(stringResource(R.string.halo_roof_height), stringResource(R.string.halo_roof_size))
            HaloDiagram(HaloDiagramType.ROOF)
            HelpText(R.string.halo_historical)
        }
    }
}

@Composable
private fun HaloSectionSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

@Composable
private fun HaloComparison() {
    HelpText(R.string.halo_compare_help)
    ScrollableTable(Modifier.width(540.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = listOf(
            listOf(stringResource(R.string.halo_variant), stringResource(R.string.halo_column_size), stringResource(R.string.halo_match)),
            listOf("Cushcraft ASQ‑10", stringResource(R.string.halo_asq_size), stringResource(R.string.halo_asq_match)),
            listOf("KK5JY · 6/10 m", stringResource(R.string.halo_es_size), stringResource(R.string.halo_es_match)),
            listOf("KP4MD · 2 m", stringResource(R.string.halo_kp_size), stringResource(R.string.halo_gamma)),
        )
        rows.forEachIndexed { index, row ->
            SelectionContainer {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cell -> Text(cell, Modifier.weight(1f),
                        style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    HaloDiagram(HaloDiagramType.SHUNT)
    HelpText(R.string.halo_es_help)
}

@Composable
private fun HaloField(raw: String, label: Int, invalid: Boolean, tag: String, suffix: Int? = null,
                      error: Int? = null, edit: (String) -> Unit) {
    OutlinedTextField(
        raw, { if (it.acceptsDecimalEdit()) edit(it) }, Modifier.fillMaxWidth().testTag(tag),
        label = { Text(stringResource(label)) }, singleLine = true, isError = invalid,
        suffix = suffix?.let { { Text(stringResource(it)) } },
        supportingText = error?.takeIf { invalid }?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun HaloRow(label: String, value: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f))
            Text(value)
        }
    }
}

@Composable
private fun HaloHeading(label: Int) {
    Text(stringResource(label), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun HaloDisclosure(label: Int, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            { expanded = !expanded },
            Modifier.testTag("halo-section-$label"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        ) {
            Text(stringResource(label) + if (expanded) " ▴" else " ▾")
        }
        if (expanded) content()
    }
}
