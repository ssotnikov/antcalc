package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.feedline.AttenuationUnit
import local.antenna.calculator.core.feedline.FeedlineLoss
import local.antenna.calculator.core.feedline.calculateFeedlineLoss
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.data.CableCatalog
import local.antenna.calculator.data.CableProfile
import local.antenna.calculator.state.CalculatorEvent
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.FeedlineDraft
import local.antenna.calculator.state.boundedNumericText
import java.math.BigDecimal
import java.text.NumberFormat
import local.antenna.calculator.core.units.LengthUnit

private fun AttenuationUnit.label() = when (this) {
    AttenuationUnit.PER_100_METRES -> R.string.feedline_per_metres
    AttenuationUnit.PER_100_FEET -> R.string.feedline_per_feet
}

private fun number(context: Context, value: Number, digits: Int): String =
    NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        isGroupingUsed = false
        maximumFractionDigits = digits
    }.format(value)

private fun rows(context: Context, result: FeedlineLoss): List<Pair<Int, String>> = buildList {
    fun addResult(label: Int, value: Double, unit: Int) {
        val shown = if (value > 0 && value < 0.01) "< " + number(context, 0.01, 2) else number(context, value, 2)
        add(label to context.getString(unit, shown))
    }
    addResult(R.string.feedline_loss, result.decibels, R.string.matching_db)
    addResult(R.string.feedline_transmitted, result.transmittedFraction * 100, R.string.matching_percent)
    result.outputWatts?.let { addResult(R.string.feedline_output, it, R.string.feedline_watts) }
    result.lostWatts?.let { addResult(R.string.feedline_lost, it, R.string.feedline_watts) }
}

private fun FeedlineDraft.calculate(profile: CableProfile?): FeedlineLoss? {
    val hertz = hertz() ?: return null
    val length = metres() ?: return null
    val inputWatts = watts()
    if (power.isNotBlank() && inputWatts == null) return null
    return if (profileId == null) {
        val attenuation = manualAttenuation()?.takeIf { attenuationHertz == hertz } ?: return null
        calculateFeedlineLoss(length, attenuation, enteredAttenuationUnit, inputWatts)
    } else {
        val attenuation = profile?.attenuation?.get(hertz) ?: return null
        calculateFeedlineLoss(length, attenuation, profile.unit, inputWatts)
    }
}

