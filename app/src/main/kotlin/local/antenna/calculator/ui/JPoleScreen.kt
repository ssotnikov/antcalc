package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.layout.JPoleDimensions
import local.antenna.calculator.core.layout.calculateJPole
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.core.units.ShorteningFactor
import local.antenna.calculator.state.boundedNumericText
import local.antenna.calculator.state.validValue

private fun JPoleDimensions.rows() = listOf(
    Triple("jpole_long", R.string.jpole_long, longElement),
    Triple("jpole_stub", R.string.jpole_stub, stub),
    Triple("jpole_radiator", R.string.jpole_radiator, radiator),
    Triple("jpole_spacing", R.string.jpole_spacing, spacing),
    Triple("jpole_tap", R.string.jpole_tap, feedHeight),
    Triple("wavelength", R.string.wavelength, wavelength),
)

@Composable
internal fun JPoleScreen(onSave: ((CalculationSnapshot) -> Unit)?, onCopied: () -> Unit) {
    val initialFrequency = stringResource(R.string.jpole_default_frequency)
    val initialFactor = stringResource(R.string.jpole_default_factor)
    var frequencyText by rememberSaveable { mutableStateOf(initialFrequency) }
    var factorText by rememberSaveable { mutableStateOf(initialFactor) }
    var unit by rememberSaveable { mutableStateOf(LengthUnit.MILLIMETERS) }
    val frequency = QuantityInput.frequency(frequencyText, FrequencyUnit.MEGAHERTZ).validValue()
    val factor = QuantityInput.shorteningFactor(factorText).validValue()
    val result = if (frequency != null && factor != null) calculateJPole(frequency, factor) else null
    val context = LocalContext.current
    val resources = LocalResources.current
    val frequencyLabel = stringResource(R.string.jpole_frequency)
    val factorLabel = stringResource(R.string.jpole_factor)
    val title = stringResource(R.string.jpole_title)
    val reportRows = result?.rows().orEmpty()

    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState())
        .padding(20.dp).testTag("jpole-screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource(R.drawable.j_pole_antenna), stringResource(R.string.jpole_intro),
            Modifier.fillMaxWidth().height(180.dp))
        JPolePanel(R.string.jpole_theory) {
            JPoleDiagram(R.string.jpole_radiatorLabel, R.string.jpole_stubLabel)
            HelpText(R.string.jpole_radiatorExplanation)
            HelpText(R.string.jpole_matching)
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JPoleHeading(R.string.input_data)
                HelpText(R.string.jpole_summary)
                JPoleInput(frequencyText, frequencyLabel, R.string.jpole_frequencyHint,
                    R.string.jpole_frequencyError.takeIf { frequency == null }, "jpole-frequency") { frequencyText = it }
                JPoleInput(factorText, factorLabel, R.string.jpole_factorHint,
                    R.string.jpole_factorError.takeIf { factor == null }, "jpole-factor") { factorText = it }
            }
        }
        JPoleHeading(R.string.jpole_geometry)
        ChoicePicker(stringResource(R.string.loop_output_unit), LengthUnit.entries, unit,
            { stringResource(it.labelResource()) }, { unit = it }, "jpole-unit")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                JPoleHeading(R.string.jpole_overview)
                JPoleDiagram(R.string.jpole_shield, R.string.jpole_core, showDimensions = true)
            }
        }
        HelpText(R.string.jpole_legend)
        HelpText(R.string.jpole_feedLegend)
        if (result != null) {
            JPoleHeading(R.string.jpole_sizes)
            reportRows.forEach { (key, label, meters) ->
                Text(stringResource(R.string.journal_property, stringResource(label), formatLength(context, meters, unit)),
                    modifier = Modifier.testTag(key), style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider()
            }
        }
        JPolePanel(R.string.jpole_formulaTitle) { JPoleFormulaContent() }
        JPolePanel(R.string.jpole_copperBuildTitle) {
            HelpText(R.string.jpole_copperBuildScope)
            JPoleHeading(R.string.jpole_copperMaterialsTitle)
            HelpText(R.string.jpole_copperMaterials)
            JPoleList(R.string.jpole_copperCut, R.string.jpole_copperJoin, R.string.jpole_copperFeed,
                R.string.jpole_copperChoke, R.string.jpole_copperMount, R.string.jpole_copperSeal)
            JPoleHeading(R.string.jpole_copperTuningTitle)
            HelpText(R.string.jpole_copperMeasure)
            HelpText(R.string.jpole_copperAdjust)
            HelpText(R.string.jpole_copperBandwidth)
        }
        JPolePanel(R.string.jpole_constructionVariantsTitle) {
            JPoleVariant(R.string.jpole_twinLeadTitle, R.string.jpole_twinLeadDescription, R.string.jpole_source_wireman)
            JPoleVariant(R.string.jpole_slim, R.string.jpole_slimJimDescription, R.string.jpole_source_slim)
            JPoleVariant(R.string.jpole_super, R.string.jpole_superJDescription, R.string.jpole_source_super)
            JPoleVariant(R.string.jpole_dualBandTitle, R.string.jpole_dualBandDescription, R.string.jpole_source_open)
            JPoleVariant(R.string.jpole_coaxialTitle, R.string.jpole_coaxialDescription)
        }
        if (onSave != null) {
            Button(enabled = result != null, modifier = Modifier.fillMaxWidth().testTag("jpole-save"), onClick = {
                if (frequency != null && factor != null && result != null) onSave(jPoleSnapshot(frequency, factor, result))
            }) { Text(stringResource(R.string.jpole_copyProject)) }
        }
        OutlinedButton(enabled = result != null, modifier = Modifier.fillMaxWidth().testTag("jpole-copy"), onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
            val text = buildString {
                appendLine(title)
                appendLine("$frequencyLabel: $frequencyText")
                appendLine("$factorLabel: $factorText")
                reportRows.forEach { (_, label, meters) -> appendLine(resources.getString(R.string.journal_property,
                    resources.getString(label), formatLength(context, meters, unit))) }
                append(resources.getString(R.string.jpole_formulaScope))
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
            onCopied()
        }) { Text(stringResource(R.string.jpole_copy)) }
        JPoleHeading(R.string.jpole_refs)
        JPoleLink(R.string.jpole_source_cebik)
    }
}

