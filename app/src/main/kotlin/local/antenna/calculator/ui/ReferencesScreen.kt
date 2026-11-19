package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
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

@Composable
internal fun ReferencesScreen(language: AppLanguage) {
    val scroll = rememberPageScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().cardListBounce(scroll.isScrollInProgress)
            .verticalScroll(scroll, overscrollEffect = null)
            .padding(20.dp).testTag("references-screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HelpText(R.string.materials_note)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReferenceSectionHeading(R.string.cantenna_sources)
            CantennaSource.entries.forEach { source ->
                ReferenceCard(
                    id = "cantenna-${source.name.lowercase()}",
                    title = stringResource(source.title), publisher = source.author, url = source.url,
                    summary = additionalReferenceSummaries.getValue(source.url),
                    terms = source.terms, termsUrl = source.termsUrl,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReferenceSectionHeading(R.string.hb9cv_sources_heading)
            hb9cvReferenceSources.forEach { source ->
                ReferenceCard(
                    id = "hb9cv-${source.name.lowercase()}",
                    title = stringResource(source.title), publisher = stringResource(source.author), url = source.url,
                    summary = additionalReferenceSummaries.getValue(source.url), terms = source.terms,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReferenceSectionHeading(R.string.moxon_title)
            MoxonSource.entries.forEach { source ->
                ReferenceCard(
                    id = "moxon-${source.name.lowercase()}",
                    title = source.title, publisher = source.author, url = source.url,
                    summary = additionalReferenceSummaries.getValue(source.url), terms = source.terms,
                )
            }
        }
        referenceGroups.forEach { group ->
            val eligible = group.materials.filter { !it.interfaceLanguageOnly || it.language == language }
            val materials = if (group.interfaceLanguageOnly ||
                (language == AppLanguage.ENGLISH && !group.showAllSourceLanguages)
            ) {
                eligible.filter { it.language == language }
            } else if (group.preferRussianSources) {
                eligible.sortedBy { it.language != AppLanguage.RUSSIAN }
            } else {
                eligible
            }
            if (materials.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReferenceSectionHeading(group.title)
                if (group.title == R.string.slim_jim_title) {
                    listOf(
                        Triple(R.string.slim_jim_ok1ike_source, "OK1IKE", SLIM_JIM_OK1IKE_URL),
                        Triple(R.string.jpole_ok1ike_hb9acc, "Max Rüegger (HB9ACC)", OK1IKE_HB9ACC_URL),
                        Triple(R.string.slim_jim_sv1jrf_source, "SV1JRF / 5B4AMD", SLIM_JIM_SV1JRF_URL),
                    ).forEachIndexed { index, (title, author, url) ->
                        ReferenceCard(
                            id = "slim-jim-additional-$index", title = stringResource(title),
                            publisher = author, url = url, summary = additionalReferenceSummaries.getValue(url),
                        )
                    }
                }
                materials.forEachIndexed { index, material ->
                    material.subsection?.takeIf { it != materials.getOrNull(index - 1)?.subsection }?.let { subsection ->
                        ReferenceSectionHeading(subsection)
                    }
                    ReferenceCard(material)
                }
            }
        }
    }
}

@Composable
private fun ReferenceSectionHeading(title: Int) {
    Text(stringResource(title), color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ReferenceCard(material: TechnicalReference) {
    ReferenceCard(
        id = material.id, title = stringResource(material.title), publisher = material.publisher,
        url = stringResource(material.address), section = material.section,
        summary = ReferenceSummary(
            material.description,
            material.sourceLanguageLabel ?: if (material.language == AppLanguage.RUSSIAN)
                R.string.references_language_ru else R.string.references_language_en,
        ),
    )
}

@Composable
private fun ReferenceCard(
    id: String,
    title: String,
    publisher: String,
    url: String,
    summary: ReferenceSummary,
    section: String? = null,
    terms: Int? = null,
    termsUrl: String? = null,
) {
    val context = LocalContext.current
    val language = summary.language?.let { stringResource(it) }
    OutlinedCard(
        onClick = { openSourceWithFeedback(context, url) },
        modifier = Modifier.fillMaxWidth().testTag("source-$id"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WebLinkLabel(tint = MaterialTheme.colorScheme.primary) {
                SelectableText(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            SelectableText(
                listOfNotNull(publisher, language, section).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HelpText(summary.description)
            terms?.let { HelpText(it) }
            termsUrl?.let { address ->
                WebLinkButton(onClick = { openSourceWithFeedback(context, address) }) {
                    Text(stringResource(R.string.cantenna_source_terms, publisher))
                }
            }
        }
    }
}
