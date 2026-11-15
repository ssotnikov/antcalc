package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.layout.SlimJimBand
import local.antenna.calculator.core.layout.SlimJimDu1anv
import local.antenna.calculator.core.layout.SlimJimG0kya
import local.antenna.calculator.core.layout.Slimtenna4X4lh
import local.antenna.calculator.core.layout.estimateSlimJimM0ukd
import local.antenna.calculator.core.sizing.calculateIdealSizing
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.journal.JournalPresentation

private data class SlimJimDimension(val mark: String, val key: String, val label: Int, val meters: Double)


internal enum class SlimJimProfile(
    val band: SlimJimBand,
    val model: String,
    val bandLabel: Int,
    val title: Int,
    val material: Int,
    val explanation: Int,
    val sourceTitle: Int,
    val sourceUrl: Int,
    @param:DrawableRes @get:DrawableRes val illustration: Int,
    val factor: Double? = null,
    val frequency: Frequency = band.frequency,
) {
    DU1ANV(SlimJimBand.TWO_METERS, SlimJimDu1anv.MODEL_ID, R.string.slim_jim_band_2m,
        R.string.slim_jim_profile, R.string.slim_jim_material, R.string.slim_jim_projection,
        R.string.slim_jim_du1anv, R.string.slim_jim_du1anv_url, R.drawable.slim_jim_antenna),
    M0UKD_70CM(SlimJimBand.SEVENTY_CENTIMETERS, "slim_jim_m0ukd_70cm", R.string.slim_jim_band_70cm,
        R.string.slim_jim_m0ukd_estimate, R.string.slim_jim_bare_metal, R.string.slim_jim_m0ukd_formula,
        R.string.slim_jim_m0ukd, R.string.slim_jim_m0ukd_url, R.drawable.slim_jim_metal, factor = 0.96),
    M0UKD_MARINE(SlimJimBand.MARINE_VHF, "slim_jim_m0ukd_marine", R.string.slim_jim_band_marine,
        R.string.slim_jim_m0ukd_estimate, R.string.slim_jim_bare_metal, R.string.slim_jim_m0ukd_formula,
        R.string.slim_jim_m0ukd, R.string.slim_jim_m0ukd_url, R.drawable.slim_jim_metal, factor = 0.96),
    M0UKD_4M(SlimJimBand.FOUR_METERS, "slim_jim_m0ukd_4m", R.string.slim_jim_band_4m,
        R.string.slim_jim_m0ukd_estimate, R.string.slim_jim_line_450, R.string.slim_jim_m0ukd_formula,
        R.string.slim_jim_m0ukd, R.string.slim_jim_m0ukd_url, R.drawable.slim_jim_ladder, factor = 0.90),
    G0KYA_6M(SlimJimBand.SIX_METERS, "slim_jim_g0kya_6m", R.string.slim_jim_band_6m,
        R.string.slim_jim_g0kya_scaled, R.string.slim_jim_moonraker, R.string.slim_jim_g0kya_formula,
        R.string.slim_jim_g0kya_article, R.string.slim_jim_g0kya_article_url, R.drawable.slim_jim_window_line),
    G0KYA_10M(SlimJimBand.TEN_METERS, "slim_jim_g0kya_10m", R.string.slim_jim_band_10m,
        R.string.slim_jim_g0kya_profile, R.string.slim_jim_moonraker, R.string.slim_jim_g0kya_dimensions,
        R.string.slim_jim_g0kya_article, R.string.slim_jim_g0kya_article_url, R.drawable.slim_jim_window_line),
    SLIMTENNA_20M(SlimJimBand.TWENTY_METERS, "slim_jim_4x4lh_20m", R.string.slim_jim_band_20m,
        R.string.slim_jim_slimtenna_profile, R.string.slim_jim_slimtenna_material, R.string.slim_jim_slimtenna_formula,
        R.string.slim_jim_20m_en_title, R.string.slim_jim_20m_en_url, R.drawable.slim_jim_slimtenna, factor = Slimtenna4X4lh.STUB_VF),
    SV1JRF_70CM(SlimJimBand.SEVENTY_CENTIMETERS, "slim_jim_sv1jrf_70cm", R.string.slim_jim_sv1jrf_band,
        R.string.slim_jim_sv1jrf_title, R.string.slim_jim_sv1jrf_material, R.string.slim_jim_sv1jrf_dimensions,
        R.string.slim_jim_sv1jrf_source, R.string.slim_jim_sv1jrf_url, R.drawable.slim_jim_metal,
        frequency = Frequency.ofHertz(436_000_000));

    val usesM0ukd get() = this == M0UKD_70CM || this == M0UKD_MARINE || this == M0UKD_4M
    val revision get() = if (this == DU1ANV) SlimJimDu1anv.REVISION else 1
}

