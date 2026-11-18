package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.nec.OpenNec
import java.math.BigDecimal

internal const val DK7ZB_SEGMENTATION_SOURCE = "https://dk7zb.darc.de/2m_veryshort/2ele.htm"

internal fun segmentationSeed(elements: List<Element>, lowHz: Long, highHz: Long, feed: Int): ArrayList<String> {
    fun decimal(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val low = BigDecimal.valueOf(lowHz, 6).stripTrailingZeros().toPlainString()
    val high = BigDecimal.valueOf(highHz, 6).stripTrailingZeros().toPlainString()
    return ArrayList(listOf(low, if (lowHz == highHz) "" else high, feed.toString()) + elements.flatMap {
        listOf(it.position, it.length, it.diameter).map { value -> decimal(value * 1000) }
    })
}

internal val dk7zbSegmentationElements = listOf(Element(0.0, 1.010, 0.010), Element(0.210, 0.920, 0.010))

internal fun dk7zbSegmentationSeed() =
    segmentationSeed(dk7zbSegmentationElements, 144_000_000, 146_000_000, 0)

@Composable
internal fun YagiSegmentationScreen(seed: ArrayList<String>?) {
    val context = LocalContext.current
    var fields by rememberSaveable {
        mutableStateOf(seed ?: arrayListOf("145", "", "1", "0", "", "", "", "", ""))
    }
    fun update(index: Int, value: String) {
        fields = ArrayList(fields).apply { this[index] = value }
    }
    val feed = fields[2].toInt()
    val rows = fields.drop(3).chunked(3)
    val low = scalingFrequency(fields[0])
    val high = if (fields[1].isBlank()) low else scalingFrequency(fields[1])
    val geometry = rows.map { row ->
        val values = row.map { scalingDecimal(it)?.div(1000) }
        if (values.any { it == null }) null
        else Element(values[0]!!, values[1]!!, values[2]!!)
    }
    val elements = geometry.filterNotNull()
    val plan = if (low != null && high != null && high >= low && elements.size == rows.size) {
        OpenNec.segmentation(elements, high)
    } else null
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState()).padding(20.dp)
        .testTag("yagi-segmentation"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HelpText(R.string.seg_intro)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScalingField(fields[0], R.string.seg_frequency, "seg-low", Modifier.weight(1f)) { update(0, it) }
            ScalingField(fields[1], R.string.seg_optional_end, "seg-high", Modifier.weight(1f)) { update(1, it) }
        }
        TextButton({ fields = dk7zbSegmentationSeed() }, Modifier.testTag("seg-example")) {
            Text(stringResource(R.string.seg_example))
        }
        rows.forEachIndexed { index, row ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.seg_element, index + 1), style = MaterialTheme.typography.titleSmall)
                    Row {
                        FilterChip(selected = feed == index, onClick = { update(2, index.toString()) },
                            label = { Text(stringResource(R.string.seg_driven)) })
                        if (index == rows.lastIndex && rows.size > 2) TextButton({
                            if (feed == index) update(2, "0")
                            fields = ArrayList(fields.dropLast(3))
                        }) { Text(stringResource(R.string.ys_remove)) }
                    }
                    if (index > 0) ScalingField(row[0], R.string.seg_position, "seg-position-$index", Modifier.fillMaxWidth()) {
                        update(3 + 3 * index, it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScalingField(row[1], R.string.seg_length, "seg-length-$index", Modifier.weight(1f)) {
                            update(4 + 3 * index, it)
                        }
                        ScalingField(row[2], R.string.seg_diameter, "seg-diameter-$index", Modifier.weight(1f)) {
                            update(5 + 3 * index, it)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { fields = ArrayList(fields + listOf("", "", "")) }, enabled = rows.size < 24) {
            Text(stringResource(R.string.seg_add))
        }
        when {
            low == null || high == null || high < low -> HelpText(R.string.seg_invalid_frequency)
            plan == null -> HelpText(R.string.seg_enter_elements)
            plan.issue != null -> SegmentationIssue(plan)
            else -> {
                Text(stringResource(R.string.seg_result_at, matchingNumber(context, high / 1e6, 6)),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("seg-result"))
                SegmentTable(elements, plan.counts, plan.wavelength, feed)
            }
        }
        SegmentationReference { openSourceWithFeedback(context, DK7ZB_SEGMENTATION_SOURCE) }
    }
}

