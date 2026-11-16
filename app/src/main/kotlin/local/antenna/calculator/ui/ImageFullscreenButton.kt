package local.antenna.calculator.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import local.antenna.calculator.R

@Composable
internal fun ImageFullscreenButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val icon = if (expanded) R.drawable.ic_zoom_out else R.drawable.ic_zoom_in
    val label = if (expanded) R.string.image_exit_fullscreen else R.string.image_expand_description
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(icon),
            stringResource(label, description),
            tint = Color.Unspecified,
        )
    }
}