private fun SlimJimProfile.dimensions(): List<SlimJimDimension> {
    return when (this) {
        SlimJimProfile.DU1ANV -> with(SlimJimDu1anv) {
            listOf(
                SlimJimDimension("L1", "slim_jim_height", R.string.slim_jim_height, heightMeters),
                SlimJimDimension("L2", "slim_jim_stub", R.string.slim_jim_stub, stubMeters),
                SlimJimDimension("L3", "slim_jim_return", R.string.slim_jim_return, returnProjectionMeters),
                SlimJimDimension("E", "slim_jim_gap", R.string.slim_jim_gap, gapMeters),
                SlimJimDimension("S", "slim_jim_spacing", R.string.slim_jim_spacing, spacingMeters),
                SlimJimDimension("h", "slim_jim_tap", R.string.slim_jim_tap, tapMeters),
                SlimJimDimension("—", "slim_jim_wire_stock", R.string.slim_jim_wire_stock, wireStockMeters),
            )
        }
        SlimJimProfile.M0UKD_70CM, SlimJimProfile.M0UKD_MARINE, SlimJimProfile.M0UKD_4M -> with(estimateSlimJimM0ukd(frequency, checkNotNull(factor))) {
            listOf(
                SlimJimDimension("λ", "wavelength", R.string.wavelength, wavelengthMeters),
                SlimJimDimension("A", "slim_jim_height", R.string.slim_jim_height, heightMeters),
                SlimJimDimension("B", "slim_jim_return", R.string.slim_jim_return, radiatorMeters),
                SlimJimDimension("C", "slim_jim_stub", R.string.slim_jim_stub, stubMeters),
                SlimJimDimension("D", "slim_jim_tap", R.string.slim_jim_tap, tapMeters),
                SlimJimDimension("E", "slim_jim_gap", R.string.slim_jim_gap, gapMeters),
            )
        }
        SlimJimProfile.G0KYA_6M, SlimJimProfile.G0KYA_10M -> with(SlimJimG0kya(frequency)) {
            listOf(
                SlimJimDimension("—", "slim_jim_line_stock", R.string.slim_jim_line_stock, stockMeters),
                SlimJimDimension("L2", "slim_jim_gap_start", R.string.slim_jim_gap_start, gapStartMeters),
                SlimJimDimension("L2 + E", "slim_jim_gap_end", R.string.slim_jim_gap_end, gapEndMeters),
                SlimJimDimension("E", "slim_jim_gap", R.string.slim_jim_gap, gapMeters),
                SlimJimDimension("h", "slim_jim_tap", R.string.slim_jim_tap, tapMeters),
            )
        }
        SlimJimProfile.SLIMTENNA_20M -> listOf(
            SlimJimDimension("λ", "wavelength", R.string.wavelength, calculateIdealSizing(frequency).wavelengthMeters),
            SlimJimDimension("H", "slim_jim_slimtenna_height", R.string.slim_jim_slimtenna_height, Slimtenna4X4lh.HEIGHT_METERS),
            SlimJimDimension("L", "slim_jim_separate_stub", R.string.slim_jim_separate_stub, Slimtenna4X4lh.stubMeters(frequency)),
        )
        SlimJimProfile.SV1JRF_70CM -> listOf(
            SlimJimDimension("A", "slim_jim_height", R.string.slim_jim_height, .503),
            SlimJimDimension("B", "slim_jim_return", R.string.slim_jim_return, .331),
            SlimJimDimension("C", "slim_jim_stub", R.string.slim_jim_stub, .166),
            SlimJimDimension("D", "slim_jim_tap", R.string.slim_jim_tap, .017),
            SlimJimDimension("E", "slim_jim_gap", R.string.slim_jim_gap, .007),
            SlimJimDimension("F", "slim_jim_spacing", R.string.slim_jim_spacing, .015),
        )
    }
}