@Composable
internal fun SegmentationIssue(plan: OpenNec.SegmentPlan) {
    val message = when (plan.issue) {
        OpenNec.SegmentationIssue.INPUT, null -> R.string.seg_invalid_frequency
        OpenNec.SegmentationIssue.POSITIONS -> R.string.seg_invalid_positions
        OpenNec.SegmentationIssue.ELEMENT -> R.string.seg_invalid_element
        OpenNec.SegmentationIssue.STEP -> R.string.seg_invalid_step
        OpenNec.SegmentationIssue.LIMIT -> R.string.seg_limit
    }
    Text(plan.elementIndex?.let { stringResource(message, it + 1) } ?: stringResource(message),
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun SegmentTable(elements: List<Element>, counts: List<Int>, wavelength: Double, feed: Int) {
    val context = LocalContext.current
    fun number(value: Double) = matchingNumber(context, value, 3)
    SegmentDiagram(elements, counts, feed)
    Text(stringResource(R.string.seg_feed_caption, counts[feed] / 2 + 1, feed + 1))
    Text(stringResource(R.string.seg_total, counts.sum(), number(wavelength)), style = MaterialTheme.typography.bodySmall)
    val heads = listOf(stringResource(R.string.seg_element_short), "N", "Δ", "Δ/λ", "Δ/a")
    SegmentRow(heads)
    elements.forEachIndexed { index, element ->
        val step = element.length / counts[index]
        HorizontalDivider()
        SegmentRow(listOf((index + 1).toString(), counts[index].toString(),
            stringResource(R.string.seg_mm, number(step * 1000)), number(step / wavelength), number(step * 2 / element.diameter)))
    }
}

@Composable
internal fun SegmentRow(values: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value -> Text(value, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
internal fun SegmentDiagram(elements: List<Element>, counts: List<Int>, feed: Int) {
    val description = stringResource(R.string.seg_diagram)
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = description }) {
        val center = size.height / 2
        val margin = 18.dp.toPx()
        val span = elements.last().position.takeIf { it > 0 } ?: 1.0
        val maxLength = elements.maxOf { it.length }
        val axisEnd = size.width - 5.dp.toPx()
        val arrow = 7.dp.toPx()
        drawLine(colors.outline, Offset(5.dp.toPx(), center), Offset(axisEnd, center))
        drawLine(colors.outline, Offset(axisEnd - arrow, center - arrow / 2), Offset(axisEnd, center))
        drawLine(colors.outline, Offset(axisEnd - arrow, center + arrow / 2), Offset(axisEnd, center))
        elements.forEachIndexed { index, element ->
            val x = if (elements.size == 1) size.width / 2 else margin +
                (size.width - 2 * margin) * (element.position / span).toFloat()
            val length = (size.height - margin) * (element.length / maxLength).toFloat()
            val step = length / counts[index]
            repeat(counts[index]) { segment ->
                val y = center - length / 2 + segment * step
                val active = index == feed && segment == counts[index] / 2
                val gap = minOf(1.dp.toPx(), step / 4)
                drawLine(if (active) colors.tertiary else colors.primary, Offset(x, y + gap),
                    Offset(x, y + step - gap), if (active) 5.dp.toPx() else 2.dp.toPx())
            }
            if (index == feed) drawCircle(colors.tertiary, 3.dp.toPx(), Offset(x, center))
        }
    }
}

@Composable
private fun SegmentationReference(openSource: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton({ expanded = !expanded }) {
        Text(stringResource(R.string.seg_help) + if (expanded) " ▴" else " ▾")
    }
    if (!expanded) return
    HelpText(R.string.seg_rules)
    HelpText(R.string.seg_reference_conditions)
    HelpText(R.string.seg_reference_results)
    SegmentRow(listOf("N", "R", "X", "G", "F/B", "AGT"))
    val context = LocalContext.current
    fun number(value: Double) = matchingNumber(context, value, 3)
    listOf(
        listOf(5.0, 31.474, -3.763, 6.28, 14.49, 0.97709),
        listOf(11.0, 28.642, 1.081, 6.55, 14.39, 0.99615),
        listOf(21.0, 27.597, 3.278, 6.65, 14.17, 0.99903),
        listOf(41.0, 27.029, 4.627, 6.71, 13.97, 0.99982),
        listOf(81.0, 26.670, 5.469, 6.74, 13.81, 1.0),
    ).forEach { row ->
        HorizontalDivider()
        SegmentRow(row.map(::number))
    }
    TextButton(openSource) { Text(stringResource(R.string.seg_source)) }
    HelpText(R.string.seg_reference_ground)
    GroundDiagram()
    Text(stringResource(R.string.seg_ground_diagram), style = MaterialTheme.typography.bodySmall)
    val models = listOf(R.string.seg_ground_perfect, R.string.seg_ground_finite, R.string.seg_ground_sommerfeld)
    val values = listOf(
        listOf(24.600, 5.326, 11.90, 28.0), listOf(25.980, 4.760, 10.94, 27.0),
        listOf(26.054, 4.913, 10.96, 26.0), listOf(25.799, 4.450, 12.46, 14.0),
        listOf(26.602, 4.030, 11.91, 14.0), listOf(26.629, 4.075, 11.92, 14.0),
    )
    values.chunked(3).forEachIndexed { index, group ->
        Text(stringResource(R.string.seg_ground_height, number(if (index == 0) 1.03 else 2.07)),
            style = MaterialTheme.typography.titleSmall)
        SegmentRow(listOf("R (Ω)", "X (Ω)", "G (dBi)", stringResource(R.string.seg_elevation)))
        group.forEachIndexed { ground, row ->
            HorizontalDivider()
            Text(stringResource(models[ground]), style = MaterialTheme.typography.bodySmall)
            SegmentRow(row.dropLast(1).map(::number) + "${row.last().toInt()}°")
        }
    }
}

@Composable
private fun GroundDiagram() {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.seg_ground_diagram)
    Canvas(Modifier.fillMaxWidth().height(74.dp).semantics { contentDescription = description }) {
        val w = size.width / 3
        val y = size.height * .24f
        val ground = size.height * .56f
        fun line(index: Int, level: Float, color: androidx.compose.ui.graphics.Color, width: Float = .55f) {
            val center = w * (index + .5f)
            drawLine(color, Offset(center - w * width / 2, level), Offset(center + w * width / 2, level),
                2.dp.toPx())
        }
        repeat(3) { index -> line(index, y, colors.primary) }
        line(1, ground, colors.outline, .85f)
        line(1, ground * 2 - y, colors.primary.copy(alpha = .45f))
        drawRect(colors.surfaceContainerHighest, topLeft = Offset(2 * w + w * .075f, ground),
            size = androidx.compose.ui.geometry.Size(w * .85f, size.height - ground))
        line(2, ground, colors.outline, .85f)
        drawLine(colors.tertiary, Offset(w * 2.85f, y), Offset(w * 2.85f, ground))
    }
}
