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

@Composable
internal fun ReferencesScreen(language: AppLanguage) {
    val scroll = rememberScrollState()
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().cardListBounce(scroll.isScrollInProgress)
            .verticalScroll(scroll, overscrollEffect = null)
            .padding(20.dp).testTag("references-screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HelpText(R.string.materials_note)
        referenceGroups.forEach { group ->
            val materials = if (group.interfaceLanguageOnly || language == AppLanguage.ENGLISH) {
                group.materials.filter { it.language == language }
            } else if (group.preferRussianSources) {
                group.materials.sortedBy { it.language != AppLanguage.RUSSIAN }
            } else {
                group.materials
            }
            if (materials.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(group.title),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                materials.forEachIndexed { index, material ->
                    material.subsection?.takeIf { it != materials.getOrNull(index - 1)?.subsection }?.let { subsection ->
                        Text(stringResource(subsection), color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                    ReferenceCard(material)
                }
            }
        }
    }
}

@Composable
private fun ReferenceCard(material: TechnicalReference) {
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
