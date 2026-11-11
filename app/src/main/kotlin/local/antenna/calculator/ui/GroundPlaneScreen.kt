package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.InputError
import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.state.*
import local.antenna.calculator.state.GroundPlaneField.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroundPlaneScreen(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    currentState: () -> CalculatorUiState,
    onChooseProject: (() -> Unit)?,
    onCopied: () -> Unit,
) {
    val draft = state.draft.groundPlane
    val result = remember(draft) { draft.result() }
    val layout = result as? GroundPlaneResult.Layout
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val title = stringResource(R.string.ground_plane_title)
    val source = stringResource(R.string.source_ground_plane_m0ukd_url)
    var details by rememberSaveable { mutableStateOf(false) }

    fun update(transform: (GroundPlaneDraft) -> GroundPlaneDraft) {
        onEvent(CalculatorEvent.GroundPlane(transform(currentState().draft.groundPlane)))
    }

    Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(20.dp).testTag("ground-plane-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StorageNotice(state, onEvent)
        Image(painterResource(R.drawable.ground_plane_antenna), stringResource(R.string.ground_plane_illustration),
            modifier = Modifier.fillMaxWidth().height(185.dp))
        HelpText(R.string.ground_plane_definition)
        HelpText(R.string.ground_plane_intro)
        GroundPlaneHeading(R.string.input_data)
        ChoicePicker(stringResource(R.string.inverted_v_mode), GroundPlaneMode.entries, draft.mode,
            { stringResource(it.label()) }, { mode -> focus.clearFocus(); update { it.copy(mode = mode) } }, "ground-plane-mode")
        for (field in draft.visibleFields) {
            GroundPlaneInput(draft, field) { text -> update { it.edit(field, text) } }
            if (field == FREQUENCY) {
                ChoicePicker(stringResource(R.string.frequency_units), FrequencyUnit.entries, draft.frequencyUnit,
                    { stringResource(it.labelResource()) }, { unit -> update { it.changeUnits(frequency = unit) } },
                    "ground-plane-frequency-unit")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (angle in listOf(0, 30, 45)) {
                FilterChip(
                    selected = draft.value(ANGLE) == angle * 10L,
                    onClick = { focus.clearFocus(); update { it.edit(ANGLE, angle.toString()) } },
                    label = { Text(stringResource(R.string.inverted_v_degrees, angle.toString())) },
                )
            }
        }
        HelpText(R.string.ground_plane_angle_help)
        ChoicePicker(stringResource(R.string.length_units), LengthUnit.entries, draft.lengthUnit,
            { stringResource(it.labelResource()) }, { unit -> update { it.changeUnits(length = unit) } }, "ground-plane-length-unit")

        GroundPlaneHeading(R.string.inverted_v_geometry)
        when (result) {
            null -> HelpText(R.string.inverted_v_pending)
            GroundPlaneResult.RadialsOverlap -> GroundPlaneNotice(R.string.ground_plane_overlap, isError = true)
            GroundPlaneResult.RadialsBelowGround -> GroundPlaneNotice(R.string.ground_plane_below, isError = true)
            is GroundPlaneResult.Layout -> {
                GroundPlaneDiagram(result, draft.lengthUnit)
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        result.rows().forEach { (label, value) ->
                            Column(Modifier.semantics(mergeDescendants = true) {}) {
                                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                                Text(groundPlaneLength(context, value, draft.lengthUnit), style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (result.endHeightMeters == 0.0) GroundPlaneNotice(R.string.ground_plane_contact, isError = false)
            }
        }
        if (onChooseProject != null) {
            Button(onClick = onChooseProject, enabled = state.editingEnabled && layout != null,
                modifier = Modifier.fillMaxWidth().testTag("ground-plane-save")) {
                Text(stringResource(R.string.inverted_v_save))
            }
        }
        OutlinedButton(
            onClick = {
                focus.clearFocus()
                val text = currentState().draft.groundPlane.copyText(context) ?: return@OutlinedButton
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
                onCopied()
            },
            enabled = layout != null,
            modifier = Modifier.fillMaxWidth().testTag("ground-plane-copy"),
        ) { Text(stringResource(R.string.copy_result)) }
        HelpText(R.string.ground_plane_limits)
        TextButton(onClick = { details = !details }) {
            Text(stringResource(if (details) R.string.matching_less else R.string.inverted_v_details))
        }
        if (details) {
            FormulaPlate(R.string.ground_plane_estimated, R.drawable.formula_ground_plane_lengths,
                R.string.ground_plane_model_alt, "ground-plane-length-formula")
            FormulaPlate(R.string.ground_plane_radius, R.drawable.formula_ground_plane_footprint,
                R.string.ground_plane_footprint_alt, "ground-plane-footprint-formula")
            FormulaPlate(R.string.ground_plane_end_height, R.drawable.formula_ground_plane_clearance,
                R.string.ground_plane_clearance_alt, "ground-plane-height-formula")
        }
        GroundPlaneHeading(R.string.references_title)
        TextButton(onClick = { openSourceWithFeedback(context, source) }, modifier = Modifier.testTag("ground-plane-source")) {
            Text(stringResource(R.string.source_ground_plane_m0ukd))
        }
        HelpText(R.string.source_ground_plane_m0ukd_description)
    }
}

@Composable
private fun GroundPlaneHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun GroundPlaneNotice(label: Int, isError: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (isError) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(stringResource(label), Modifier.fillMaxWidth().padding(12.dp)
            .testTag("ground-plane-notice").semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun GroundPlaneInput(draft: GroundPlaneDraft, field: GroundPlaneField, onEdit: (String) -> Unit) {
    val label = stringResource(field.label())
    val suffix = when (field) {
        WHIP, RADIAL, HEIGHT -> stringResource(draft.lengthUnit.labelResource())
        FREQUENCY -> stringResource(draft.frequencyUnit.labelResource())
        ANGLE -> "°"
        WHIP_FACTOR, RADIAL_FACTOR -> null
    }
    val message = when (val status = draft.number(field)) {
        is FieldState.Valid -> null
        FieldState.Empty -> R.string.error_empty
        FieldState.Incomplete -> R.string.error_incomplete
        is FieldState.Invalid -> field.errorLabel(status.error)
    }?.let { stringResource(it) }
    OutlinedTextField(
        value = draft.fields.getValue(field), onValueChange = onEdit,
        label = { Text(if (suffix == null) label else stringResource(R.string.inverted_v_input_unit, label, suffix)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = message != null,
        supportingText = message?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        modifier = Modifier.fillMaxWidth().testTag("ground-plane-${field.name}").semantics { if (message != null) error(message) },
    )
}

private fun GroundPlaneField.errorLabel(error: InputError): Int = when (error) {
    InputError.TOO_LONG -> R.string.error_too_long
    InputError.INVALID_NUMBER -> R.string.error_number
    InputError.NON_POSITIVE -> R.string.error_positive
    InputError.OUT_OF_RANGE -> when (this) {
        WHIP, RADIAL -> R.string.error_length_range
        HEIGHT -> R.string.inverted_v_height_range
        FREQUENCY -> R.string.error_frequency_range
        WHIP_FACTOR, RADIAL_FACTOR -> R.string.error_factor_range
        ANGLE -> R.string.ground_plane_angle_range
    }
    InputError.TOO_PRECISE -> when (this) {
        WHIP, RADIAL, HEIGHT -> R.string.error_length_step
        FREQUENCY -> R.string.error_frequency_step
        WHIP_FACTOR, RADIAL_FACTOR -> R.string.error_factor_step
        ANGLE -> R.string.inverted_v_angle_step
    }
}

private fun GroundPlaneMode.label() = if (this == GroundPlaneMode.ESTIMATED) R.string.ground_plane_estimated else R.string.ground_plane_manual

private fun GroundPlaneField.label() = when (this) {
    FREQUENCY -> R.string.frequency
    WHIP_FACTOR -> R.string.ground_plane_whip_factor
    RADIAL_FACTOR -> R.string.ground_plane_radial_factor
    WHIP -> R.string.ground_plane_whip
    RADIAL -> R.string.ground_plane_radial
    HEIGHT -> R.string.ground_plane_height
    ANGLE -> R.string.ground_plane_angle
}

private fun GroundPlaneResult.Layout.rows() = listOf(
    R.string.ground_plane_whip to whipMeters,
    R.string.ground_plane_radial to radialMeters,
    R.string.ground_plane_radius to radiusMeters,
    R.string.ground_plane_diameter to diameterMeters,
    R.string.ground_plane_drop to dropMeters,
    R.string.ground_plane_end_height to endHeightMeters,
    R.string.ground_plane_tip to tipHeightMeters,
    R.string.ground_plane_wire to wireMeters,
)

internal fun groundPlaneLength(context: Context, meters: Double, unit: LengthUnit): String {
    val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        maximumFractionDigits = 2
        isGroupingUsed = false
    }
    return "${number.format(meters * 1e9 / unit.nanometersPerUnit)} ${context.getString(unit.labelResource())}"
}

private fun GroundPlaneDraft.copyText(context: Context): String? {
    val layout = result() as? GroundPlaneResult.Layout ?: return null
    val lengthLabel = context.getString(lengthUnit.labelResource())
    return buildString {
        appendLine(context.getString(R.string.ground_plane_title))
        appendLine(context.getString(mode.label()))
        visibleFields.forEach { field ->
            val raw = fields.getValue(field).trim()
            val displayed = when (field) {
                WHIP, RADIAL, HEIGHT -> "$raw $lengthLabel"
                FREQUENCY -> "$raw ${context.getString(frequencyUnit.labelResource())}"
                ANGLE -> context.getString(R.string.inverted_v_degrees, raw)
                WHIP_FACTOR, RADIAL_FACTOR -> raw
            }
            appendLine(context.getString(R.string.journal_property, context.getString(field.label()), displayed))
        }
        layout.rows().forEach { (label, value) ->
            appendLine(context.getString(R.string.journal_property, context.getString(label), groundPlaneLength(context, value, lengthUnit)))
        }
        if (layout.endHeightMeters == 0.0) appendLine(context.getString(R.string.ground_plane_contact))
        append(context.getString(R.string.ground_plane_limits))
    }
}
