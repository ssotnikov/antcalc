package local.antenna.calculator.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import local.antenna.calculator.R

private const val MaxPhotoZoom = 5f

@Composable
internal fun FullscreenJournalPhoto(image: ImageBitmap, description: String?, onClose: () -> Unit) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    val zoomLabel = stringResource(R.string.photo_zoom_level, (zoom * 100).roundToInt())
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
            Box(Modifier.fillMaxSize()
                .testTag("journal-photo-fullscreen")
                .semantics { stateDescription = zoomLabel }
                .pointerInput(image) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, MaxPhotoZoom)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val relative = centroid - center
                        val moved = relative - (relative - offset) * (nextZoom / zoom) + pan
                        val fit = minOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
                        val maxX = ((image.width * fit * nextZoom - size.width) / 2f).coerceAtLeast(0f)
                        val maxY = ((image.height * fit * nextZoom - size.height) / 2f).coerceAtLeast(0f)
                        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                        zoom = nextZoom
                    }
                }) {
                Image(image, description, Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                }, contentScale = ContentScale.Fit)
            }
            FilledIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)
                .safeDrawingPadding().padding(8.dp).testTag("journal-photo-fullscreen-close")) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.journal_close))
            }
        }
    }
}