internal fun SlimJimProfile.connectionText(): Int = when (this) {
    SlimJimProfile.DU1ANV -> R.string.slim_jim_connections
    SlimJimProfile.SLIMTENNA_20M -> R.string.slim_jim_slimtenna_connections
    else -> R.string.slim_jim_other_connections
}

private fun SlimJimProfile.snapshot(dimensions: List<SlimJimDimension>) = CalculationSnapshot(
    model, revision,
    buildList {
        add(SnapshotValue("frequency", frequency.hertz.toDouble(), SnapshotUnit.HERTZ))
        factor?.let {
            val key = if (this@snapshot == SlimJimProfile.SLIMTENNA_20M) "slim_jim_stub_vf" else "factor"
            add(SnapshotValue(key, it, SnapshotUnit.FACTOR))
        }
    },
    dimensions.map { SnapshotValue(it.key, it.meters, SnapshotUnit.METERS) },
)

@Composable
internal fun SlimJimScreen(
    onSave: ((CalculationSnapshot) -> Unit)?,
    onMeasurements: (() -> Unit)?,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var unit by rememberSaveable { mutableStateOf(LengthUnit.MILLIMETERS) }
    var selected by rememberSaveable { mutableStateOf(SlimJimProfile.DU1ANV) }
    val dimensions = remember(selected) { selected.dimensions() }
    val frequency = JournalPresentation(context).frequency(selected.frequency.hertz)
    val profile = stringResource(selected.title)
    val version = stringResource(R.string.journal_model, profile, selected.revision)
    val factorText = selected.factor?.let { localizedNumber(context, it.toString()) }
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
            .padding(20.dp).testTag("slim-jim-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RotatingAntenna("slim-${selected.ordinal}", profile,
            Modifier.testTag("slim-jim-hero"),
            geometry = if (selected == SlimJimProfile.SV1JRF_70CM) sv1jrfVertical.vector() else null, baseHeight = 175.dp)
        HelpText(if (selected == SlimJimProfile.SLIMTENNA_20M) R.string.slim_jim_slimtenna_intro else R.string.slim_jim_intro)
        ChoicePicker(stringResource(R.string.slim_jim_band), SlimJimProfile.entries, selected,
            { stringResource(it.bandLabel) }, { selected = it }, "slim-jim-band")
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SlimJimHeading(R.string.slim_jim_construction)
                Text(profile)
                Text(stringResource(R.string.slim_jim_revision, selected.revision))
                Text(frequency)
                HelpText(selected.material)
            }
        }
        ChoicePicker(stringResource(R.string.length_units), LengthUnit.entries, unit,
            { stringResource(it.labelResource()) }, { unit = it }, "slim-jim-unit")
        SlimJimHeading(R.string.slim_jim_diagram)
        when (selected) {
            SlimJimProfile.SLIMTENNA_20M -> SlimtennaDiagram()
            SlimJimProfile.SV1JRF_70CM -> PublishedVerticalDiagram(sv1jrfVertical,
                stringResource(R.string.slim_jim_sv1jrf_material), "slim-jim-diagram-$selected",
                highlightGapAndFeed = true)
            else -> SlimJimDiagram(selected)
        }
        HelpText(selected.connectionText())
        if (selected.usesM0ukd) HelpText(R.string.slim_jim_m0ukd_spacing)
        SlimJimHeading(R.string.slim_jim_sizes)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ScrollableTable(Modifier.width(maxWidth.coerceAtLeast(300.dp))) {
                dimensions.forEach { dimension ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(dimension.mark, Modifier.widthIn(min = 28.dp), color = MaterialTheme.colorScheme.secondary)
                        Text(stringResource(dimension.label), Modifier.weight(1f))
                        Text(formatLength(context, dimension.meters, unit))
                    }
                    HorizontalDivider()
                }
            }
        }
        HelpText(selected.explanation)
        if (factorText != null) {
            Text(stringResource(R.string.journal_property,
                stringResource(if (selected == SlimJimProfile.SLIMTENNA_20M) R.string.slim_jim_stub_vf else R.string.factor),
                factorText))
        }
        if (onSave != null) {
            Button(onClick = { onSave(selected.snapshot(dimensions)) }, modifier = Modifier.fillMaxWidth().testTag("slim-jim-save")) {
                Text(stringResource(R.string.jpole_copyProject))
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("slim-jim-copy"), onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
            val report = buildString {
                appendLine(version)
                appendLine("${selected.model} · $frequency")
                for (dimension in dimensions) {
                    val value = formatLength(context, dimension.meters, unit)
                    appendLine("${dimension.mark} · ${resources.getString(dimension.label)}: $value")
                }
                factorText?.let {
                    val label = if (selected == SlimJimProfile.SLIMTENNA_20M) R.string.slim_jim_stub_vf else R.string.factor
                    appendLine(resources.getString(R.string.journal_property, resources.getString(label), it))
                }
                appendLine(resources.getString(selected.explanation))
                append(resources.getString(selected.sourceUrl))
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(profile, report))
            onCopied()
        }) { Text(stringResource(R.string.jpole_copy)) }
        if (selected != SlimJimProfile.SLIMTENNA_20M) {
            SlimJimPanel(R.string.slim_jim_theory) { HelpText(R.string.slim_jim_theory_text) }
        }
        SlimJimPanel(R.string.slim_jim_tuning, Modifier.testTag("slim-jim-tuning")) {
            HelpText(when (selected) {
                SlimJimProfile.DU1ANV -> R.string.slim_jim_tuning_text
                SlimJimProfile.SLIMTENNA_20M -> R.string.slim_jim_slimtenna_tuning
                SlimJimProfile.SV1JRF_70CM -> R.string.slim_jim_sv1jrf_tuning
                else -> R.string.slim_jim_other_tuning
            })
            onMeasurements?.let {
                TextButton(onClick = it, modifier = Modifier.testTag("slim-jim-open-measurements")) {
                    Text(stringResource(R.string.sweep_tool_title))
                }
            }
        }
        if (selected == SlimJimProfile.DU1ANV) SlimJimPanel(R.string.slim_jim_reference) {
            HelpText(R.string.slim_jim_reference_text)
            SlimJimLink(R.string.slim_jim_m0ukd, SLIM_JIM_M0UKD_URL)
        }
        SlimJimHeading(R.string.slim_jim_source)
        if (selected == SlimJimProfile.DU1ANV) HelpText(R.string.slim_jim_author)
        SlimJimLink(selected.sourceTitle, stringResource(selected.sourceUrl))
        SlimJimPanel(R.string.slim_jim_ok1ike) {
            HelpText(R.string.slim_jim_ok1ike_dimensions)
            SlimJimLink(R.string.slim_jim_ok1ike_source, SLIM_JIM_OK1IKE_URL)
            SlimJimLink(R.string.jpole_ok1ike_hb9acc, OK1IKE_HB9ACC_URL)
        }
    }
}

