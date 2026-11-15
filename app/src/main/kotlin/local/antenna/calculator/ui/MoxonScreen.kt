package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.layout.MoxonDimensions
import local.antenna.calculator.core.layout.MoxonEstimate
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.FrequencyUnit.MEGAHERTZ
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.state.boundedNumericText

@Composable
internal fun MoxonScreen(
    onSave: ((CalculationSnapshot, String) -> Unit)?,
    onMeasurements: (() -> Unit)?,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences("moxon_calculator", Context.MODE_PRIVATE)
    }
    val saved = remember(prefs) { prefs.all }
    var calculating by rememberSaveable {
        mutableStateOf(saved["calculate"] == true)
    }
    var profileId by rememberSaveable {
        mutableStateOf(moxonProfiles.firstOrNull { it.id == saved["profile"] }?.id ?: moxonProfiles.first().id)
    }
    var presetIndex by rememberSaveable { mutableStateOf(moxonPresets.indexOfFirst { it.hz == saved["preset_hz"] }) }
    var rawFrequency by rememberSaveable {
        mutableStateOf((saved["frequency"] as? String)?.boundedNumericText() ?: "146.000")
    }
    var rawDiameter by rememberSaveable {
        mutableStateOf((saved["diameter"] as? String)?.boundedNumericText() ?: "2")
    }
    var rawAllowance by rememberSaveable(profileId) {
        mutableStateOf((prefs.all["allowance_$profileId"] as? String)?.boundedNumericText().orEmpty())
    }
    var unit by rememberSaveable {
        mutableStateOf(LengthUnit.entries.firstOrNull { it.name == saved["unit"] } ?: LengthUnit.MILLIMETERS)
    }
    LaunchedEffect(calculating, profileId, presetIndex, rawFrequency, rawDiameter, rawAllowance, unit) {
        prefs.edit {
            putBoolean("calculate", calculating)
            putString("profile", profileId)
            putLong("preset_hz", moxonPresets.getOrNull(presetIndex)?.hz ?: 0L)
            putString("frequency", rawFrequency)
            putString("diameter", rawDiameter)
            putString("allowance_$profileId", rawAllowance)
            putString("unit", unit.name)
        }
    }
    val profile = moxonProfiles.firstOrNull { it.id == profileId } ?: moxonProfiles.first()
    val published = profile.takeUnless { calculating }
    val preset = moxonPresets.getOrNull(presetIndex)
    val frequencyInput = (PositiveDecimalParser.parse(rawFrequency) as? FieldState.Valid)?.value
    val frequency = frequencyInput?.let {
        runCatching { it.movePointRight(6).longValueExact() }.getOrNull()
    }?.takeIf { it in Frequency.MIN_HERTZ..Frequency.MAX_HERTZ }?.let(Frequency::ofHertz)
    val diameter = (PositiveDecimalParser.parse(rawDiameter) as? FieldState.Valid)?.value?.toDouble()
        ?.takeIf { it.isFinite() }
    val estimate = if (published == null && frequency != null && diameter != null) {
        MoxonEstimate.calculate(frequency, diameter)
    } else null
    val dimensions = published?.dimensions ?: estimate?.dimensions
    val source = published?.source ?: MoxonSource.METHOD
    val stock = published?.stockInches.orEmpty()
    val allowance = if (rawAllowance.isBlank()) 0.0 else {
        (PositiveDecimalParser.parse(rawAllowance, allowZero = true) as? FieldState.Valid)?.value
            ?.takeIf { it.scale() <= 3 }?.toDouble()?.takeIf { it in 0.0..1000.0 }
    }
    val adjustedStock = allowance?.let { extra -> stock.map { (name, inches) -> name to inches * 25.4 + extra * 2 } }
    val snapshot = dimensions?.let {
        moxonSnapshot(published, frequency, diameter, it, unit, allowance?.takeIf { stock.isNotEmpty() }, adjustedStock.orEmpty())
    }
    val canSave = snapshot != null && (stock.isEmpty() || allowance != null)
    val title = published?.let { context.moxonLabel(it) } ?: stringResource(R.string.moxon_estimate)
    val measureNote = stringResource(R.string.moxon_tuning)
    val length = { mm: Double -> formatLength(context, mm / 1000, unit) }
    val frequencies = if (published != null) published.frequencies else listOfNotNull(frequency?.hertz)
    val frequencyText = frequencies.joinToString(" / ") { context.frequencyWithUnit(it, MEGAHERTZ) }
    val conductor = if (published != null) stringResource(published.material) else {
        diameter?.let { "${context.moxonNumber(it)} ${stringResource(R.string.mm)}" }.orEmpty()
    }
    fun report() = buildList {
        add(title)
        if (frequencyText.isNotEmpty()) add(frequencyText)
        if (conductor.isNotEmpty()) add(conductor)
        dimensions?.valuesMm?.forEachIndexed { index, mm ->
            val dimension = MoxonDimension.entries[index]
            add("${dimension.mark} · ${resources.getString(dimension.label)} — ${length(mm)}")
        }
        published?.facts?.forEach { add(resources.getString(it)) }
        if (adjustedStock.orEmpty().isNotEmpty() && allowance != null) {
            add(resources.getString(R.string.moxon_allowance) + " — " + context.moxonNumber(allowance))
            adjustedStock.orEmpty().forEach { (key, mm) -> add("$key — ${length(mm)}") }
        }
        if (published?.hybrid == true) addAll(moxonYagiLines(context, length))
        if (published == null) add(resources.getString(R.string.moxon_round_model))
        if (published?.hybrid == true) add(resources.getString(R.string.moxon_hybrid_note))
        add(measureNote)
        add(source.url)
    }.joinToString("\n")

    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
            .padding(20.dp).testTag("moxon-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoxonAntennaArt(published?.view ?: MoxonView.GENERIC, dimensions)
        HelpText(published?.description ?: R.string.moxon_intro)
        ChoicePicker(stringResource(R.string.moxon_mode), listOf(false, true), calculating,
            { stringResource(if (it) R.string.moxon_calculate else R.string.moxon_published) },
            { calculating = it }, "moxon-mode")
        if (published != null) {
            MoxonHeading(R.string.yagi_profile)
            MoxonMenu(title, "moxon-profile", moxonProfiles.map { it.id to context.moxonLabel(it) }) {
                profileId = it
            }
            if (frequencyText.isNotEmpty()) Text(frequencyText)
            Text(conductor)
        } else {
            MoxonHeading(R.string.moxon_band)
            val custom = stringResource(R.string.moxon_custom)
            val presetLabel = preset?.let { "${it.band} ${stringResource(it.unit)}" } ?: custom
            val bands = moxonPresets.map { it.hz.toString() to "${it.band} ${stringResource(it.unit)}" }
            MoxonMenu(presetLabel, "moxon-preset", listOf("custom" to custom) + bands) { id ->
                presetIndex = moxonPresets.indexOfFirst { it.hz.toString() == id }
                moxonPresets.getOrNull(presetIndex)?.let { rawFrequency = context.formatFrequency(it.hz, MEGAHERTZ) }
            }
            MoxonInput(rawFrequency, R.string.moxon_frequency, "moxon-frequency", frequency == null) {
                rawFrequency = it
                presetIndex = -1
            }
            MoxonInput(rawDiameter, R.string.moxon_diameter, "moxon-diameter", diameter == null) { rawDiameter = it }
            HelpText(R.string.moxon_round_model)
            if (preset?.preliminary == true) HelpText(R.string.moxon_preliminary)
            if (estimate?.nearUpperLimit == true) HelpText(R.string.moxon_upper_limit)
            if (frequency != null && diameter != null && estimate == null) {
                Text(stringResource(R.string.moxon_domain), color = MaterialTheme.colorScheme.error)
            }
        }
        if (dimensions != null) {
            MoxonHeading(if (published == null) R.string.moxon_estimate else R.string.moxon_sizes)
            MoxonAntennaArt(published?.view ?: MoxonView.GENERIC, dimensions, schematic = true)
            HelpText(R.string.moxon_geometry_note)
            if (published?.hybrid == true) HelpText(R.string.moxon_hybrid_note)
            ChoicePicker(stringResource(R.string.loop_output_unit), LengthUnit.entries, unit,
                { stringResource(it.labelResource()) }, { unit = it }, "moxon-units")
            dimensions.valuesMm.forEachIndexed { index, mm ->
                val dimension = MoxonDimension.entries[index]
                Text("${dimension.mark} · ${stringResource(dimension.label)} — ${length(mm)}")
            }
            published?.sourceUnit?.let { original ->
                MoxonDisclosure(R.string.moxon_original) {
                    dimensions.valuesMm.forEachIndexed { index, mm ->
                        Text("${MoxonDimension.entries[index].mark} — ${context.moxonNumber(mm / original.mm)}${original.symbol}")
                    }
                }
            }
            if (published?.hybrid == true) {
                MoxonHeading(R.string.moxon_yagi)
                moxonYagiLines(context, length).forEach { Text(it) }
            }
            MoxonDisclosure(R.string.moxon_formulas) {
                Text("E = B + C + D\nDE = A + 2B\nREF = A + 2D")
                HelpText(R.string.moxon_lengths)
                if (published == null) {
                    Text("λ (mm) = 299792.458 / f (MHz)\nt = log₁₀(d / λ)")
                    Text("A/λ = −0.0008571428571 t² − 0.009571428571 t + 0.3398571429\n" +
                        "B/λ = −0.002142857143 t² − 0.02035714286 t + 0.008285714286\n" +
                        "C/λ = 0.001809523381 t² + 0.01780952381 t + 0.05164285714\n" +
                        "D/λ = 0.001 t + 0.07178571429")
                }
            }
        } else if (published != null) {
            HelpText(R.string.moxon_partial)
        }
        if (published != null && published.facts.isNotEmpty()) {
            MoxonDisclosure(R.string.moxon_facts) { published.facts.forEach { HelpText(it) } }
        }
        if (stock.isNotEmpty()) {
            MoxonHeading(R.string.moxon_stock)
            MoxonInput(rawAllowance, R.string.moxon_allowance, "moxon-allowance", allowance == null,
                R.string.yagi_invalid) { rawAllowance = it }
            HelpText(R.string.moxon_allowance_note)
            adjustedStock?.forEach { (key, mm) ->
                val part = if (key == "Boom") stringResource(R.string.moxon_stock_boom) else key
                Text("$part — ${length(mm)}")
            }
        }
        if (dimensions != null) {
            if (onSave != null) Button(
                onClick = { snapshot?.let { onSave(it, report()) } },
                enabled = canSave, modifier = Modifier.fillMaxWidth().testTag("moxon-save"),
            ) { Text(stringResource(R.string.jpole_copyProject)) }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                    clipboard.setPrimaryClip(ClipData.newPlainText(title, report()))
                    onCopied()
                },
                enabled = canSave, modifier = Modifier.fillMaxWidth().testTag("moxon-copy"),
            ) { Text(stringResource(R.string.moxon_copy)) }
        }
        MoxonDisclosure(R.string.yagi_check) {
            HelpText(R.string.moxon_tuning)
            if (onMeasurements != null) TextButton(onClick = onMeasurements) {
                Text(stringResource(R.string.sweep_tool_title))
            }
        }
        HorizontalDivider()
        MoxonHeading(R.string.yagi_rights)
        Text(source.author)
        source.terms?.let { HelpText(it) }
        TextButton(onClick = { openSourceWithFeedback(context, source.url) }) { Text(source.title) }
    }
}