private fun jPoleSnapshot(frequency: Frequency, factor: ShorteningFactor, result: JPoleDimensions) =
    CalculationSnapshot("j_pole", 1, listOf(
        SnapshotValue("frequency", frequency.hertz.toDouble(), SnapshotUnit.HERTZ),
        SnapshotValue("factor", factor.tenThousandths / 10_000.0, SnapshotUnit.FACTOR),
    ), result.rows().map { (key, _, meters) -> SnapshotValue(key, meters, SnapshotUnit.METERS) })

@Composable
private fun JPoleInput(value: String, label: String, hint: Int, error: Int?, tag: String, update: (String) -> Unit) {
    OutlinedTextField(value, { update(it.boundedNumericText()) },
        modifier = Modifier.fillMaxWidth().testTag(tag), label = { Text(label) },
        singleLine = true, isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text(stringResource(error ?: hint)) })
}

@Composable
private fun JPoleHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun JPolePanel(label: Int, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.jpole_expanded else R.string.jpole_collapsed)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = state },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
            Text(stringResource(label), color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) content()
    }
}

@Composable
private fun JPoleList(vararg labels: Int) {
    labels.forEachIndexed { index, label -> Text(stringResource(R.string.jpole_step, index + 1, stringResource(label))) }
}

@Composable
private fun JPoleVariant(title: Int, description: Int, source: Int? = null) {
    JPoleHeading(title)
    HelpText(description)
    source?.let { JPoleLink(it) }
}

@Composable
private fun JPoleLink(source: Int) {
    val context = LocalContext.current
    val url = stringResource(source)
    TextButton(onClick = { openSourceWithFeedback(context, url) }) {
        Text(stringResource(when (source) {
            R.string.jpole_source_wireman -> R.string.jpole_link_wireman
            R.string.jpole_source_slim -> R.string.jpole_link_slim
            R.string.jpole_source_super -> R.string.jpole_link_super
            R.string.jpole_source_open -> R.string.jpole_link_open
            else -> R.string.jpole_link_cebik
        }))
    }
}

