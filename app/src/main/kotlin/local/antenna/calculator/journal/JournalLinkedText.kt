package local.antenna.calculator.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import local.antenna.calculator.ui.appendWebLink
import local.antenna.calculator.ui.openRecordLinkWithFeedback
import local.antenna.calculator.ui.webLinkInlineContent
import local.antenna.calculator.ui.validatedRecordLinkAddress

internal data class JournalWebLink(val start: Int, val end: Int, val address: String)

private val journalLinkPattern = Regex("""\bhttps://[^\s<>"\u0000-\u001F]+""", RegexOption.IGNORE_CASE)

internal fun journalWebLinks(text: String): List<JournalWebLink> = journalLinkPattern.findAll(text).mapNotNull { match ->
    var candidate = match.value.trimEnd('.', ',', ';', ':', '!', '»', '”', '’')
    for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
        while (candidate.endsWith(close) && candidate.count { it == close } > candidate.count { it == open }) {
            candidate = candidate.dropLast(1)
        }
    }
    validatedRecordLinkAddress(candidate)?.let { JournalWebLink(match.range.first, match.range.first + candidate.length, it) }
}.toList()

@Composable
internal fun JournalLinkedText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val links = remember(value) { journalWebLinks(value) }
    val annotated = remember(value, links, linkColor, context) {
        buildAnnotatedString {
            var position = 0
            for (link in links) {
                append(value.substring(position, link.start))
                appendWebLink(value.substring(link.start, link.end), link.address, linkColor) {
                    openRecordLinkWithFeedback(context, link.address)
                }
                position = link.end
            }
            append(value.substring(position))
        }
    }
    Text(annotated, modifier, style = style, color = color, textAlign = textAlign, inlineContent = webLinkInlineContent(linkColor))
}
