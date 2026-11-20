package local.antenna.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import local.antenna.calculator.R

@Composable
internal fun PanelChevron(expanded: Boolean, tint: Color = LocalContentColor.current) {
    Icon(painterResource(R.drawable.ic_section_chevron), contentDescription = null,
        modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = tint)
}

@Composable
internal fun WebGlobeIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(R.drawable.ic_web_globe), contentDescription = null,
        modifier = modifier.size(18.dp), tint = tint)
}

@Composable
internal fun WebLinkLabel(tint: Color = LocalContentColor.current, content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        WebGlobeIcon(tint = tint)
        content()
    }
}

@Composable
internal fun WebLinkButton(
    onClick: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick, modifier) { WebLinkLabel(content = content) }
}

@Composable
internal fun PanelButton(
    expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val state = stringResource(if (expanded) R.string.jpole_expanded else R.string.jpole_collapsed)
    TextButton(onClick, modifier.semantics { stateDescription = state }, colors = colors, contentPadding = contentPadding) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            PanelChevron(expanded)
            content()
        }
    }
}

private const val WEB_LINK_INLINE_ICON = "web-link-icon"

internal fun AnnotatedString.Builder.appendWebLink(label: String, address: String, color: Color, showIcon: Boolean = true, open: () -> Unit) {
    if (showIcon) appendInlineContent(WEB_LINK_INLINE_ICON, " ")
    withLink(LinkAnnotation.Clickable(
        tag = address,
        styles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
        linkInteractionListener = { open() },
    )) { append(label) }
}

internal fun webLinkInlineContent(color: Color): Map<String, InlineTextContent> = mapOf(
    WEB_LINK_INLINE_ICON to InlineTextContent(Placeholder(1.45.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
        WebGlobeIcon(tint = color)
    },
)