@Composable
private fun JPoleFormulaContent() {
    HelpText(R.string.jpole_formulaScope)
    HelpText(R.string.jpole_formula_equations)
    HelpText(R.string.jpole_formulaUnits)
    HelpText(R.string.jpole_spacingEstimate)
    HelpText(R.string.jpole_tapEstimate)
    HelpText(R.string.jpole_shorteningEstimate)
    JPoleHeading(R.string.jpole_formulaExamplesTitle)
    HelpText(R.string.jpole_formulaExamplesHint)
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { FrequencyNumberFormat(locale) }
    ScrollableTable(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JPoleTableRow(listOf(stringResource(R.string.jpole_formulaFrequencyColumn), "λ", "Lᵣ", "Lₛ", "Lₜ", "S", "h"))
        for (hertz in listOf(50_500_000L, 145_000_000L, 156_800_000L, 435_000_000L, 446_100_000L)) {
            val result = calculateJPole(Frequency.ofHertz(hertz), ShorteningFactor.DEFAULT)
            JPoleTableRow(listOf(numbers.inUnit(hertz / 1e6, FrequencyUnit.MEGAHERTZ),
                formatLength(context, result.wavelength, LengthUnit.METERS)) +
                listOf(result.radiator, result.stub, result.longElement, result.spacing, result.feedHeight)
                    .map { formatLength(context, it, LengthUnit.MILLIMETERS) })
        }
    }
}

@Composable
private fun JPoleTableRow(cells: List<String>) {
    val cellWidth = 78.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEach { Text(it, Modifier.width(cellWidth), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun JPoleDiagram(left: Int, right: Int, showDimensions: Boolean = false) {
    val label = stringResource(if (showDimensions) R.string.jpole_overview else R.string.jpole_theory)
    val colors = MaterialTheme.colorScheme
    val gold = if (colors.surface.luminance() < 0.5f) Color(0xFFF7C879) else Color(0xFFA35400)
    val metal = colors.secondary
    val matchingSection = if (showDimensions) metal else gold
    val guide = colors.outline
    val textStyle = MaterialTheme.typography.labelMedium.copy(color = metal)
    val measurer = rememberTextMeasurer()
    Column {
        Canvas(Modifier.fillMaxWidth().height(200.dp)
            .semantics { contentDescription = label }) {
            val longX = size.width * .42f
            val shortX = size.width * .60f
            val base = size.height * .76f
            val tap = size.height * .58f
            val shortTop = size.height * .44f
            val sectionTop = if (showDimensions) base else shortTop
            drawLine(if (showDimensions) metal else colors.primary,
                Offset(longX, size.height * .12f), Offset(longX, sectionTop), 4.dp.toPx())
            if (!showDimensions) {
                drawLine(matchingSection, Offset(longX, sectionTop), Offset(longX, base), 4.dp.toPx())
            }
            drawLine(matchingSection, Offset(longX, base), Offset(shortX, base), 4.dp.toPx())
            drawLine(matchingSection, Offset(shortX, base), Offset(shortX, shortTop), 4.dp.toPx())
            if (showDimensions) {
                val spacingY = base + 10.dp.toPx()
                drawLine(guide, Offset(longX, spacingY), Offset(shortX, spacingY))
                for (x in listOf(longX, shortX)) {
                    drawLine(guide, Offset(x, base), Offset(x, spacingY))
                }
                drawLine(guide, Offset(size.width * .84f, tap), Offset(size.width * .84f, base))
                drawLine(guide, Offset(shortX, tap), Offset(size.width * .84f, tap))
                drawLine(guide, Offset(shortX, base), Offset(size.width * .84f, base))
                val spacingLabel = measurer.measure("S", textStyle)
                drawText(spacingLabel, topLeft = Offset((longX + shortX - spacingLabel.size.width) / 2, spacingY))
                drawText(measurer, "A", topLeft = Offset(size.width * .86f, (base + tap) / 2), style = textStyle)
                drawLine(gold, Offset(size.width * .24f, tap), Offset(longX, tap), 2.dp.toPx())
                drawLine(gold, Offset(shortX, tap), Offset(size.width * .77f, tap), 2.dp.toPx())
                drawCircle(gold, 4.dp.toPx(), Offset(longX, tap))
                drawCircle(gold, 4.dp.toPx(), Offset(shortX, tap))
            }
            drawText(measurer, "Lᵣ ≈ kλ/2", topLeft = Offset(0f, size.height * .25f), style = textStyle)
            drawText(measurer, "Lₛ ≈ kλ/4", topLeft = Offset(shortX + 8.dp.toPx(), size.height * .30f), style = textStyle)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(left), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(right), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
