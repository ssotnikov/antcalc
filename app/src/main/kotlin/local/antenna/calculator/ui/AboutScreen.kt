package local.antenna.calculator.ui

import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.BuildConfig
import local.antenna.calculator.R
import local.antenna.calculator.core.trimming.AdjustmentDirection

@Composable
fun AboutScreen() {
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AboutHeading(R.string.about_dimensions_heading)
        FormulaPlate(R.string.wavelength, R.drawable.formula_wavelength, R.string.formula_wavelength_alt, "formula-wavelength")
        HelpText(R.string.about_summary)
        FormulaPlate(R.string.radiator_length, R.drawable.formula_monopole, R.string.formula_monopole_alt, "formula-monopole")
        AntennaDiagram(dipole = false)
        FormulaPlate(R.string.dipole_total, R.drawable.formula_dipole, R.string.formula_dipole_alt, "formula-dipole")
        AntennaDiagram(dipole = true)
        HelpText(R.string.formula_lengths_key)
        HelpText(R.string.sizing_note)
        HelpText(R.string.conductor_note)
        FormulaPlate(R.string.ideal_five_eighths, R.drawable.formula_five_eighths, R.string.formula_five_eighths_alt, "formula-five-eighths")
        HelpText(R.string.five_eighths_note)
        FormulaPlate(R.string.trimming, R.drawable.formula_trimming, R.string.formula_trimming_alt, "formula-trimming")
        HelpText(R.string.about_trimming)
        FormulaPlate(R.string.total_adjustment, R.drawable.formula_delta, R.string.formula_delta_alt, "formula-delta")
        FormulaPlate(R.string.arm_adjustment, R.drawable.formula_arm, R.string.formula_arm_alt, "formula-arm")
        HelpText(R.string.formula_delta_note)
        HelpText(R.string.dipole_note)
        TuningDiagram(AdjustmentDirection.SHORTEN)
        AboutHeading(R.string.about_measurement_heading)
        HelpText(R.string.resonance_note)
        HelpText(R.string.about_measurement_note)
        HelpText(R.string.trimming_note)
        Text(stringResource(R.string.materials_title), style = MaterialTheme.typography.titleLarge)
        HelpText(R.string.materials_note)
        MaterialLink(R.string.source_nist, R.string.source_nist_description, R.string.source_nist_url, "source-nist")
        MaterialLink(R.string.source_rsgb, R.string.source_rsgb_description, R.string.source_rsgb_url, "source-rsgb")
        MaterialLink(R.string.source_arrl, R.string.source_arrl_description, R.string.source_arrl_url, "source-arrl")
        MaterialLink(R.string.source_ti, R.string.source_ti_description, R.string.source_ti_url, "source-ti")
        MaterialLink(R.string.source_nashua, R.string.source_nashua_description, R.string.source_nashua_url, "source-nashua")
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME))
    }
}

@Composable
private fun AboutHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun MaterialLink(title: Int, description: Int, address: Int, tag: String) {
    val context = LocalContext.current
    val url = stringResource(address)
    OutlinedCard(onClick = {
        openSourceWithFeedback(context, url)
    }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            Text(url.toUri().host.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormulaPlate(title: Int, image: Int, description: Int, tag: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Image(painter = painterResource(image), contentDescription = stringResource(description),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(100.dp).testTag(tag))
        }
    }
}
