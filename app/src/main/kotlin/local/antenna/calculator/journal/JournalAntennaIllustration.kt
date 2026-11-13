package local.antenna.calculator.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.trimming.AntennaType

@Composable
internal fun JournalAntennaIllustration(type: AntennaType, modifier: Modifier = Modifier) {
    val painter = when (type) {
        AntennaType.FULL_WAVE_LOOP -> painterResource(R.drawable.full_wave_loop_square)
        AntennaType.MONOPOLE, AntennaType.SYMMETRIC_DIPOLE -> {
            val antennas = ImageBitmap.imageResource(R.drawable.sizing_antennas)
            remember(antennas, type) {
                // Pixel bounds in the shared nodpi artwork, including transparent margins.
                when (type) {
                    AntennaType.MONOPOLE -> BitmapPainter(antennas, IntOffset(150, 0), IntSize(535, 724))
                    else -> BitmapPainter(antennas, IntOffset(780, 305), IntSize(1325, 419))
                }
            }
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.fillMaxWidth().height(144.dp).testTag("journal-antenna-image-${type.name}"),
    )
}
