package local.antenna.calculator.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.antenna.calculator.R
import local.antenna.calculator.core.matching.*
import local.antenna.calculator.state.boundedNumericText

private enum class SmithField(val label: Int, val kind: MatchingField, val range: ClosedFloatingPointRange<Double>) {
    REFERENCE(R.string.smith_reference, MatchingField.REFERENCE, 10.0..300.0),
    RESISTANCE(R.string.smith_resistance, MatchingField.RESISTANCE, 0.0..500.0),
    REACTANCE(R.string.smith_reactance, MatchingField.REACTANCE, -500.0..500.0);

    fun read(text: String) = parseMatchingNumber(text, kind).value?.takeIf { it in range }
}

@Composable
internal fun SmithChartScreen(notify: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val palette = smithPalette()
    var reference by rememberSaveable { mutableStateOf("217") }
    var resistance by rememberSaveable { mutableStateOf("74") }
    var reactance by rememberSaveable { mutableStateOf("40") }
    var distance by rememberSaveable { mutableIntStateOf(375) }
    var pending by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var writing by remember { mutableStateOf(false) }

    val z0 = SmithField.REFERENCE.read(reference)
    val r = SmithField.RESISTANCE.read(resistance)
    val x = SmithField.REACTANCE.read(reactance)
    val load = if (z0 != null && r != null && x != null) SmithLoad(z0, r, x) else null
    val diagram = makeDiagram(context, load, distance)
    val labels = smithMetricLabels.map { stringResource(it) }
    val rows = labels.zip(smithMetrics(context, load))
    val lineValue = smithLineValue(context, diagram)
    val title = stringResource(R.string.smith_title)
    val saved = stringResource(R.string.smith_exported)
    val failed = stringResource(R.string.smith_export_error)
    val formatGrid: (Double) -> String = { matchingNumber(context, it, 1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val values = pending
        pending = null
        if (uri != null && values != null) {
            val captured = SmithLoad(values.getDouble("reference"), values.getDouble("resistance"), values.getDouble("reactance"))
            val capture = makeDiagram(context, captured, values.getInt("distance"))
            val input = resources.getString(R.string.smith_export_input,
                matchingNumber(context, captured.reference), capture.loadLabel)
            val details = listOf(
                resources.getString(R.string.smith_load_parameters) to input,
                resources.getString(R.string.smith_line_point) to smithLineValue(context, capture),
            ) +
                labels.zip(smithMetrics(context, captured))
            writing = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        writeSmithPng(context.contentResolver, uri, capture, palette, formatGrid, title, details)
                        true
                    } catch (_: IOException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    } catch (_: IllegalArgumentException) {
                        false
                    }
                }
                writing = false
                notify(if (ok) saved else failed)
            }
        }
    }

    Column(
        Modifier.widthIn(max = 580.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("smith-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SmithPanel {
            val chartDescription = if (load != null) {
                stringResource(R.string.smith_chart_accessible, diagram.loadLabel, rows.last().second, lineValue)
            } else title
            SmithChartGraphic(diagram, palette, chartDescription, formatGrid) { point, line ->
                if (line) {
                    val current = load ?: return@SmithChartGraphic
                    distance = (current.reflection.distanceToward(point, distance / 1000.0) * 1000).roundToInt()
                } else {
                    val impedance = z0?.let(point::toSmithLoad) ?: return@SmithChartGraphic
                    resistance = matchingNumber(context, impedance.resistance, 1)
                    reactance = matchingNumber(context, impedance.reactance, 1)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("smith-legend")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmithLegendRow("●", Color(palette.load), stringResource(R.string.smith_legend_load))
                    SmithLegendRow("●", Color(palette.line), stringResource(R.string.smith_legend_line))
                    SmithLegendRow("┄", Color(palette.load), stringResource(R.string.smith_legend_circle))
                }
            }
            Text(stringResource(R.string.smith_distance), style = MaterialTheme.typography.bodyMedium)
            Slider(distance.toFloat(), { distance = it.roundToInt() }, valueRange = 0f..500f,
                modifier = Modifier.testTag("smith-distance").semantics { stateDescription = lineValue })
            Text(lineValue, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("smith-line-value"))
            SmithHint(R.string.smith_drag_hint)
            SmithInput(SmithField.REFERENCE, reference, z0) { reference = it }
            SmithInput(SmithField.RESISTANCE, resistance, r) { resistance = it }
            SmithInput(SmithField.REACTANCE, reactance, x) { reactance = it }
            SmithRows(rows)
            Button(
                onClick = {
                    val current = load ?: return@Button
                    focus.clearFocus()
                    pending = Bundle().apply {
                        putDouble("reference", current.reference)
                        putDouble("resistance", current.resistance)
                        putDouble("reactance", current.reactance)
                        putInt("distance", distance)
                    }
                    try {
                        picker.launch("smith-chart.png")
                    } catch (_: ActivityNotFoundException) {
                        pending = null
                        notify(failed)
                    } catch (_: SecurityException) {
                        pending = null
                        notify(failed)
                    }
                },
                enabled = load != null && pending == null && !writing,
                modifier = Modifier.fillMaxWidth().testTag("smith-export"),
            ) { Text(stringResource(R.string.smith_download)) }
        }
        SmithReferencePanels(palette, formatGrid)
    }
}

