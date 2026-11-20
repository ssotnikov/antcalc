package local.antenna.calculator.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

internal val licenseSourceAddresses = setOf(
    "https://opensource.org/licenses/MIT",
    "https://opensource.org/license/mit",
)

internal data class LicenseTextPart(val text: String, val address: String? = null, val bold: Boolean = false)

// The bundled notice uses linked badges, Markdown links, bold text and bare HTTPS URLs.
// Interpret only these inline forms; never load the badge image or remote document content.
private val licenseInlinePattern = Regex(
    """\[!\[(?<badgeLabel>[^\]\r\n]+)]\(https://[^\s)]+\)]\((?<badgeUrl>https://[^\s)]+)\)|\[(?<label>[^\]\r\n]+)]\((?<url>https://[^\s)]+)\)|\*\*(?<bold>[^*\r\n]+)\*\*|(?<plain>https://[^\s<>"\u0000-\u001F]+)""",
)

internal fun licenseTextParts(value: String): List<LicenseTextPart> = buildList {
    var position = 0
    for (match in licenseInlinePattern.findAll(value)) {
        if (match.range.first > position) add(LicenseTextPart(value.substring(position, match.range.first)))
        val groups = match.groups
        val label = groups["badgeLabel"]?.value ?: groups["label"]?.value
        val address = groups["badgeUrl"]?.value ?: groups["url"]?.value
        when {
            label != null -> add(LicenseTextPart(label, address?.let(::validatedSourceAddress)))
            groups["bold"] != null -> add(LicenseTextPart(requireNotNull(groups["bold"]).value, bold = true))
            else -> {
                val raw = requireNotNull(groups["plain"]).value
                var url = raw.trimEnd('.', ',', ';', ':', '!')
                while (url.endsWith(')') && url.count { it == ')' } > url.count { it == '(' }) url = url.dropLast(1)
                add(LicenseTextPart(url, validatedSourceAddress(url)))
                if (url.length < raw.length) add(LicenseTextPart(raw.substring(url.length)))
            }
        }
        position = match.range.last + 1
    }
    if (position < value.length) add(LicenseTextPart(value.substring(position)))
}

@Composable
internal fun LicenseLinkedText(
    value: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.primary
    val parts = remember(value) { licenseTextParts(value) }
    val text = remember(parts, color, context) {
        buildAnnotatedString {
            for (part in parts) {
                when {
                    part.address != null -> appendWebLink(part.text, part.address, color, showIcon = !part.text.equals("MIT License", ignoreCase = true) || part.address !in licenseSourceAddresses) {
                        openSourceWithFeedback(context, part.address)
                    }
                    part.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part.text) }
                    else -> append(part.text)
                }
            }
        }
    }
    SelectionContainer { Text(text, style = style, color = textColor, inlineContent = webLinkInlineContent(color)) }
}
