package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.log2
import local.antenna.calculator.R
import local.antenna.calculator.core.Cantenna

// Rounded rows reproduced in the supplied reference. Live calculations use Cantenna.analyze.
private val goughRows: List<List<Number>> = listOf(
    listOf(73, 2407, 3145, 752, 188, 564),
    listOf(75, 2343, 3061, 440, 110, 330),
    listOf(80, 2197, 2869, 282, 70.6, 212),
    listOf(85, 2067, 2701, 231, 57.8, 173),
    listOf(90, 1953, 2551, 205, 51.2, 154),
    listOf(92, 1910, 2495, 197, 49.4, 148),
    listOf(96, 1831, 2391, 186, 46.5, 139),
    listOf(99, 1775, 2319, 179, 44.8, 134),
)

@Composable
internal fun CantennaReferencePanels(
    frequency: Double?,
    diameter: Double?,
    band: Cantenna.Band?,
    format: CantennaText,
) {
    CantennaReferencePanel(R.string.cantenna_reference_table, "cantenna-reference-table") {
        HelpText(R.string.cantenna_reference_table_note)
        val headers = listOf("D", "fTE₁₁", "fTM₀₁", "λg", "λg/4", "3λg/4", "λ₀/4").mapIndexed { index, label ->
            "$label · " + stringResource(if (index in 1..2) R.string.mhz else R.string.hb9cv_aerial_mm)
        }
        ScrollableTable {
            CantennaReferenceRow(headers, header = true)
            goughRows.forEach { row ->
                CantennaReferenceRow((row + 30.7).map { format.number(it.toDouble()) }, header = false)
            }
        }
        HelpText(R.string.cantenna_reference_wifi)
    }
    CantennaReferencePanel(R.string.cantenna_curve, "cantenna-reference-curve") {
        if (frequency == null || band == null) HelpText(R.string.cantenna_curve_input)
        else CantennaGuideCurve(frequency, diameter, band, format)
    }
    CantennaReferencePanel(R.string.cantenna_construction, "cantenna-reference-build") {
        HelpText(R.string.cantenna_reference_axial)
        CantennaAperture()
        HelpText(R.string.cantenna_reference_polarization)
        CantennaDiagram(.100, .132, .044, .031, .100, .170, null, tuning = false)
        HelpText(R.string.cantenna_reference_flare)
        HelpText(R.string.cantenna_reference_gain)
        HelpText(R.string.cantenna_reference_build_1)
        HelpText(R.string.cantenna_reference_build_2)
        HelpText(R.string.cantenna_reference_build_3)
    }
}

@Composable
private fun CantennaReferencePanel(label: Int, tag: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.jpole_expanded else R.string.jpole_collapsed)
    Column {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag(tag).semantics { stateDescription = state },
        ) { Text(stringResource(label)) }
        if (expanded) content()
    }
}

@Composable
private fun CantennaReferenceRow(cells: List<String>, header: Boolean) {
    Row {
        cells.forEach { cell ->
            Text(cell, Modifier.width(106.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                style = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        }
    }
    HorizontalDivider()
}

@Composable
private fun CantennaAperture() {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val electric = MaterialTheme.colorScheme.tertiary
    val description = stringResource(R.string.cantenna_reference_aperture)
    Canvas(Modifier.fillMaxWidth().height(156.dp).testTag("cantenna-aperture")
        .semantics { contentDescription = description }) {
        val radius = minOf(size.height, size.width) * .40f
        val stroke = 2.dp.toPx()
        drawCircle(outline, radius, center, style = Stroke(stroke))
        drawLine(outline, center.copy(x = center.x - radius), center.copy(x = center.x + radius), stroke)
        val feed = center.copy(y = center.y + radius)
        drawLine(electric, feed.copy(y = feed.y + 8.dp.toPx()), feed.copy(y = center.y + radius * .50f), stroke * 2)
        for (position in listOf(-.43f, 0f, .43f)) {
            val x = center.x + radius * position
            val upper = center.copy(x = x, y = center.y - radius * .57f)
            val lower = center.copy(x = x, y = center.y + radius * .29f)
            drawLine(electric, lower, upper, stroke)
            val head = radius * .11f
            drawLine(electric, upper + Offset(-head, head), upper, stroke)
            drawLine(electric, upper + Offset(head, head), upper, stroke)
        }
    }
}

@Composable
private fun CantennaGuideCurve(hz: Double, diameter: Double?, band: Cantenna.Band, format: CantennaText) {
    val focus = Cantenna.Band(hz)
    val (cutoff, upperCutoff) = checkNotNull(Cantenna.diameterRange(focus))
    val range = Cantenna.diameterRange(band)
    val left = cutoff * .88
    val right = upperCutoff * 1.20
    val wavelength = Cantenna.LIGHT_SPEED / hz
    val selected = diameter?.let { Cantenna.analyze(Cantenna.Can(it), hz).guideWavelength }
    val axes = MaterialTheme.colorScheme.outline
    val curve = MaterialTheme.colorScheme.primary
    val area = MaterialTheme.colorScheme.secondary.copy(alpha = .20f)
    val marker = MaterialTheme.colorScheme.tertiary
    val mhz = stringResource(R.string.mhz)
    Text(stringResource(R.string.cantenna_curve_frequency, "${format.number(hz / 1e6)} $mhz"))
    Text(stringResource(R.string.cantenna_curve_x, format.mm(left), format.mm(right)))
    Text(stringResource(R.string.cantenna_curve_y))
    if (range == null) HelpText(R.string.cantenna_interval_none)
    else Text(stringResource(R.string.cantenna_curve_window, format.mm(range.first), format.mm(range.second)))
    if (diameter != null) {
        Text(stringResource(R.string.cantenna_curve_point, format.mm(diameter), format.mm(selected)),
            Modifier.testTag("cantenna-curve-point"))
        if (diameter !in left..right || (selected ?: 0.0) > wavelength * 8) HelpText(R.string.cantenna_curve_offscale)
    }
    Canvas(Modifier.fillMaxWidth().height(170.dp).testTag("cantenna-guide-curve")) {
        val inset = 8.dp.toPx()
        val width = size.width - inset * 2
        val height = size.height - inset * 2
        fun x(d: Double) = inset + ((d - left) / (right - left)).toFloat() * width
        fun y(lambda: Double) = inset + (1 - log2(lambda / wavelength) / 3).toFloat() * height
        range?.let { (start, end) ->
            val a = x(start.coerceIn(left, right))
            val b = x(end.coerceIn(left, right))
            drawRect(area, Offset(a, inset), Size(b - a, height))
        }
        for (ratio in listOf(1, 2, 4, 8)) {
            val level = y(wavelength * ratio)
            drawLine(axes.copy(alpha = .6f), Offset(inset, level), Offset(inset + width, level))
        }
        drawLine(axes, Offset(x(cutoff), inset), Offset(x(cutoff), inset + height), 1.dp.toPx())
        val path = Path()
        var started = false
        for (step in 0..160) {
            val d = left + (right - left) * step / 160
            val lambda = Cantenna.analyze(Cantenna.Can(d), hz).guideWavelength ?: continue
            if (lambda > wavelength * 8) continue
            if (started) path.lineTo(x(d), y(lambda)) else path.moveTo(x(d), y(lambda))
            started = true
        }
        drawPath(path, curve, style = Stroke(2.dp.toPx()))
        if (diameter != null && diameter in left..right && selected != null && selected <= wavelength * 8) {
            drawCircle(marker, 4.dp.toPx(), Offset(x(diameter), y(selected)))
        }
    }
}
