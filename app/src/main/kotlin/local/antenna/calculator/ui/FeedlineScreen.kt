package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.feedline.AttenuationUnit
import local.antenna.calculator.core.feedline.FeedlineLoss
import local.antenna.calculator.core.feedline.FeedlineLengthLimit
import local.antenna.calculator.core.feedline.calculateFeedlineLoss
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.data.CableCatalog
import local.antenna.calculator.data.CableProfile
import local.antenna.calculator.state.CalculatorEvent
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.FeedlineDraft
import local.antenna.calculator.state.FeedlineTask
import local.antenna.calculator.state.boundedNumericText
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.abs
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

private fun rows(context: Context, result: FeedlineLoss, maximum: FeedlineMaximumCalculation? = null): List<Pair<Int, String>> = buildList {
    fun addResult(label: Int, value: Double, unit: Int) {
        val shown = if (value > 0 && value < 0.01) "< " + number(context, 0.01, 2) else number(context, value, 2)
        add(label to context.getString(unit, shown))
    }
    if (maximum == null) addResult(R.string.feedline_loss, result.decibels, R.string.matching_db)
    else {
        addResult(R.string.feed_limit_cable_loss, maximum.cableDecibels.toDouble(), R.string.matching_db)
        addResult(R.string.feed_limit_connection_result, maximum.connectionDecibels.toDouble(), R.string.matching_db)
        addResult(R.string.feed_limit_total_loss, result.decibels, R.string.matching_db)
    }
    addResult(R.string.feedline_transmitted, result.transmittedFraction * 100, R.string.matching_percent)
    result.outputWatts?.let { addResult(R.string.feedline_output, it, R.string.feedline_watts) }
    result.lostWatts?.let { addResult(if (maximum == null) R.string.feedline_lost else R.string.feed_limit_line_lost, it, R.string.feedline_watts) }
}