@Composable
private fun SlimJimHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun SlimJimPanel(label: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.jpole_expanded else R.string.jpole_collapsed)
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = modifier.semantics { stateDescription = state },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
            Text(stringResource(label))
        }
        if (expanded) content()
    }
}

@Composable
private fun SlimJimLink(label: Int, url: String) {
    val context = LocalContext.current
    TextButton(onClick = { openSourceWithFeedback(context, url) }) { Text(stringResource(label)) }
}

@Composable
private fun SlimJimDiagram(profile: SlimJimProfile) {
    val du1anv = profile == SlimJimProfile.DU1ANV
    val m0ukd = profile.usesM0ukd
    val windowLine = profile == SlimJimProfile.G0KYA_6M || profile == SlimJimProfile.G0KYA_10M
    val description = stringResource(when {
        windowLine -> R.string.slim_jim_window_diagram
        m0ukd -> R.string.slim_jim_m0ukd_diagram
        else -> R.string.slim_jim_diagram_description
    })
    val centre = stringResource(R.string.slim_jim_center_lead)
    val braid = stringResource(R.string.slim_jim_braid_lead)
    val colors = MaterialTheme.colorScheme
    val wire = colors.secondary
    val amber = if (colors.surface.luminance() < .5f) Color(0xFFF7C879) else Color(0xFF805000)
    val style = MaterialTheme.typography.labelMedium.copy(color = wire)
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(260.dp)
        .testTag("slim-jim-diagram-$profile").semantics { contentDescription = description }) {
        val left = size.width * .38f
        val right = size.width * .65f
        val top = size.height * .12f
        val bottom = size.height * .82f
        val gapTop = size.height * .53f
        val gapBottom = size.height * .60f
        val tap = size.height * .74f
        val thickness = 4.dp.toPx()
        if (windowLine || profile == SlimJimProfile.M0UKD_4M) {
            val rungColor = colors.outline.copy(alpha = .42f)
            for (fraction in listOf(.17f, .27f, .37f, .47f, .66f, .77f)) {
                val y = size.height * fraction
                drawLine(rungColor, Offset(left, y), Offset(right, y), if (windowLine) 7.dp.toPx() else 2.dp.toPx())
            }
        }
        drawLine(wire, Offset(left, top), Offset(left, bottom), thickness)
        drawLine(wire, Offset(left, top), Offset(right, top), thickness)
        drawLine(wire, Offset(left, bottom), Offset(right, bottom), thickness)
        drawLine(wire, Offset(right, top), Offset(right, gapTop), thickness)
        drawLine(wire, Offset(right, gapBottom), Offset(right, bottom), thickness)
        drawLine(amber, Offset(size.width * .23f, tap), Offset(left, tap), 2.dp.toPx())
        drawLine(amber, Offset(right, tap), Offset(size.width * .77f, tap), 2.dp.toPx())
        drawCircle(amber, 4.dp.toPx(), Offset(left, tap))
        drawCircle(amber, 4.dp.toPx(), Offset(right, tap))
        fun mark(text: String, x: Float, y: Float, color: Color = wire) {
            val layout = measurer.measure(text, style.copy(color = color))
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
        }
        fun dimension(x: Float, y1: Float, y2: Float, label: String, color: Color = colors.outline) {
            val tick = 4.dp.toPx()
            drawLine(color, Offset(x, y1), Offset(x, y2))
            drawLine(color, Offset(x - tick, y1), Offset(x + tick, y1))
            drawLine(color, Offset(x - tick, y2), Offset(x + tick, y2))
            mark(label, x + size.width * .065f, (y1 + y2) / 2f, if (color == amber) amber else wire)
        }
        if (du1anv || m0ukd) {
            dimension(size.width * .09f, top, bottom, if (m0ukd) "A" else "L1")
            dimension(size.width * .82f, top, gapTop, if (m0ukd) "B" else "L3")
        }
        dimension(size.width * .82f, gapTop, gapBottom, "E", amber)
        dimension(size.width * .82f, gapBottom, bottom, if (m0ukd) "C" else "L2")
        if (windowLine) {
            val upperEdge = size.width * .12f
            dimension(upperEdge, gapTop, bottom, "L2 + E")
            drawLine(colors.outline, Offset(upperEdge, gapTop), Offset(right, gapTop))
        }
        dimension(size.width * .16f, tap, bottom, if (m0ukd) "D" else "h", amber)
        mark(centre, size.width * .25f, tap - 22.dp.toPx(), amber)
        mark(braid, size.width * .77f, tap - 22.dp.toPx(), amber)
        if (du1anv || m0ukd) {
            val spacing = size.height * .89f
            val inside = if (m0ukd) thickness / 2 else 0f
            val ends = listOf(left + inside, right - inside)
            drawLine(colors.outline, Offset(ends.first(), spacing), Offset(ends.last(), spacing))
            for (end in ends) {
                drawLine(colors.outline, Offset(end, spacing - 4.dp.toPx()), Offset(end, spacing + 4.dp.toPx()))
            }
            mark(if (m0ukd) "F" else "S", (left + right) / 2f, size.height * .95f)
        }
    }
}

