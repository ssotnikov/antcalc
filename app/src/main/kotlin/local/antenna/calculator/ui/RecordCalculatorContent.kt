package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

@Composable
internal fun RecordCalculatorContent(request: RecordCalculatorRequest?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCalculatorRecord provides request) {
        if (request != null && request.snapshot == null) {
            val context = LocalContext.current
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.journal_reopen_partial) + request.knownValues(context),
                        Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { content() }
            }
        } else content()
    }
}
