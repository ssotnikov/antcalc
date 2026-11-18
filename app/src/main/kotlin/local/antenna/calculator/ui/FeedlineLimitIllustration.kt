package local.antenna.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import local.antenna.calculator.R
import local.antenna.calculator.core.feedline.FeedlineLengthLimit

internal enum class FeedlineIllustration(val title: Int) {
    CABLE(R.string.feed_limit_cable), LINK(R.string.feed_limit_link), CHART(R.string.feed_limit_chart),
}

@Composable
internal fun FeedlineLimitIllustration(
    illustration: FeedlineIllustration,
    calculation: FeedlineMaximumCalculation?, allowed: BigDecimal?, connections: BigDecimal?, status: String?,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    val warm = if (colors.surface.luminance() < .5f) Color(0xFFF3BA80) else Color(0xFF824410)
    val finite = calculation?.limit as? FeedlineLengthLimit.Finite
    val value = when (calculation?.limit) {
        is FeedlineLengthLimit.Finite -> stringResource(R.string.feed_limit_no_more, feedlineLengthText(context, finite!!.metres, true))
        FeedlineLengthLimit.Unbounded -> stringResource(R.string.feed_limit_unbounded)
        FeedlineLengthLimit.Exceeded -> stringResource(R.string.feed_limit_exceeded)
        null -> stringResource(R.string.feedline_pending)
    }
    val graphDescription = stringResource(R.string.feed_limit_graph_description, value)
    Surface(Modifier.fillMaxWidth().testTag("feedline-limit-illustration"),
        color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            status?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = colors.tertiary) }
            Text(value, style = MaterialTheme.typography.titleMedium,
                color = if (calculation?.limit == FeedlineLengthLimit.Exceeded) colors.error else colors.primary)
            if (illustration == FeedlineIllustration.CHART) {
                Text(stringResource(R.string.feed_limit_y_axis), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = graphDescription }) {
                    val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
                    val top = 8.dp.toPx(); val bottom = size.height - 8.dp.toPx()
                    drawLine(colors.outline, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
                    drawLine(colors.outline, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
                    if (finite != null && finite.metres.signum() > 0 && allowed != null && allowed.signum() > 0) {
                        val maximum = allowed.toDouble() * 1.5
                        fun y(loss: Double) = bottom - ((bottom - top) * (loss / maximum).coerceIn(0.0, 1.0)).toFloat()
                        val thresholdY = y(allowed.toDouble())
                        val x = left + (right - left) / 1.4f
                        val extra = connections?.toDouble() ?: 0.0
                        val cable = calculation.cableDecibels.toDouble()
                        drawLine(warm, Offset(left, thresholdY), Offset(right, thresholdY),
                            2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
                        drawLine(colors.primary, Offset(left, y(extra)), Offset(right, y(extra + 1.4 * cable)), 2.dp.toPx())
                        val actualY = y(extra + cable)
                        drawLine(colors.outline, Offset(x, actualY), Offset(x, bottom), 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                        drawCircle(warm, 5.dp.toPx(), Offset(x, actualY))
                    }
                }
                Text(stringResource(R.string.feed_limit_x_axis), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.feed_limit_line_legend), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    Text(allowed?.let { stringResource(R.string.feed_limit_allowance_value,
                        java.text.NumberFormat.getNumberInstance(locale).apply {
                            maximumFractionDigits = 32; isGroupingUsed = false
                        }.format(it)) } ?: stringResource(R.string.feed_limit_allowance_legend), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = warm)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.feed_limit_station), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.feed_limit_antenna), style = MaterialTheme.typography.bodySmall)
                }
                Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = value }) {
                    val cy = size.height * .56f; val radio = Size(48.dp.toPx(), 32.dp.toPx())
                    drawRoundRect(colors.outlineVariant, Offset(0f, cy - radio.height / 2), radio, CornerRadius(5.dp.toPx()))
                    drawRoundRect(colors.primary, Offset(6.dp.toPx(), cy - 9.dp.toPx()), Size(23.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawCircle(colors.primary, 4.dp.toPx(), Offset(38.dp.toPx(), cy + 7.dp.toPx()))
                    val mast = size.width - 18.dp.toPx()
                    drawLine(colors.primary, Offset(mast, cy + 12.dp.toPx()), Offset(mast, cy - 34.dp.toPx()), 2.dp.toPx())
                    drawLine(colors.primary, Offset(mast, cy - 17.dp.toPx()), Offset(mast - 15.dp.toPx(), cy - 32.dp.toPx()), 2.dp.toPx())
                    drawLine(colors.primary, Offset(mast, cy - 17.dp.toPx()), Offset(mast + 15.dp.toPx(), cy - 32.dp.toPx()), 2.dp.toPx())
                    drawLine(colors.outline, Offset(radio.width, cy), Offset(mast, cy), 6.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.primary, Offset(radio.width, cy), Offset(mast, cy), 2.dp.toPx())
                }
            }
            Text(stringResource(R.string.feed_limit_graph_caption), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
    }
}