@Composable
private fun SlimtennaDiagram() {
    val description = stringResource(R.string.slim_jim_slimtenna_connections)
    val colors = MaterialTheme.colorScheme
    val amber = if (colors.surface.luminance() < .5f) Color(0xFFF7C879) else Color(0xFF805000)
    val labelStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(246.dp)
        .testTag("slim-jim-diagram-SLIMTENNA_20M").semantics { contentDescription = description }) {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = colors.secondary, width: Float = 2.dp.toPx()) {
            drawLine(color, point(x1, y1), point(x2, y2), width)
        }
        fun mark(text: String, x: Float, y: Float, color: Color = colors.secondary) {
            val label = measurer.measure(text, labelStyle.copy(color = color))
            drawText(label, topLeft = point(x, y) - Offset(label.size.width / 2f, label.size.height / 2f))
        }
        line(.28f, .09f, .43f, .09f)
        for ((x, bottom) in listOf(.28f to .61f, .43f to .54f)) {
            line(x, .09f, x, .24f, width = 2.dp.toPx())
            line(x, .24f, x, .41f, width = 3.dp.toPx())
            line(x, .41f, x, bottom, width = 4.dp.toPx())
        }
        for (y in listOf(.24f, .41f)) line(.28f, y, .43f, y, colors.outline)
        line(.15f, .09f, .15f, .61f, colors.outline)
        for (y in listOf(.09f, .61f)) line(.12f, y, .18f, y, colors.outline)
        mark("H", .09f, .35f)

        line(.28f, .61f, .87f, .61f, amber)
        line(.28f, .72f, .87f, .72f, amber)
        line(.87f, .61f, .87f, .72f, amber)
        drawCircle(amber, 4.dp.toPx(), point(.28f, .61f))
        drawCircle(amber, 4.dp.toPx(), point(.28f, .72f), style = Stroke(1.5.dp.toPx()))
        mark("1", .23f, .69f, amber)
        mark("2", .87f, .53f, amber)

        val lowerTap = point(.70f, .72f)
        drawCircle(amber, 3.dp.toPx(), point(.63f, .61f))
        drawCircle(amber, 3.dp.toPx(), lowerTap)
        line(.63f, .61f, .63f, .75f, amber)
        drawCircle(colors.surface, 3.dp.toPx(), point(.63f, .72f))
        line(.63f, .75f, .57f, .80f, amber)
        line(.70f, .72f, .70f, .80f, amber)
        mark("3", .64f, .84f, amber)

        line(.28f, .93f, .87f, .93f, colors.outline)
        for (x in listOf(.28f, .87f)) line(x, .90f, x, .96f, colors.outline)
        mark("L", .56f, .98f)
    }
}