@Composable
private fun SmithInput(field: SmithField, text: String, value: Double?, edit: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = text, onValueChange = { edit(it.boundedNumericText()) },
        label = { Text(stringResource(field.label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (field.kind.signed) KeyboardType.Text else KeyboardType.Decimal),
        isError = value == null,
        supportingText = if (value == null) { {
            Text(stringResource(R.string.smith_input_range, field.range.start.toInt(), field.range.endInclusive.toInt()))
        } } else null,
        modifier = Modifier.fillMaxWidth().testTag("smith-field-${field.name}"),
    )
    val label = stringResource(field.label)
    val range = field.range.start.toFloat()..field.range.endInclusive.toFloat()
    Slider(value?.toFloat() ?: range.start, { edit(matchingNumber(context, it.toDouble(), 1)) }, valueRange = range,
        modifier = Modifier.testTag("smith-slider-${field.name}").semantics { stateDescription = "$label, $text" })
}

@Composable
internal fun SmithPanel(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SmithHeading(label: Int) {
    Text(stringResource(label), Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary)
}

@Composable
internal fun SmithHint(label: Int) {
    Text(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SmithLegendRow(symbol: String, color: Color, explanation: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(symbol, color = color, style = MaterialTheme.typography.labelMedium)
        Text(explanation, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SmithRows(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().testTag("smith-results")) {
        rows.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1.25f), style = MaterialTheme.typography.bodySmall)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
            }
            if (index < rows.lastIndex) HorizontalDivider()
        }
    }
}

private val smithMetricLabels = listOf(R.string.smith_normalized, R.string.smith_magnitude, R.string.smith_phase,
    R.string.smith_return_loss, R.string.smith_swr)

internal fun smithMeasure(context: Context, value: MatchingMeasure, decimals: Int) = when (value) {
    MatchingMeasure.Infinite -> "∞"
    is MatchingMeasure.Finite -> matchingNumber(context, value.value, decimals)
}

private fun makeDiagram(context: Context, load: SmithLoad?, distance: Int) = SmithDiagram(
    load, distance / 1000.0, load?.let {
        context.getString(R.string.smith_ohms, complex(context, it.resistance, it.reactance, 1))
    }.orEmpty(),
)

private fun complex(context: Context, real: Double, imaginary: Double, decimals: Int): String {
    val threshold = 0.5 * Math.pow(10.0, -decimals.toDouble())
    val component = if (abs(imaginary) < threshold) 0.0 else imaginary
    return matchingNumber(context, if (abs(real) < threshold) 0.0 else real, decimals) +
        (if (component < 0) " − j" else " + j") + matchingNumber(context, abs(component), decimals)
}

private fun smithLineValue(context: Context, diagram: SmithDiagram): String {
    val distance = matchingNumber(context, diagram.distance)
    val point = diagram.linePoint ?: return context.getString(R.string.smith_distance_value, distance)
    return context.getString(R.string.smith_line_value, distance, complex(context, point.real, point.imaginary, 3))
}

private fun smithMetrics(context: Context, load: SmithLoad?): List<String> {
    if (load == null) return List(smithMetricLabels.size) { "—" }
    val result = load.matching
    return listOf(
        complex(context, load.resistance / load.reference, load.reactance / load.reference, 3),
        matchingNumber(context, result.magnitude, 3),
        result.complex?.phaseDegrees?.let { matchingNumber(context, it, 1) + "°" } ?: "—",
        context.getString(R.string.matching_db, smithMeasure(context, result.returnLoss, 1)),
        smithMeasure(context, result.swr, 2),
    )
}
