package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.state.AppLanguage

private data class Material(
    val id: String,
    val title: Int,
    val description: Int,
    val address: Int,
    val publisher: String,
    val language: AppLanguage,
    val section: String? = null,
)

private data class ReferenceGroup(val title: Int, val materials: List<Material>)

private val referenceGroups = listOf(
    ReferenceGroup(R.string.references_fundamentals, listOf(
        Material("nist", R.string.source_nist, R.string.source_nist_description, R.string.source_nist_url, "NIST", AppLanguage.ENGLISH),
        Material("itu", R.string.source_itu, R.string.source_itu_description, R.string.source_itu_url, "МСЭ / ITU", AppLanguage.RUSSIAN, "§ 4.3"),
        Material("antenna-geometry", R.string.source_antenna_geometry, R.string.source_antenna_geometry_description, R.string.source_antenna_geometry_url, "ARRL", AppLanguage.ENGLISH, "Unit 4"),
    )),
    ReferenceGroup(R.string.references_construction, listOf(
        Material("rsgb", R.string.source_rsgb, R.string.source_rsgb_description, R.string.source_rsgb_url, "RSGB", AppLanguage.ENGLISH),
        Material("nasa", R.string.source_nasa, R.string.source_nasa_description, R.string.source_nasa_url, "NASA Radio JOVE", AppLanguage.ENGLISH, "§ 3.1"),
        Material("verticals", R.string.source_verticals, R.string.source_verticals_description, R.string.source_verticals_url, "ARRL", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_five_eighths, listOf(
        Material("five-eighths", R.string.source_five_eighths, R.string.source_five_eighths_description, R.string.source_five_eighths_url, "Dale Hunt, WB6BYU", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_tuning, listOf(
        Material("rig-tuning", R.string.source_rig_tuning, R.string.source_rig_tuning_description, R.string.source_rig_tuning_url, "RigExpert", AppLanguage.RUSSIAN, "§ 5.1.2"),
        Material("nashua", R.string.source_nashua, R.string.source_nashua_description, R.string.source_nashua_url, "Martin Blustine · Nashua Area Radio Society", AppLanguage.ENGLISH, "Method II"),
        Material("analyzer", R.string.source_analyzer, R.string.source_analyzer_description, R.string.source_analyzer_url, "ARRL", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_matching, listOf(
        Material("kadamm", R.string.source_kadamm, R.string.source_kadamm_description, R.string.source_kadamm_url, "КАДАММ", AppLanguage.RUSSIAN),
        Material("rohde", R.string.source_rohde, R.string.source_rohde_description, R.string.source_rohde_url, "Rohde & Schwarz", AppLanguage.ENGLISH, "§ 8"),
        Material("reflection", R.string.source_reflection, R.string.source_reflection_description, R.string.source_reflection_url, "Analog Devices", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_cable, listOf(
        Material("rig-cable", R.string.source_rig_cable, R.string.source_rig_cable_description, R.string.source_rig_cable_url, "RigExpert", AppLanguage.RUSSIAN, "§ 5.2.5"),
        Material("steer", R.string.source_steer, R.string.source_steer_description, R.string.source_steer_url, "Michael Steer · NC State University", AppLanguage.ENGLISH, "§ 2.2"),
        Material("cable-delay", R.string.source_cable_delay, R.string.source_cable_delay_description, R.string.source_cable_delay_url, "Keysight", AppLanguage.ENGLISH),
    )),
)

@Composable
internal fun ReferencesScreen(language: AppLanguage) {
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(20.dp).testTag("references-screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HelpText(R.string.materials_note)
        referenceGroups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(group.title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                group.materials.sortedBy { it.language != language }.forEach { MaterialLink(it) }
            }
        }
    }
}

@Composable
private fun MaterialLink(material: Material) {
    val context = LocalContext.current
    val url = stringResource(material.address)
    val language = stringResource(
        if (material.language == AppLanguage.RUSSIAN) R.string.references_language_ru else R.string.references_language_en,
    )
    OutlinedCard(
        onClick = { openSourceWithFeedback(context, url) },
        modifier = Modifier.fillMaxWidth().testTag("source-${material.id}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(material.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                listOfNotNull(material.publisher, language, material.section).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HelpText(material.description)
        }
    }
}
