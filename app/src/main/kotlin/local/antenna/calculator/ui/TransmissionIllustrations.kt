package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun ReflectionIllustrations() {
    val colors = MaterialTheme.colorScheme
    Illustration(R.string.matching_waves_title, R.string.matching_waves_help) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.matching_wave_source), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.matching_wave_load), style = MaterialTheme.typography.bodySmall)
        }
        Canvas(Modifier.fillMaxWidth().height(54.dp)) {
            val left = 8.dp.toPx()
            val right = size.width - left
            val head = 7.dp.toPx()
            val stroke = 2.dp.toPx()
            val middle = size.height / 2
            drawLine(colors.outline, Offset(left, middle), Offset(right, middle), stroke)
            val forward = middle / 2
            drawLine(colors.primary, Offset(left, forward), Offset(right, forward), stroke)
            drawLine(colors.primary, Offset(right - head, forward - head), Offset(right, forward), stroke)
            drawLine(colors.primary, Offset(right - head, forward + head), Offset(right, forward), stroke)
            val reflected = middle * 1.5f
            drawLine(colors.tertiary, Offset(right, reflected), Offset(left, reflected), stroke)
            drawLine(colors.tertiary, Offset(left + head, reflected - head), Offset(left, reflected), stroke)
            drawLine(colors.tertiary, Offset(left + head, reflected + head), Offset(left, reflected), stroke)
        }
    }
    Illustration(R.string.matching_balance_title, R.string.matching_balance_help) {
        ExampleBar(8f / 9f, 1f / 9f)
        FigureKey(colors.primary, R.string.matching_balance_accepted)
        FigureKey(colors.tertiary, R.string.matching_balance_reflected)
    }
}

@Composable
internal fun CableIllustrations() {
    val colors = MaterialTheme.colorScheme
    Illustration(R.string.cable_wave_title, R.string.cable_wave_help) {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            // The axis and labels span exactly one wavelength.
            val start = size.width * .1f
            val span = size.width * .8f
            val center = size.height / 2
            for (quarter in 0..4) {
                val x = start + span * quarter / 4
                drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            drawLine(colors.outline, Offset(start, center), Offset(start + span, center), 1.dp.toPx())
            val wave = Path().apply {
                moveTo(start, center)
                for (step in 1..64) {
                    val fraction = step / 64f
                    val height = sin(2 * PI * fraction).toFloat() * size.height * .36f
                    lineTo(start + span * fraction, center - height)
                }
            }
            drawPath(wave, colors.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth()) {
            for (degrees in 0..360 step 90) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cable_degrees, degrees.toString()), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Illustration(R.string.cable_turns_title, R.string.cable_turns_help) {
        ExampleBar(7f / 8f, .328302f / 8f, divisions = 8)
        FigureKey(colors.primary, R.string.cable_turns_whole)
        FigureKey(colors.tertiary, R.string.cable_turns_remainder)
    }
}

@Composable
private fun Illustration(title: Int, explanation: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
        HelpText(explanation)
    }
}

@Composable
private fun ExampleBar(first: Float, second: Float, divisions: Int = 1) {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceVariant)) {
        drawRect(colors.primary, size = Size(size.width * first, size.height))
        drawRect(colors.tertiary, topLeft = Offset(size.width * first, 0f), size = Size(size.width * second, size.height))
        for (division in 1 until divisions) {
            val x = size.width * division / divisions
            drawLine(colors.surface, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
    }
}

@Composable
private fun FigureKey(color: Color, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}
