package local.antenna.calculator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import local.antenna.calculator.R

private const val MaxPhotoZoom = 5f

@Composable
internal fun FullscreenImage(
    image: ImageBitmap,
    description: String?,
    onClose: () -> Unit,
    title: String? = null,
) {
    val zoomState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    var zoom by zoomState
    var offset by offsetState
    val percent = (zoom * 100).roundToInt()
    val zoomLabel = stringResource(R.string.photo_zoom_level, percent)
    val tag = if (title == null) "journal-photo-fullscreen" else "yagi-illustration-fullscreen"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        if (title == null) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ZoomableImage(image, description, tag, zoomState, offsetState, zoomLabel,
                    modifier = Modifier.fillMaxSize())
                FilledIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)
                    .safeDrawingPadding().padding(8.dp).testTag("$tag-close")) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.journal_close))
                }
            }
        } else {
            val colors = MaterialTheme.colorScheme
            Column(Modifier.fillMaxSize().background(colors.surface).safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().background(colors.surfaceContainer).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("$tag-close")) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.journal_close))
                    }
                    Text(title, Modifier.weight(1f).padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                }
                ZoomableImage(image, description, tag, zoomState, offsetState, zoomLabel,
                    modifier = Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.image_zoom_hint), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    val smaller = stringResource(R.string.image_zoom_out)
                    val larger = stringResource(R.string.image_zoom_in)
                    val reset = stringResource(R.string.image_zoom_reset, percent)
                    TextButton(onClick = {
                        val next = (zoom / 1.5f).coerceAtLeast(1f)
                        offset = if (next == 1f) Offset.Zero else offset * (next / zoom)
                        zoom = next
                    }, enabled = zoom > 1f, modifier = Modifier.semantics { contentDescription = smaller }) { Text("−") }
                    TextButton(onClick = {
                        zoom = 1f
                        offset = Offset.Zero
                    },
                        modifier = Modifier.testTag("$tag-reset").semantics { contentDescription = reset }) {
                        Text(stringResource(R.string.image_zoom_percent, percent))
                    }
                    TextButton(onClick = {
                        val next = (zoom * 1.5f).coerceAtMost(MaxPhotoZoom)
                        offset *= next / zoom
                        zoom = next
                    }, enabled = zoom < MaxPhotoZoom, modifier = Modifier.semantics { contentDescription = larger }) { Text("+") }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    image: ImageBitmap,
    description: String?,
    tag: String,
    zoomState: MutableFloatState,
    offsetState: MutableState<Offset>,
    zoomLabel: String,
    modifier: Modifier,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentImage by rememberUpdatedState(image)
    var zoom by zoomState
    var offset by offsetState
    Box(modifier.clipToBounds().onSizeChanged { viewport = it }
        .testTag(tag)
        .semantics { stateDescription = zoomLabel }
        .pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoomChange, _ ->
                val nextZoom = (zoom * zoomChange).coerceIn(1f, MaxPhotoZoom)
                val center = Offset(size.width / 2f, size.height / 2f)
                val relative = centroid - center
                val bounded = offset.limitToImage(currentImage, size, zoom)
                val moved = relative - (relative - bounded) * (nextZoom / zoom) + pan
                offset = moved.limitToImage(currentImage, size, nextZoom)
                zoom = nextZoom
            }
        }) {
        Image(image, description, Modifier.fillMaxSize().graphicsLayer {
            val position = offset.limitToImage(image, viewport, zoom)
            scaleX = zoom
            scaleY = zoom
            translationX = position.x
            translationY = position.y
        }, contentScale = ContentScale.Fit)
    }
}

private fun Offset.limitToImage(image: ImageBitmap, viewport: IntSize, zoom: Float): Offset {
    val fit = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    val maxX = ((image.width * fit * zoom - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((image.height * fit * zoom - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
}
