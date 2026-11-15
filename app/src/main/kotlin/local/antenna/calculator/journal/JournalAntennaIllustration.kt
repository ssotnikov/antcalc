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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.ui.MoxonAntennaArt
import local.antenna.calculator.ui.MoxonView
import local.antenna.calculator.ui.RotatingAntenna
import local.antenna.calculator.ui.cantennaVector
import local.antenna.calculator.ui.HaloVariant
import local.antenna.calculator.ui.haloVector

@Composable
internal fun JournalAntennaIllustration(type: AntennaType, modifier: Modifier = Modifier) {
    if (type == AntennaType.MOXON) {
        MoxonAntennaArt(MoxonView.GENERIC, null, modifier.testTag("journal-antenna-image-MOXON"))
        return
    }
    if (type == AntennaType.HB9CV) {
        RotatingAntenna("hb-0", "HB9CV", modifier.testTag("journal-antenna-image-HB9CV"), interactive = false, baseHeight = 144.dp)
        return
    }
    if (type == AntennaType.CANTENNA) {
        RotatingAntenna("cantenna-journal", stringResource(R.string.cantenna_title),
            modifier.testTag("journal-antenna-image-CANTENNA"), geometry = remember { cantennaVector() },
            interactive = false, baseHeight = 144.dp)
        return
    }
    if (type == AntennaType.HALO_CB) {
        RotatingAntenna("halo-journal", stringResource(R.string.halo_title),
            modifier.testTag("journal-antenna-image-HALO_CB"), geometry = remember { haloVector(HaloVariant.ROUND) },
            interactive = false, baseHeight = 144.dp)
        return
    }
    val painter = when (type) {
        AntennaType.HB9CV -> error("HB9CV is drawn directly")
        AntennaType.CANTENNA -> error("Cantenna is drawn directly")
        AntennaType.HALO_CB -> error("Halo CB is drawn directly")
        AntennaType.MOXON -> error("Moxon is drawn directly")
        AntennaType.SLIM_JIM -> painterResource(R.drawable.slim_jim_antenna)
        AntennaType.YAGI -> painterResource(R.drawable.yagi_r3_hero)
        AntennaType.J_POLE -> painterResource(R.drawable.j_pole_antenna)
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