private fun FeedlineDraft.calculate(
    profile: CableProfile?, allowProvisional: Boolean = false, passportFrequency: Long? = hertz(),
): FeedlineLoss? {
    if (task == FeedlineTask.MAXIMUM_LENGTH) return maximumCalculation(profile, allowProvisional, passportFrequency)?.transmission
    val hertz = hertz() ?: return null
    val length = metres() ?: return null
    val inputWatts = watts()
    if (power.isNotBlank() && inputWatts == null) return null
    return if (profileId == null) {
        val attenuation = manualAttenuation()?.takeIf { allowProvisional || attenuationHertz == hertz } ?: return null
        calculateFeedlineLoss(length, attenuation, enteredAttenuationUnit, inputWatts)
    } else {
        val attenuation = profile?.attenuation?.get(passportFrequency) ?: return null
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
    val resources = LocalResources.current
    val appName = stringResource(R.string.app_name)
    val catalog = remember(context) { CableCatalog.read(context) }
    val focus = LocalFocusManager.current
    val draft = state.draft.feedline
    val profile = catalog.profile(draft.profileId)
    val hertz = draft.hertz()
    val point = profile?.attenuation?.get(hertz)
    val previewHertz = if (hertz != null && point == null) {
        profile?.attenuation?.keys?.minWithOrNull(compareBy<Long> { abs(it - hertz) }.thenBy { it })
    } else null
    val previewFrequency = previewHertz?.let { context.frequencyWithUnit(it, draft.frequencyUnit) }
    val provisional = draft.profileId == null && hertz != null && draft.manualAttenuation() != null && draft.attenuationHertz != hertz
    val result = draft.calculate(profile, allowProvisional = true, passportFrequency = previewHertz ?: hertz)
    val maximumMode = draft.task == FeedlineTask.MAXIMUM_LENGTH
    val maximum = if (maximumMode) draft.maximumCalculation(profile, true, previewHertz ?: hertz) else null
    var illustration by rememberSaveable { mutableStateOf(FeedlineIllustration.CHART) }
    val enabled = state.editingEnabled
    var details by rememberSaveable { mutableStateOf(false) }
    fun edit(change: (FeedlineDraft) -> FeedlineDraft) =
        onEvent(CalculatorEvent.Feedline(change(currentState().draft.feedline)))

    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState()).padding(20.dp)
            .testTag("feedline-scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StorageNotice(state, onEvent)
        ChoicePicker(stringResource(R.string.feed_task), FeedlineTask.entries, draft.task,
            { stringResource(if (it == FeedlineTask.LOSS) R.string.feed_task_loss else R.string.feed_task_max) },
            { task -> if (enabled) edit { it.copy(task = task) } }, "feedline-task")
        if (maximumMode) ChoicePicker(stringResource(R.string.feed_limit_view), FeedlineIllustration.entries, illustration,
            { stringResource(it.title) }, { illustration = it }, "feedline-illustration")
        fun watts(value: Double?) = value?.let {
            val shown = if (it > 0 && it < .01) "< " + number(context, .01, 2) else number(context, it, 2)
            resources.getString(R.string.feedline_watts, shown)
        } ?: "—"
        val status = when {
            previewFrequency != null -> stringResource(R.string.feedline_preview_result, previewFrequency)
            provisional -> stringResource(R.string.feedline_provisional_label)
            else -> null
        }
        val caption = when {
            maximumMode -> R.string.feed_limit_legend
            draft.metres() == null -> R.string.feedline_enter_length
            result != null && draft.power.isBlank() -> R.string.feedline_enter_power
            else -> R.string.hero_feed_legend
        }
        if (maximumMode && illustration != FeedlineIllustration.CABLE) {
            FeedlineLimitIllustration(illustration, maximum, draft.allowedDecibels(), draft.connectionDecibels(), status)
        } else {
        if (maximumMode) Text(maximumLengthValue(context, maximum),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("feedline-diagram-length"))
        TransmissionDiagram(TransmissionIllustration.POWER,
            stringResource(R.string.hero_power_in) + "\n" + watts(draft.watts()?.toDouble()),
            stringResource(R.string.hero_power_out) + "\n" + watts(result?.outputWatts),
            result?.lostWatts?.let { stringResource(R.string.hero_dissipated, watts(it)) },
            result?.transmittedFraction, caption, status)
        }
        HelpText(if (maximumMode) R.string.feed_limit_screen_intro else R.string.feedline_screen_intro)
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
        FeedlineField(draft.frequency, stringResource(if (draft.profileId == null) R.string.feed_manual_frequency else R.string.feedline_frequency, frequencyLabel), enabled,
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
            if (provisional) {
                HelpText(R.string.feed_provisional)
                OutlinedButton({ edit { it.copy(attenuationHertz = it.hertz()) } }, enabled = enabled) {
                    Text(stringResource(R.string.feedline_confirm_attenuation))
                }
            }
        } else if (profile != null) {
            if (point != null) {
                Text(number(context, point, 12) + " " + stringResource(profile.unit.label()),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("feedline-datasheet-value"))
            } else if (hertz != null) {
                if (previewHertz != null && previewFrequency != null) {
                    SelectableText(stringResource(R.string.feedline_preview_explanation, previewFrequency),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton({ edit { it.chooseFrequency(previewHertz) } }, enabled = enabled,
                        modifier = Modifier.testTag("feedline-use-preview-frequency")) {
                        Text(stringResource(R.string.feedline_use_preview_frequency, previewFrequency))
                    }
                } else HelpText(R.string.feedline_missing_frequency)
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
        if (maximumMode) {
            FeedlineField(draft.allowedLoss, stringResource(R.string.feed_limit_allowed), enabled, "feedline-allowed-loss",
                if (draft.allowedLoss.isNotBlank() && draft.allowedDecibels() == null) R.string.feed_limit_input_error else null) {
                edit { value -> value.copy(allowedLoss = it.boundedNumericText()) }
            }
            FeedlineField(draft.connectionLoss, stringResource(R.string.feed_limit_connections), enabled, "feedline-connection-loss",
                if (draft.connectionLoss.isNotBlank() && draft.connectionDecibels() == null) R.string.feed_limit_input_error else null) {
                edit { value -> value.copy(connectionLoss = it.boundedNumericText()) }
            }
            HelpText(R.string.feed_limit_connections_help)
        } else {
        FeedlineField(draft.length, stringResource(R.string.feedline_length), enabled, "feedline-length",
            if (draft.length.isNotBlank() && draft.metres() == null) R.string.feedline_length_error else null) {
            edit { value -> value.copy(length = it.boundedNumericText()) }
        }
        }
        FeedlineField(draft.power, stringResource(R.string.feedline_power), enabled, "feedline-power",
            if (draft.power.isNotBlank() && draft.watts() == null) R.string.feedline_power_error else null) {
            edit { value -> value.copy(power = it.boundedNumericText()) }
        }
        FeedlineHeading(R.string.feedline_result)
        if (provisional && result != null) HelpText(R.string.feed_provisional)
        if (maximum?.limit == FeedlineLengthLimit.Exceeded) {
            Text(stringResource(R.string.feed_limit_exceeded), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("feedline-limit-error"))
        } else if (result == null) {
            HelpText(R.string.feedline_pending)
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().testTag("feedline-result")) {
                SelectionContainer {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (status != null) {
                            Text(status, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("feedline-preview-reference"))
                        }
                        if (maximum != null) {
                            Column(Modifier.testTag("feedline-max-length").semantics(mergeDescendants = true) {}) {
                                Text(stringResource(R.string.feed_limit_title), style = MaterialTheme.typography.bodyMedium)
                                Text(maximumLengthValue(context, maximum), style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            HelpText(when {
                                maximum.limit == FeedlineLengthLimit.Unbounded -> R.string.feed_limit_unbounded_help
                                (maximum.limit as? FeedlineLengthLimit.Finite)?.metres?.signum() == 0 -> R.string.feed_limit_zero
                                else -> R.string.feed_limit_rounding
                            })
                        }
                        rows(context, result, maximum).forEach { (label, value) ->
                            Column(Modifier.semantics(mergeDescendants = true) {}) {
                                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        LinearProgressIndicator(progress = { result.transmittedFraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
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
        }, enabled = result != null && !provisional && previewHertz == null, modifier = Modifier.fillMaxWidth().testTag("feedline-copy")) {
            Text(stringResource(R.string.copy_result))
        }
        TextButton({ details = !details }, modifier = Modifier.testTag("feedline-details")) {
            Text(stringResource(if (details) R.string.matching_less else if (maximumMode) R.string.feed_limit_details else R.string.cable_details))
        }
        if (details) {
            if (maximumMode) {
                SelectableText(stringResource(R.string.feed_limit_formula))
                SelectableText(stringResource(R.string.feed_limit_definitions),
                    modifier = Modifier.testTag("feedline-limit-definitions"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectableText(stringResource(R.string.feed_limit_example),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (maximumMode) SelectableText(stringResource(R.string.feed_limit_power_formula))
            else SelectableText("A = α × L / L₀\nT = 10⁽⁻ᴬ/¹⁰⁾\nP₂ = P₁ × T\nΔP = P₁ × (1 − T)")
            HelpText(if (maximumMode) R.string.feed_limit_power_help else R.string.feedline_formula_help)
            if (maximumMode) TextButton({ openSourceWithFeedback(context, FEEDLINE_LOSS_METHOD_URL) }) {
                Text(stringResource(R.string.feed_limit_source))
            }
        }
        HelpText(if (maximumMode) R.string.feed_limit_limits else R.string.feedline_limits)
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
    AppTextField(value, { if (it.acceptsDecimalEdit()) onEdit(it) }, visualTransformation = frequencyTransformation, label = label, enabled = enabled, singleLine = true,
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
    val maximum = if (draft.task == FeedlineTask.MAXIMUM_LENGTH) draft.maximumCalculation(profile) else null
    if (maximum != null) {
        appendLine(context.getString(R.string.feed_limit_method))
        appendLine(context.getString(R.string.feed_limit_allowed) + ": " + number(context, requireNotNull(draft.allowedDecibels()), 32))
        appendLine(context.getString(R.string.feed_limit_connections) + ": " + number(context, requireNotNull(draft.connectionDecibels()), 32))
        appendLine(context.getString(R.string.feed_limit_title) + ": " + maximumLengthValue(context, maximum))
    } else {
        appendLine(context.getString(R.string.feedline_length) + ": " + number(context, requireNotNull(draft.metres()), LengthUnit.METERS.displayScale))
    }
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
    rows(context, result, maximum).forEach { (label, value) -> appendLine(context.getString(label) + ": " + value) }
    append(context.getString(if (maximum == null) R.string.feedline_limits else R.string.feed_limit_limits))
}

private fun maximumLengthValue(context: Context, maximum: FeedlineMaximumCalculation?): String = when (val limit = maximum?.limit) {
    is FeedlineLengthLimit.Finite -> context.getString(R.string.feed_limit_no_more, feedlineLengthText(context, limit.metres))
    FeedlineLengthLimit.Unbounded -> context.getString(R.string.feed_limit_unbounded)
    FeedlineLengthLimit.Exceeded -> context.getString(R.string.feed_limit_exceeded)
    null -> context.getString(R.string.feedline_pending)
}
