package local.antenna.calculator.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import local.antenna.calculator.ui.megahertzOutputTransformation
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import local.antenna.calculator.ui.inputExampleColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.JournalLimits
import local.antenna.calculator.data.CableCatalog

internal enum class JournalSuggestion(val label: Int, val example: Int, val tag: String, val options: List<Int>) {
    INSTALLATION(R.string.journal_installation, R.string.journal_example_installation, "journal-installation", listOf(
        R.string.journal_setup_mast, R.string.journal_setup_roof, R.string.journal_setup_balcony,
        R.string.journal_setup_attic, R.string.journal_setup_field, R.string.journal_setup_trees,
    )),
    FEEDLINE(R.string.journal_feedline, R.string.journal_example_feedline, "journal-feedline", emptyList()),
}

@Composable
internal fun JournalSuggestionField(
    state: TextFieldState,
    field: JournalSuggestion,
    enabled: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val options = if (field == JournalSuggestion.FEEDLINE) {
        val russian = LocalConfiguration.current.locales[0].language == "ru"
        CableCatalog.read(context).suggestions.map { if (russian) it.russian else it.english }
    } else field.options.map { stringResource(it) }
    val query = state.text.toString().trimStart()
    val suggestions = if (focused && enabled && options.none { it.equals(query, ignoreCase = true) }) {
        options.filter { it.startsWith(query, ignoreCase = true) }
    } else emptyList()

    Column(Modifier.fillMaxWidth()) {
        JournalTextField(
            state, field.label, field.example, field.tag, enabled,
            modifier = Modifier.onFocusChanged { focused = it.isFocused },
            multiline = true, hint = R.string.journal_project_suggestions_hint,
        )
        if (suggestions.isNotEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth().testTag(field.tag + "-suggestions")) {
                suggestions.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            state.setTextAndPlaceCursorAtEnd(option)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalTextField(
    state: TextFieldState,
    label: Int,
    example: Int,
    tag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    limit: Int = JournalLimits.NOTE,
    multiline: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    error: Int? = null,
    hint: Int? = null,
    numeric: Boolean = keyboard == KeyboardType.Decimal,
    megahertz: Boolean = false,
) {
    val supporting = error ?: hint
    var focused by remember { mutableStateOf(false) }
    val placeholderColor = inputExampleColor()
    OutlinedTextField(
        state = state,
        enabled = enabled,
        label = { Text(stringResource(label)) },
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        placeholder = if (focused) null else {
            { Text(stringResource(example), color = placeholderColor) }
        },
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        outputTransformation = megahertzOutputTransformation(megahertz && !focused),
        inputTransformation = if (numeric) {
            InputTransformation { if (length > limit) replace(limit, length, "!") }
        } else InputTransformation.maxLength(limit),
        lineLimits = if (multiline) TextFieldLineLimits.MultiLine(maxHeightInLines = 4) else TextFieldLineLimits.SingleLine,
        modifier = modifier.fillMaxWidth().testTag(tag).onFocusChanged { focused = it.isFocused },
    )
}
