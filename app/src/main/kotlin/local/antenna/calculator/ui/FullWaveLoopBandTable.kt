package local.antenna.calculator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.LoopModel
import local.antenna.calculator.core.layout.LoopShape
import local.antenna.calculator.core.layout.fullWaveLoop
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit

private data class LoopBand(val wavelength: Int, val mhz: Double, val unit: LengthUnit = LengthUnit.METERS)

private val referenceBands = listOf(
    LoopBand(160, 1.850), LoopBand(80, 3.650), LoopBand(40, 7.100), LoopBand(30, 10.120),
    LoopBand(20, 14.200), LoopBand(17, 18.100), LoopBand(15, 21.200), LoopBand(12, 24.940),
    LoopBand(10, 28.500), LoopBand(6, 50.100), LoopBand(2, 145.000),
    LoopBand(70, 433.000, LengthUnit.CENTIMETERS),
)

@Composable
internal fun FullWaveLoopBandTable() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tableScroll = rememberScrollState()
    val state = stringResource(if (expanded) R.string.smith_expanded else R.string.smith_collapsed)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { heading(); stateDescription = state }
                .testTag("loop-band-table-toggle").padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.loop_band_title), Modifier.weight(1f),
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
            Text(if (expanded) "▾" else "▸", Modifier.clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.secondary)
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HelpText(R.string.loop_band_note)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tableWidth = maxOf(maxWidth, 365.dp * maxOf(1f, LocalDensity.current.fontScale))
                    Column(Modifier.horizontalScroll(tableScroll).width(tableWidth).testTag("loop-band-table")) {
                        LoopBandRow(
                            listOf(R.string.loop_band_column, R.string.loop_frequency, R.string.loop_band_perimeter,
                                R.string.loop_band_square, R.string.loop_band_triangle).map { stringResource(it) },
                            header = true,
                        )
                        val locale = LocalConfiguration.current.locales[0]
                        val frequencies = remember(locale) { FrequencyNumberFormat(locale) }
                        val lengths = remember(locale) {
                            NumberFormat.getNumberInstance(locale).apply {
                                isGroupingUsed = false
                                minimumFractionDigits = 2
                                maximumFractionDigits = 2
                            }
                        }
                        for (band in referenceBands) {
                            val square = checkNotNull(fullWaveLoop(LoopShape.SQUARE, LoopModel.EMPIRICAL, frequencyMhz = band.mhz))
                            val triangle = checkNotNull(fullWaveLoop(LoopShape.TRIANGLE, LoopModel.EMPIRICAL, frequencyMhz = band.mhz))
                            LoopBandRow(listOf(
                                stringResource(R.string.value_unit, band.wavelength.toString(), stringResource(band.unit.labelResource())),
                                frequencies.inUnit(band.mhz, FrequencyUnit.MEGAHERTZ),
                                lengths.format(square.perimeterMeters),
                                lengths.format(square.sideMeters),
                                lengths.format(triangle.sideMeters),
                            ))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoopBandRow(values: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val weights = listOf(0.9f, 1.0f, 1.0f, 1.2f, 1.4f)
        values.forEachIndexed { index, value ->
            Text(value, Modifier.weight(weights[index]),
                color = if (header) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End)
        }
    }
    HorizontalDivider()
}
