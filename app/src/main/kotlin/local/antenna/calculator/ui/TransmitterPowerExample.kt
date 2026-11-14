package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

private data class PowerExample(val load: String, val swr: String, val limit: Int, val watts: String)

private val powerExamples = listOf(
    PowerExample("50", "1:1", R.string.matching_pa_limit_both, "100"),
    PowerExample("25", "2:1", R.string.matching_pa_limit_current, "50"),
    PowerExample("100", "2:1", R.string.matching_pa_limit_voltage, "50"),
    PowerExample("40 + j30", "2:1", R.string.matching_pa_limit_complex, "80"),
)

@Composable
internal fun TransmitterPowerExample() {
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.source_pa_current_mode_url)
    Text(
        stringResource(R.string.matching_pa_title),
        modifier = Modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleMedium,
    )
    HelpText(R.string.matching_pa_intro)
    HelpText(R.string.matching_pa_model)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tableWidth = maxOf(maxWidth, 480.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
                ScrollableTable(Modifier.width(tableWidth)) {
                    PowerExampleRow(listOf(
                        stringResource(R.string.matching_pa_load),
                        stringResource(R.string.matching_pa_swr),
                        stringResource(R.string.matching_pa_limit),
                        stringResource(R.string.matching_pa_power),
                    ), header = true)
                    for (example in powerExamples) {
                        HorizontalDivider()
                        PowerExampleRow(listOf(example.load, example.swr, stringResource(example.limit), example.watts))
                    }
                }
            }
        }
    }
    TextButton(onClick = { openSourceWithFeedback(context, sourceUrl) }) {
        Text(stringResource(R.string.source_pa_current_mode))
    }
    HelpText(R.string.matching_pa_result)
    HelpText(R.string.matching_pa_link_budget)
}

@Composable
private fun PowerExampleRow(values: List<String>, header: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val weights = listOf(1f, 0.9f, 2.4f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        values.forEachIndexed { index, value ->
            Text(
                value,
                Modifier.weight(weights[index]).then(if (header) Modifier.semantics { heading() } else Modifier),
                style = style,
                color = if (header) colors.secondary else colors.onSurface,
                textAlign = if (index == 1 || index == 3) TextAlign.End else TextAlign.Start,
            )
        }
    }
}
