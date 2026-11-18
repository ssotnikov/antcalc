package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    isError: Boolean = false,
    example: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        label = { InputLabel(label, value.isEmpty() && !focused && !isError && enabled) },
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        placeholder = example?.let { { Text(it, color = inputExampleColor()) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    example: String? = null,
    supportingText: @Composable (() -> Unit)? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val resting = state.text.isEmpty() && !focused && !isError && enabled
    OutlinedTextField(
        state = state,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        label = { InputLabel(label, resting) },
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = if (resting && example != null) {
            {
                Column {
                    Text(example, color = inputExampleColor())
                    supportingText?.invoke()
                }
            }
        } else supportingText,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        lineLimits = lineLimits,
        keyboardOptions = keyboardOptions,
    )
}

@Composable
private fun InputLabel(label: String, resting: Boolean) {
    // Keep the label size stable across focus and value changes.
    Text(
        label,
        color = if (resting) inputExampleColor() else Color.Unspecified,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
