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
internal fun ExpandImageButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_zoom_in),
            stringResource(R.string.image_expand_description, description),
            tint = Color.Unspecified,
        )
    }
}