private fun Context.moxonLabel(profile: MoxonProfile): String = buildList {
    profile.band?.let { add("$it " + getString(if (profile.centimeterBand) R.string.cm else R.string.m)) }
    add(profile.name)
    if (profile.frequencies.isNotEmpty()) add(profile.frequencies.joinToString(" / ") { frequencyWithUnit(it, MEGAHERTZ) })
}.joinToString(" · ")

private fun Context.moxonNumber(value: Double): String = NumberFormat.getNumberInstance(resources.configuration.locales[0])
    .apply { isGroupingUsed = false; maximumFractionDigits = 8 }.format(value)

private fun moxonYagiLines(context: Context, length: (Double) -> String) =
    moxonSatelliteYagi.mapIndexed { index, (position, mm) ->
        val name = if (index == 0) context.getString(R.string.moxon_coupled) else context.getString(R.string.moxon_director, index)
        "$name — ${length(position)} / ${length(mm)}"
    }

private fun moxonSnapshot(
    profile: MoxonProfile?,
    frequency: Frequency?,
    diameter: Double?,
    dimensions: MoxonDimensions,
    unit: LengthUnit,
    allowance: Double?,
    stock: List<Pair<String, Double>>,
) = CalculationSnapshot(
    model = profile?.id ?: MoxonEstimate.MODEL,
    revision = profile?.revision ?: MoxonEstimate.REVISION,
    inputs = buildList {
        if (profile != null) {
            profile.frequencies.forEachIndexed { index, hz ->
                add(SnapshotValue(if (index == 0) "frequency" else "moxon_second_frequency", hz.toDouble(), SnapshotUnit.HERTZ))
            }
            if (profile.frequencies.isEmpty()) add(SnapshotValue("moxon_band", checkNotNull(profile.band).toDouble(), SnapshotUnit.METERS))
        } else {
            add(SnapshotValue("frequency", checkNotNull(frequency).hertz.toDouble(), SnapshotUnit.HERTZ))
            add(SnapshotValue("moxon_diameter", checkNotNull(diameter) / 1000, SnapshotUnit.METERS))
        }
        add(SnapshotValue("moxon_unit", unit.ordinal.toDouble(), SnapshotUnit.FACTOR))
        allowance?.let { add(SnapshotValue("moxon_allowance", it / 1000, SnapshotUnit.METERS)) }
    },
    outputs = buildList {
        dimensions.valuesMm.forEachIndexed { index, mm ->
            add(SnapshotValue(MoxonDimension.entries[index].metric, mm / 1000, SnapshotUnit.METERS))
        }
        if (profile?.hybrid == true) moxonSatelliteYagi.forEachIndexed { index, (position, length) ->
            add(SnapshotValue("moxon_yagi_position_$index", position / 1000, SnapshotUnit.METERS))
            add(SnapshotValue("moxon_yagi_length_$index", length / 1000, SnapshotUnit.METERS))
        }
        stock.forEachIndexed { index, (_, mm) ->
            add(SnapshotValue("moxon_stock_$index", mm / 1000, SnapshotUnit.METERS))
        }
    },
)

@Composable
private fun MoxonHeading(label: Int) {
    Text(stringResource(label), Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun MoxonDisclosure(label: Int, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(label) + if (expanded) " ⌃" else " ⌄")
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun MoxonInput(
    text: String, label: Int, tag: String, error: Boolean, errorLabel: Int = R.string.moxon_invalid, onChange: (String) -> Unit,
) {
    OutlinedTextField(text, { onChange(it.boundedNumericText()) },
        label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error,
        supportingText = if (error) {{ Text(stringResource(errorLabel)) }} else null,
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun MoxonMenu(label: String, tag: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
            Text(label, Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { expanded = false; onSelect(id) },
                    modifier = Modifier.testTag("$tag-$id"),
                )
            }
        }
    }
}
