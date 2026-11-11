package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class UpdateSnackbarVisuals(
    override val message: String,
    override val duration: SnackbarDuration,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
}

@Composable
internal fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val visuals = data.visuals
        if (visuals is UpdateSnackbarVisuals) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val style = MaterialTheme.typography.bodyMedium
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                // A zero provides a stable character width for proportional fonts.
                val inset = with(density) { measurer.measure("000", style, softWrap = false).size.width.toDp() }
                val messageWidth = measurer.measure(visuals.message, style, softWrap = false).size.width
                val fits = with(density) { messageWidth + 2 * inset.roundToPx() + 16.dp.roundToPx() } <= constraints.maxWidth
                if (fits) {
                    Surface(
                        modifier = Modifier.testTag("compact-update-notice"),
                        shape = SnackbarDefaults.shape,
                        color = SnackbarDefaults.color,
                        contentColor = SnackbarDefaults.contentColor,
                        shadowElevation = 6.dp,
                    ) {
                        Box(Modifier.padding(horizontal = inset, vertical = 14.dp)) {
                            Text(visuals.message, style = style, textAlign = TextAlign.Center, softWrap = false)
                        }
                    }
                } else {
                    Snackbar(data)
                }
            }
        } else {
            Snackbar(data)
        }
    }
}
