package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

internal data class CalculatorEquation(val expression: String, val description: Int, val tag: String? = null)

@Composable
internal fun CalculatorFormulas(vararg equations: CalculatorEquation) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.calculation_formulas_label),
                Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            for (equation in equations) {
                val spoken = stringResource(equation.description)
                val tag = equation.tag?.let { Modifier.testTag(it) } ?: Modifier
                SelectableText(equation.expression, tag.semantics { contentDescription = spoken },
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