@Composable
internal fun FeedlineScreen(
    state: CalculatorUiState,
    currentState: () -> CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val catalog = remember(context) { CableCatalog.read(context) }
    val focus = LocalFocusManager.current
    val draft = state.draft.feedline
    val profile = catalog.profile(draft.profileId)
    val hertz = draft.hertz()
    val point = profile?.attenuation?.get(hertz)
    val result = draft.calculate(profile)
    val enabled = state.editingEnabled
    var details by rememberSaveable { mutableStateOf(false) }
    fun edit(change: (FeedlineDraft) -> FeedlineDraft) =
        onEvent(CalculatorEvent.Feedline(change(currentState().draft.feedline)))

    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("feedline-scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StorageNotice(state, onEvent)
        HelpText(R.string.feedline_screen_intro)
        FeedlineHeading(R.string.feedline_inputs)
        ChoicePicker(stringResource(R.string.feedline_method), listOf(true, false), draft.profileId != null,
            { stringResource(if (it) R.string.feedline_datasheet else R.string.feedline_manual) },
            { passport -> edit { it.copy(profileId = if (passport) catalog.profiles.first().id else null) } },
            "feedline-mode")
        if (draft.profileId != null) {
            CableCatalogPicker(stringResource(R.string.feedline_model), profile?.name ?: stringResource(R.string.feedline_unknown),
                catalog.profiles, enabled, "feedline-profile", includeManual = false) { chosen ->
                edit { it.copy(profileId = chosen?.id) }
            }
        }
        val frequencyLabel = stringResource(draft.frequencyUnit.labelResource())
        FeedlineField(draft.frequency, stringResource(R.string.feedline_frequency, frequencyLabel), enabled,
            "feedline-frequency", if (draft.frequency.isNotBlank() && hertz == null) R.string.feedline_frequency_error else null,
            megahertz = draft.frequencyUnit == FrequencyUnit.MEGAHERTZ) {
            edit { value -> value.copy(frequency = it.boundedNumericText()) }
        }
        ChoicePicker(stringResource(R.string.frequency_units), FrequencyUnit.entries, draft.frequencyUnit,
            { stringResource(it.labelResource()) }, { unit -> edit { it.changeFrequencyUnit(unit) } }, "feedline-frequency-unit")
        if (draft.profileId == null) {
            FeedlineField(draft.attenuationText(), stringResource(R.string.feedline_attenuation), enabled, "feedline-attenuation",
                if (draft.attenuation.isNotBlank() && draft.manualAttenuation() == null) R.string.feedline_attenuation_error else null) {
                edit { value -> value.editAttenuation(it) }
            }
            ChoicePicker(stringResource(R.string.feedline_attenuation_unit), AttenuationUnit.entries, draft.attenuationUnit,
                { stringResource(it.label()) }, { unit -> edit { it.copy(attenuationUnit = unit) } }, "feedline-unit")
            if (hertz != null && draft.manualAttenuation() != null && draft.attenuationHertz != hertz) {
                HelpText(R.string.feedline_confirm_frequency)
                OutlinedButton({ edit { it.copy(attenuationHertz = it.hertz()) } }, enabled = enabled) {
                    Text(stringResource(R.string.feedline_confirm_attenuation))
                }
            }
        } else if (profile != null) {
            if (point != null) {
                Text(number(context, point, 12) + " " + stringResource(profile.unit.label()),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("feedline-datasheet-value"))
            } else if (hertz != null) {
                HelpText(R.string.feedline_missing_frequency)
                OutlinedButton({ edit { it.copy(profileId = null) } }, enabled = enabled) {
                    Text(stringResource(R.string.feedline_manual))
                }
            }
            profile.conditions?.let { HelpText(it) }
            var frequencies by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ frequencies = true }, enabled = enabled) { Text(stringResource(R.string.feedline_frequencies)) }
                DropdownMenu(frequencies, { frequencies = false }) {
                    for (frequency in profile.attenuation.keys) {
                        DropdownMenuItem(text = { Text(context.frequencyWithUnit(frequency, draft.frequencyUnit)) }, onClick = {
                            frequencies = false
                            edit { it.chooseFrequency(frequency) }
                        })
                    }
                }
            }
            CableProfileSource(profile)
        } else {
            HelpText(R.string.feedline_unknown)
        }
        FeedlineField(draft.length, stringResource(R.string.feedline_length), enabled, "feedline-length",
            if (draft.length.isNotBlank() && draft.metres() == null) R.string.feedline_length_error else null) {
            edit { value -> value.copy(length = it.boundedNumericText()) }
        }
        FeedlineField(draft.power, stringResource(R.string.feedline_power), enabled, "feedline-power",
            if (draft.power.isNotBlank() && draft.watts() == null) R.string.feedline_power_error else null) {
            edit { value -> value.copy(power = it.boundedNumericText()) }
        }
        FeedlineHeading(R.string.feedline_result)
        if (result == null) {
            HelpText(R.string.feedline_pending)
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().testTag("feedline-result")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows(context, result).forEach { (label, value) ->
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    LinearProgressIndicator(progress = { result.transmittedFraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Button(onClick = {
            focus.clearFocus()
            val latest = currentState().draft.feedline
            val currentProfile = catalog.profile(latest.profileId)
            val currentResult = latest.calculate(currentProfile) ?: return@Button
            val report = report(context, latest, currentProfile, currentResult)
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                ClipData.newPlainText(appName, report),
            )
            onCopied()
        }, enabled = result != null, modifier = Modifier.fillMaxWidth().testTag("feedline-copy")) {
            Text(stringResource(R.string.copy_result))
        }
        TextButton({ details = !details }) {
            Text(stringResource(if (details) R.string.matching_less else R.string.cable_details))
        }
        if (details) {
            Text("A = α × L / L₀\nT = 10⁽⁻ᴬ/¹⁰⁾\nP₂ = P₁ × T\nΔP = P₁ × (1 − T)")
            HelpText(R.string.feedline_formula_help)
        }
        HelpText(R.string.feedline_limits)
    }
}

@Composable
private fun FeedlineHeading(resource: Int) {
    Text(stringResource(resource), Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FeedlineField(
    value: String, label: String, enabled: Boolean, tag: String, error: Int?,
    megahertz: Boolean = false, onEdit: (String) -> Unit,
) {
    val (fieldModifier, frequencyTransformation) = megahertzFieldPresentation(Modifier, megahertz)
    OutlinedTextField(value, onEdit, visualTransformation = frequencyTransformation, label = { Text(label) }, enabled = enabled, singleLine = true,
        isError = error != null, supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = fieldModifier.fillMaxWidth().testTag(tag))
}

@Composable
internal fun CableCatalogPicker(
    label: String,
    selected: String,
    profiles: List<CableProfile>,
    enabled: Boolean,
    tag: String,
    includeManual: Boolean,
    onPick: (CableProfile?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().testTag(tag), enabled = enabled) {
                Text(selected)
            }
            DropdownMenu(expanded, { expanded = false }) {
                if (includeManual) DropdownMenuItem({ Text(stringResource(R.string.feedline_manual)) }, {
                    expanded = false
                    onPick(null)
                })
                for (profile in profiles) DropdownMenuItem({ Text(profile.name) }, {
                    expanded = false
                    onPick(profile)
                })
            }
        }
    }
}

@Composable
internal fun CableProfileSource(profile: CableProfile) {
    val context = LocalContext.current
    TextButton(onClick = { openSourceWithFeedback(context, profile.source) }) {
        Text(stringResource(R.string.feedline_source, profile.name))
    }
}

private fun report(context: Context, draft: FeedlineDraft, profile: CableProfile?, result: FeedlineLoss): String = buildString {
    appendLine(context.getString(R.string.feedline_title))
    val frequencyUnit = context.getString(draft.frequencyUnit.labelResource())
    val frequency = context.formatFrequency(requireNotNull(draft.hertz()), draft.frequencyUnit)
    appendLine(context.getString(R.string.feedline_frequency, frequencyUnit) + ": " + frequency)
    appendLine(context.getString(R.string.feedline_length) + ": " + number(context, requireNotNull(draft.metres()), LengthUnit.METERS.displayScale))
    draft.watts()?.let { appendLine(context.getString(R.string.feedline_power) + ": " + number(context, it, 32)) }
    val attenuation: BigDecimal = if (profile != null) profile.attenuation.getValue(requireNotNull(draft.hertz()))
        else requireNotNull(draft.manualAttenuation())
    val unit = profile?.unit ?: draft.enteredAttenuationUnit
    appendLine(context.getString(R.string.feedline_attenuation) + ": " + number(context, attenuation, 32) + " " + context.getString(unit.label()))
    if (profile != null) {
        appendLine(profile.name)
        profile.conditions?.let { appendLine(context.getString(it)) }
        appendLine(context.getString(R.string.feedline_verified, profile.verified))
        appendLine(profile.source)
    } else {
        appendLine(context.getString(R.string.feedline_manual))
    }
    appendLine()
    rows(context, result).forEach { (label, value) -> appendLine(context.getString(label) + ": " + value) }
    append(context.getString(R.string.feedline_limits))
}
