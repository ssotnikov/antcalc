package local.antenna.calculator.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.R3udkYagi

private val guideElementNames = listOf(R.string.construction_yagi_reflector, R.string.construction_yagi_driven,
    R.string.construction_yagi_director_1, R.string.construction_yagi_director_2)

// Both localized versions share the same block order and image references.
private enum class YagiSection(@param:StringRes val title: Int, val blocks: IntRange, val step: Int? = null) {
    GEOMETRY(R.string.construction_yagi_geometry, 2..2),
    MATERIALS(R.string.construction_yagi_materials, 3..70),
    MOUNTS(R.string.construction_yagi_mounts_title, 71..96, 1),
    ELEMENTS(R.string.construction_yagi_elements_title, 97..113, 2),
    FEED(R.string.construction_yagi_feed_title, 114..124, 3),
    ASSEMBLY(R.string.construction_yagi_assembly_title, 125..134, 4),
    CHECK(R.string.construction_yagi_check_title, 135..137, 5),
    FINISH(R.string.construction_yagi_finish_title, 138..148, 6),
    VARIANTS(R.string.construction_yagi_variants_title, 149..154),
}

@Composable
internal fun YagiConstructionScreen(onOpenMeasurements: (() -> Unit)?) {
    val context = LocalContext.current
    val blocks = yagiArticleBlocks()
    var expandedSections by rememberSaveable { mutableIntStateOf(1 shl YagiSection.GEOMETRY.ordinal) }
    val scroll = rememberPageLazyListState()
    LazyColumn(
        Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("yagi-guide"),
        state = scroll,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("yagi-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painterResource(R.drawable.yagi_antenna),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(192.dp),
                )
                Text(stringResource(R.string.construction_yagi_intro))
            }
        }
        YagiSection.entries.forEach { section ->
            val mask = 1 shl section.ordinal
            val expanded = expandedSections and mask != 0
            item(section) {
                YagiSectionHeader(section, expanded) { expandedSections = expandedSections xor mask }
            }
            if (expanded) {
                if (section == YagiSection.GEOMETRY) item("dimensions") { YagiDimensions() }
                if (section == YagiSection.CHECK) item("measurement-link") { YagiMeasurementGuide(onOpenMeasurements) }
                items(section.blocks.toList(), key = { it }) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        YagiArticleBlock(blocks.getJSONObject(index))
                        if (index == 30) YagiStlExport()
                    }
                }
            }
        }
        item("source") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                YagiArticleBlock(blocks.getJSONObject(155))
                Text(
                    stringResource(R.string.construction_yagi_source_title),
                    Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium,
                )
                for (index in 156 until blocks.length()) YagiArticleBlock(blocks.getJSONObject(index))
                HelpText(R.string.construction_yagi_rights)
                TextButton(onClick = { openSourceWithFeedback(context, LIZAALERT_YAGI_URL) }) {
                    Text(stringResource(R.string.construction_yagi_source_link))
                }
            }
        }
    }
}

@Composable
private fun YagiSectionHeader(section: YagiSection, expanded: Boolean, onToggle: () -> Unit) {
    val label = stringResource(section.title)
    val state = stringResource(if (expanded) R.string.jpole_expanded else R.string.jpole_collapsed)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
                .semantics { heading(); stateDescription = state }.testTag("yagi-section-${section.name}").padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▾" else "▸", Modifier.padding(end = 10.dp).clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.secondary)
            Text(section.step?.let { "$it. $label" } ?: label, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
        }
        HorizontalDivider()
    }
}

@Composable
private fun YagiMeasurementGuide(onOpenMeasurements: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val linkText = stringResource(R.string.sweep_tool_title)
    val paragraph = stringResource(R.string.construction_yagi_check, linkText)
    val text = buildAnnotatedString {
        val start = paragraph.indexOf(linkText)
        if (onOpenMeasurements == null || start < 0) {
            append(paragraph)
        } else {
            append(paragraph.substring(0, start))
            val link = LinkAnnotation.Clickable(
                tag = "yagi-open-vna",
                styles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { onOpenMeasurements() },
            )
            withLink(link) { append(linkText) }
            append(paragraph.substring(start + linkText.length))
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
}

@Composable
private fun YagiDimensions() {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    ScrollableTable(Modifier.width(320.dp * scale)) {
        YagiDimensionRow(stringResource(R.string.construction_yagi_element),
            stringResource(R.string.construction_yagi_position), stringResource(R.string.construction_yagi_length), header = true)
        R3udkYagi.elements.forEachIndexed { index, element ->
            HorizontalDivider()
            YagiDimensionRow(stringResource(guideElementNames[index]), element.positionMm.toInt().toString(), element.overallMm.toInt().toString())
        }
    }
}

@Composable
private fun YagiDimensionRow(name: String, position: String, length: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.weight(1.4f), color = color, style = style)
        Text(position, Modifier.weight(1f), color = color, style = style, textAlign = TextAlign.End)
        Text(length, Modifier.weight(1f), color = color, style = style, textAlign = TextAlign.End)
    }
}
