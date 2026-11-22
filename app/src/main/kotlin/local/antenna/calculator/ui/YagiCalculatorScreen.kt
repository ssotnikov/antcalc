package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.flow.drop
import local.antenna.calculator.R
import local.antenna.calculator.nec.OpenNec
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.layout.R3udkYagi
import local.antenna.calculator.core.layout.YagiCutList
import local.antenna.calculator.core.layout.YagiDrivenCut
import local.antenna.calculator.core.layout.YagiDesign
import local.antenna.calculator.core.layout.YagiMatchingCable
import local.antenna.calculator.core.layout.YagiElement
import local.antenna.calculator.core.layout.calculateR3udkCutList
import local.antenna.calculator.core.layout.calculateYagiCutList
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit

private const val DK_HOME = "https://dk7zb.darc.de/"
private const val DG_HOME = "https://www.dg7ybn.de/"

internal enum class YagiProfile(
    val design: YagiDesign, val label: String, val hz: List<Long>,
    val mount: Int, val feed: Int, val original: String, val terms: String?, val rights: Int,
    val measure: Int = R.string.yagi_measure,
) {
    YBN(YagiDesign.YBN, "DG7YBN · YBN 2‑5m", listOf(144_300_000),
        R.string.yagi_dg_mount, R.string.yagi_dg_feed, DG_HOME + "144MHz/144_5elem.htm", DG_HOME, R.string.yagi_dg_rights, R.string.yagi_dg_measure),
    R3(YagiDesign.R3, "R3UDK", listOf(R3udkYagi.FREQUENCY_HZ),
        R.string.yagi_r3_mount, R.string.yagi_r3_feed, LIZAALERT_YAGI_URL, null, R.string.yagi_r3_rights),
    DK2(YagiDesign.DK2, "DK7ZB · 2 m · 3 el. · 50 Ω", listOf(144_000_000, 146_000_000),
        R.string.yagi_dk2_mount, R.string.yagi_dk2_feed, DK_HOME + "PVC-Yagis/3-Ele-2m.htm", DK_HOME, R.string.yagi_dk_rights),
    DK50(YagiDesign.DK50, "DK7ZB · 70 cm · 5 el. · 50 Ω", listOf(430_000_000, 440_000_000),
        R.string.yagi_dk50_mount, R.string.yagi_dk50_feed, DK_HOME + "70cm/5.htm", DK_HOME, R.string.yagi_dk_rights),
    DK28(YagiDesign.DK28, "DK7ZB · 70 cm · 5 el. · 28 Ω", listOf(432_000_000),
        R.string.yagi_dk28_mount, R.string.yagi_dk28_feed, DK_HOME + "70cm/5.htm", DK_HOME, R.string.yagi_dk_rights),
    DUO(YagiDesign.DUO, "DK7ZB · Duoband 2+2", listOf(144_000_000, 146_000_000, 430_000_000, 440_000_000),
        R.string.yagi_duo_mount, R.string.yagi_duo_feed, DK_HOME + "Duoband/duoband_2-70_2-3.htm", DK_HOME, R.string.yagi_dk_rights, R.string.yagi_duo_measure),
    GTV(YagiDesign.GTV, "DG7YBN · GTV 2‑6m", listOf(144_100_000),
        R.string.yagi_gtv_mount, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV15(YagiDesign.GTV15, "DG7YBN · GTV 2‑6m · 15 × 15", listOf(144_100_000),
        R.string.yagi_mount_gtv_15, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_SP6(YagiDesign.GTV_SP6, "DG7YBN · GTV 2‑6m · SP6GWN · 20 × 20", listOf(144_100_000),
        R.string.yagi_mount_gtv_sp6gwn, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_STAUFF(YagiDesign.GTV_STAUFF, "DG7YBN · GTV 2‑6m · Stauff · 1″ × 1″", listOf(144_100_000),
        R.string.yagi_mount_gtv_stauff, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_THROUGH4(YagiDesign.GTV_THROUGH4, "DG7YBN · GTV 2‑6m · Ø4 · 20 × 20 × 2", listOf(144_100_000),
        R.string.yagi_mount_gtv_through_4, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_THROUGH476(YagiDesign.GTV_THROUGH476, "DG7YBN · GTV 2‑6m · Ø4.76 · 1″ × 1″", listOf(144_100_000),
        R.string.yagi_mount_gtv_through_476, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_DATV(YagiDesign.GTV_DATV, "DG7YBN · GTV 2‑6m · DATV · 20 × 20", listOf(146_500_000),
        R.string.yagi_mount_gtv_datv, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_DATV_THROUGH(YagiDesign.GTV_DATV_THROUGH, "DG7YBN · GTV 2‑6m · DATV · 25 × 25 × 1.5", listOf(146_500_000),
        R.string.yagi_mount_gtv_datv_through, R.string.yagi_gtv_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_XPOL_STAUFF(YagiDesign.GTV_XPOL_STAUFF, "DG7YBN · GTV 2‑6m · xpol · Stauff", listOf(144_100_000),
        R.string.yagi_mount_gtv_xpol_stauff, R.string.yagi_xpol_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_XPOL_8(YagiDesign.GTV_XPOL_8, "DG7YBN · GTV 2‑6m · xpol · 20 × 20", listOf(145_500_000),
        R.string.yagi_mount_gtv_xpol_8, R.string.yagi_xpol_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_SAT(YagiDesign.GTV_SAT, "DG7YBN · GTV 2‑6m · xpol SAT · Stauff", listOf(145_800_000),
        R.string.yagi_mount_gtv_sat, R.string.yagi_sat_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    GTV_XPOL_THROUGH4(YagiDesign.GTV_XPOL_THROUGH4, "DG7YBN · GTV 2‑6m · xpol · Ø4", listOf(144_100_000),
        R.string.yagi_mount_gtv_xpol_through4, R.string.yagi_xpol_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    NT1K(YagiDesign.NT1K, "NT1K · Tape Measure Yagi · 2 m", listOf(147_054_176),
        R.string.yagi_nt1k_mount, R.string.yagi_nt1k_feed, NT1K_TAPE_YAGI_URL, null,
        R.string.yagi_nt1k_rights, R.string.yagi_nt1k_measure),
    GTV_XPOL_THROUGH476(YagiDesign.GTV_XPOL_THROUGH476, "DG7YBN · GTV 2‑6m · xpol · Ø4.76", listOf(144_100_000),
        R.string.yagi_mount_gtv_xpol_through476, R.string.yagi_xpol_feed, DG_HOME + "144MHz/144_GTV2_6m.htm", DG_HOME, R.string.yagi_dg_rights),
    ;

    val id get() = design.id
    val hasBentDipole get() = id.startsWith("gtv")
    val datv get() = id.startsWith("gtv_datv")
    val dualPlane get() = design.dualPlane
    val usesNominalXpolFrequency get() = dualPlane && hz.single() == 144_100_000L
    val fixedOffsets get() = id.startsWith("gtv_through") || this == GTV_DATV_THROUGH
    val matchingCables get() = if (this == DK28) YagiMatchingCable.entries else emptyList()
    val drivenCutSource get() = when (this) {
        YBN -> YBN_DUBUS_URL
        DUO -> DK7ZB_DUOBAND_ARTICLE_URL
        DK50, DK28 -> NUXCOM_DK7ZB_URL
        GTV, GTV_DATV, GTV_DATV_THROUGH, GTV_XPOL_STAUFF, GTV_XPOL_8,
        GTV_XPOL_THROUGH4, GTV_XPOL_THROUGH476, GTV_SAT -> DG7YBN_BENT_DIPOLE_URL
        else -> null
    }
    val buildSource get() = when (this) {
        DK2 -> DK_HOME + "PVC-Yagis/PVC-details.htm"
        DK28 -> DK_HOME + "70cm/70cm-kurz.htm"
        NT1K -> NT1K_TAPE_YAGI_DRAWING_URL
        else -> original
    }

    fun frequency(context: Context): String = hz.chunked(2).joinToString(" / ") { range ->
        range.joinToString(" – ") { context.frequencyWithUnit(it, FrequencyUnit.MEGAHERTZ) }
    }
}

private val inputFields = listOf(R.string.yagi_allowance, R.string.yagi_rear, R.string.yagi_front)
private val decimal = Regex("(?:[0-9]{1,4}(?:[.,][0-9]{1,3})?|[.,][0-9]{1,3})")

private fun Context.yagiCalculatorPreferences() =
    applicationContext.getSharedPreferences("yagi_calculator", Context.MODE_PRIVATE)

internal fun Context.selectYagiCalculatorProfile(profile: YagiProfile) {
    yagiCalculatorPreferences().edit { putString("profile", profile.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YagiCalculatorScreen(
    onGuide: () -> Unit,
    onMeasurements: (() -> Unit)?,
    onSave: ((CalculationSnapshot, String) -> Unit)?,
    onCopied: () -> Unit,
    onSegmentation: ((ArrayList<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember(context.applicationContext) {
        context.yagiCalculatorPreferences()
    }
    val record = LocalCalculatorRecord.current
    var profile by rememberSaveable {
        mutableStateOf(YagiProfile.entries.firstOrNull { it.design.available && it.id == (record?.snapshot?.let { snapshot -> YagiProfile.entries.firstOrNull { it.design.model == snapshot.model }?.id } ?: prefs.getString("profile", null)) } ?: YagiProfile.R3)
    }
    var unit by rememberSaveable {
        mutableStateOf(record?.value("yagi_unit", SnapshotUnit.FACTOR)?.toInt()?.let { LengthUnit.entries.getOrNull(it) } ?: LengthUnit.entries.firstOrNull { it.name == prefs.getString("unit", null) } ?: LengthUnit.MILLIMETERS)
    }
    val driverPlanes = remember(profile) { profile.design.driverPlanes() }
    val driverLabels = driverPlanes.flatMap { it.labels }
    fun initialField(index: Int, fallback: String = ""): String {
        if (record == null) return prefs.getString("${profile.id}_$index", fallback).orEmpty().take(24)
        val keys = listOf("yagi_allowance", "yagi_rear", "yagi_front") + driverPlanes.flatMapIndexed { plane, item ->
            listOf("gap", "bend", "setback").take(item.labels.size).map { "${profile.driverMetricPrefix(plane)}_$it" }
        }
        return keys.getOrNull(index)?.let { record.text(it, SnapshotUnit.METERS, 1000.0) }.orEmpty()
    }
    val fields = key(profile) {
        val defaultOffset = if (profile.hasBentDipole) GTV_REAR_OFFSET_MM.toInt().toString() else ""
        listOf(
            rememberTextFieldState(initialField(0)),
            rememberTextFieldState(if (profile.fixedOffsets) defaultOffset else initialField(1, defaultOffset)),
            rememberTextFieldState(if (profile.fixedOffsets) defaultOffset else initialField(2, defaultOffset)),
        ) + driverLabels.indices.map { index ->
            rememberTextFieldState(initialField(inputFields.size + index))
        }
    }
    val draft = fields.map { it.text.toString() }
    LaunchedEffect(prefs, profile, fields) {
        if (record != null) return@LaunchedEffect
        val prefix = profile.id
        snapshotFlow { fields.map { it.text.toString() } }.drop(1).collect { entries ->
            prefs.edit { entries.forEachIndexed { index, value -> putString("${prefix}_$index", value) } }
        }
    }
    val values = draft.map { raw ->
        raw.trim().takeIf(decimal::matches)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }
    }
    var fieldOffset = inputFields.size
    val driverErrors = mutableListOf<Boolean>()
    val driverGeometry = driverPlanes.map { plane ->
        val indices = fieldOffset until fieldOffset + plane.labels.size
        fieldOffset += plane.labels.size
        val measured = indices.map { values[it] }
        driverErrors += indices.mapIndexed { index, field -> draft[field].isNotBlank() && plane.invalid(measured, index) }
        if (measured.indices.any { plane.invalid(measured, it) }) null
        else YagiDriverGeometry(checkNotNull(measured[0]), measured.getOrNull(1), measured.getOrNull(2))
    }
    val driverCuts = driverPlanes.zip(driverGeometry).map { (plane, geometry) -> geometry?.let(plane::cut) }
    val horizontalCut = driverCuts.firstOrNull() ?: profile.design.drivenCut
    val verticalCut = driverCuts.getOrNull(1)
    val errors = inputFields.indices.map { draft[it].isNotBlank() && values[it] == null }
    val elements = profile.design.elements
    val cut = if (errors.none { it } && driverErrors.none { it }) calculateYagiCutList(profile.design, values[0] ?: 0.0, values[1], values[2]) else null
    val driven = listOf(horizontalCut, verticalCut)
    val profileLabel: (YagiProfile) -> String = {
        if (it == YagiProfile.NT1K) it.label else "${it.label} · ${it.frequency(context)}"
    }
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberPageScrollState())
            .padding(20.dp).testTag("yagi-calculator"), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val geometry = remember(profile) {
            if (profile.design.restricted) {
                context.assets.open("antennas/dg7ybn.json").bufferedReader().use {
                    readAntennaVector(org.json.JSONObject(it.readText()), "yagi-${profile.design.ordinal}")
                }
            } else null
        }
        if (!profile.design.usesTape) {
            RotatingAntenna("yagi-${profile.design.ordinal}", profileLabel(profile), geometry = geometry, baseHeight = 160.dp)
        }
        YagiProfilePicker(profile, profileLabel) { selected ->
            val previousId = profile.id
            if (record == null) prefs.edit {
                fields.forEachIndexed { index, field -> putString("${previousId}_$index", field.text.toString()) }
                putString("profile", selected.id)
            }
            profile = selected
        }
        val frequencyLabel = if (profile == YagiProfile.NT1K) R.string.yagi_nt1k_measure_frequency else R.string.frequency
        YagiProperty(frequencyLabel, profile.frequency(context))
        if (profile.usesNominalXpolFrequency) HelpText(R.string.yagi_xpol_nominal)
        YagiProperty(R.string.yagi_mount, stringResource(profile.mount))
        YagiProperty(R.string.yagi_feed, stringResource(profile.feed))
        val published = profile.publishedNotes
        val isGtv = profile.hasBentDipole
        YagiHeading(R.string.yagi_geometry)
        if (profile.dualPlane) {
            val horizontal = driverGeometry.getOrNull(0)
            val vertical = driverGeometry.getOrNull(1)
            YagiHeading(R.string.yagi_horizontal_plane)
            YagiDiagram(elements, isGtv, elements[1].positionMm - 87.0,
                measuredDriver = horizontal, modelSpanMm = driverPlanes[0].spanMm)
            YagiHeading(R.string.yagi_vertical_plane)
            YagiDiagram(profile.design.verticalElements, isGtv, profile.design.verticalElements[1].positionMm - 87.0,
                measuredDriver = vertical, modelSpanMm = driverPlanes[1].spanMm)
            HelpText(R.string.yagi_xpol_scheme)
        } else {
            val tip = if (profile.datv) 186.0 else GTV_TIP_FROM_REFLECTOR_MM
            val scheme = when {
                profile.datv -> R.string.yagi_datv_scheme
                isGtv -> R.string.yagi_gtv_scheme
                else -> R.string.yagi_scheme
            }
            YagiDiagram(elements, isGtv, tip, scheme)
            HelpText(scheme)
        }
        if (onSegmentation != null) {
            YagiHeading(R.string.seg_catalog_title)
            HelpText(when {
                profile.design.usesTape -> R.string.yagi_nt1k_nec
                profile.dualPlane || profile == YagiProfile.DUO -> R.string.seg_catalog_dual
                profile.hasBentDipole || profile == YagiProfile.R3 -> R.string.seg_catalog_bent
                else -> R.string.seg_catalog_gap
            })
            driven.forEachIndexed { index, cut ->
                if (cut != null) {
                    val gap = stringResource(R.string.seg_catalog_known_gap, matchingNumber(context, cut.gapMm, 3))
                    val plane = if (index == 0) R.string.yagi_horizontal_plane else R.string.yagi_vertical_plane
                    Text(if (profile.dualPlane) "${stringResource(plane)} · $gap" else gap,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            var exampleExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }
            PanelButton(exampleExpanded, { exampleExpanded = !exampleExpanded }, Modifier.testTag("catalog-segmentation-example")) {
                Text(stringResource(R.string.seg_catalog_example))
            }
            if (exampleExpanded) {
                HelpText(R.string.seg_catalog_ideal)
                val example = dk7zbSegmentationElements
                val plan = OpenNec.segmentation(example, 146_000_000)
                SegmentDiagram(example, plan.counts, 0)
                TextButton({ onSegmentation(dk7zbSegmentationSeed()) },
                    Modifier.testTag("open-catalog-segmentation")) {
                    Text(stringResource(R.string.seg_open))
                }
            }
        }
        YagiHeading(R.string.yagi_dimensions)
        ChoicePicker(stringResource(R.string.loop_output_unit), LengthUnit.entries, unit,
            { stringResource(it.labelResource()) },
            { selected -> unit = selected; if (record == null) prefs.edit { putString("unit", selected.name) } }, "yagi-units")
        if (profile.dualPlane) {
            YagiHeading(R.string.yagi_horizontal_plane)
            YagiTable(elements, unit, isGtv, null, axialCoordinates = true)
            YagiHeading(R.string.yagi_vertical_plane)
            YagiTable(profile.design.verticalElements, unit, isGtv, null, axialCoordinates = true)
        } else YagiTable(elements, unit, isGtv, values[1], selectable = !profile.design.restricted)
        if (published != null) {
            YagiHeading(R.string.yagi_published)
            HelpText(published)
        }
        YagiHeading(R.string.yagi_cuts)
        HelpText(profile.cutConditions())
        inputFields.forEachIndexed { index, label ->
            if (profile.dualPlane && index > 0) return@forEachIndexed
            AppTextField(
                state = fields[index],
                label = stringResource(label),
                example = if (index == 0) "0" else "20",
                inputTransformation = decimalInputTransformation(limit = 24),
                lineLimits = TextFieldLineLimits.SingleLine,
                readOnly = profile.fixedOffsets && index > 0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errors[index],
                supportingText = if (errors[index]) {{ Text(stringResource(R.string.yagi_invalid)) }} else null,
                modifier = Modifier.fillMaxWidth().testTag("yagi-input-$index"),
            )
        }
        if (driverLabels.isNotEmpty()) {
            YagiHeading(R.string.yagi_driver_setup)
            HelpText(if (profile.dualPlane) R.string.yagi_xpol_input_help else R.string.yagi_dk_gap_help)
            if (profile == YagiProfile.DK50 || profile == YagiProfile.DK28) {
                val gapField = fields[inputFields.size]
                OutlinedButton(
                    onClick = { if (gapField.text.isBlank()) gapField.edit { replace(0, length, "10") } },
                    enabled = draft[inputFields.size].isBlank(),
                    modifier = Modifier.testTag("yagi-driver-example"),
                ) { Text(stringResource(R.string.yagi_driver_example)) }
            }
            driverLabels.forEachIndexed { index, label ->
                if (profile.dualPlane && index % 3 == 0) {
                    YagiHeading(if (index == 0) R.string.yagi_horizontal_plane else R.string.yagi_vertical_plane)
                }
                AppTextField(
                    state = fields[inputFields.size + index],
                    label = stringResource(label),
                    inputTransformation = decimalInputTransformation(limit = 24),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = driverErrors[index],
                    supportingText = if (driverErrors[index]) {{ Text(stringResource(R.string.yagi_driver_invalid)) }} else null,
                    modifier = Modifier.fillMaxWidth().testTag("yagi-driver-$index"),
                )
            }
        }
        cut?.let { YagiProperty(R.string.yagi_span, yagiLength(context, it.spanMm, unit)) }
        if (cut != null) {
            YagiProperty(R.string.yagi_boom, cut.boomMm?.let { yagiLength(context, it, unit) }
                ?: stringResource(R.string.yagi_unset))
            if (profile.dualPlane) YagiHeading(R.string.yagi_horizontal_plane)
            cut.blanksMm.forEachIndexed { index, mm ->
                if (elements[index].id == "de" && horizontalCut != null) return@forEachIndexed
                YagiProperty(yagiElementLabel(elements[index].id), mm?.let { yagiLength(context, it, unit) }
                    ?: stringResource(R.string.yagi_blank_unknown))
            }
            horizontalCut?.let { driven ->
                YagiProperty(R.string.yagi_driven_gap, drivenCutValue(context, profile, driven.gapMm, unit))
                YagiProperty(R.string.yagi_driven_two_halves, drivenCutValue(context, profile, driven.halfBlankMm, unit))
            }
            if (profile.dualPlane) {
                YagiHeading(R.string.yagi_vertical_plane)
                cut.verticalBlanksMm.forEachIndexed { index, mm ->
                    val element = profile.design.verticalElements[index]
                    if (element.id == "de" && verticalCut != null) return@forEachIndexed
                    YagiProperty(yagiElementLabel(element.id), mm?.let { yagiLength(context, it, unit) }
                        ?: stringResource(R.string.yagi_blank_unknown))
                }
                verticalCut?.let { driven ->
                    YagiProperty(R.string.yagi_driven_gap, yagiLength(context, driven.gapMm, unit))
                    YagiProperty(R.string.yagi_driven_two_halves, yagiLength(context, driven.halfBlankMm, unit))
                }
            }
        }
        if (profile.matchingCables.isNotEmpty()) {
            YagiHeading(R.string.yagi_matching_title)
            Text(stringResource(R.string.yagi_matching_diagram), color = MaterialTheme.colorScheme.secondary)
            HelpText(R.string.yagi_matching_topology)
            profile.matchingCables.forEach { cable ->
                YagiProperty(cable.label(), stringResource(R.string.yagi_matching_pair, yagiLength(context, cable.sectionMm, unit)))
            }
        }
        HelpText(profile.cutNote())
        if (profile.design.restricted) HelpText(R.string.yagi_dg_no_transfer)
        if (onSave != null) {
            Button(onClick = {
                if (cut != null) onSave(yagiSnapshot(profile, values, cut, unit, driverGeometry, driven),
                    yagiReport(context, profile, cut, values, unit, driverGeometry, driven))
            }, enabled = cut != null, modifier = Modifier.fillMaxWidth().testTag("yagi-save")) {
                Text(stringResource(R.string.jpole_copyProject))
            }
        }
        OutlinedButton(onClick = {
            if (cut != null) {
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@OutlinedButton
                clipboard.setPrimaryClip(ClipData.newPlainText(profile.label, yagiReport(context, profile, cut, values, unit, driverGeometry, driven)))
                onCopied()
            }
        }, enabled = cut != null && !profile.design.restricted, modifier = Modifier.fillMaxWidth().testTag("yagi-copy")) {
            Text(stringResource(R.string.yagi_copy))
        }
        if (profile == YagiProfile.R3) {
            TextButton(onClick = onGuide, modifier = Modifier.testTag("yagi-open-guide")) { Text(stringResource(R.string.yagi_guide)) }
        } else {
            WebLinkButton(onClick = { openSourceWithFeedback(context, profile.buildSource) }) {
                Text(stringResource(R.string.yagi_external_guide))
            }
        }
        YagiHeading(R.string.yagi_check)
        HelpText(profile.measure)
        if (onMeasurements != null) {
            TextButton(onClick = onMeasurements) { Text(stringResource(R.string.sweep_tool_title)) }
        }
        HorizontalDivider()
        YagiHeading(R.string.yagi_rights)
        if (profile.design.restricted) HelpText(R.string.yagi_dg_notice)
        HelpText(profile.rights)
        WebLinkButton(onClick = { openSourceWithFeedback(context, profile.original) }) {
            Text(stringResource(R.string.yagi_original))
        }
        profile.drivenCutSource?.let { source ->
            WebLinkButton(onClick = { openSourceWithFeedback(context, source) }) {
                Text(stringResource(R.string.yagi_driven_source))
            }
        }
        profile.terms?.let { terms ->
            WebLinkButton(onClick = { openSourceWithFeedback(context, terms) }) { Text(stringResource(R.string.yagi_policy)) }
        }
        if (profile == YagiProfile.NT1K) {
            WebLinkButton(onClick = { openSourceWithFeedback(context, HACKERMAGNET_TAPE_YAGI_URL) }) {
                Text(stringResource(R.string.yagi_nt1k_printables))
            }
        }
    }
}

@Composable
private fun YagiProfilePicker(
    profile: YagiProfile, label: (YagiProfile) -> String, select: (YagiProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YagiHeading(R.string.yagi_profile)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("yagi-profile")) {
                Text(label(profile), Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.width(maxWidth)) {
                (listOf(YagiProfile.R3, YagiProfile.YBN, YagiProfile.GTV) + YagiProfile.entries)
                    .distinct().filter { it.design.available }.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(label(item), style = MaterialTheme.typography.bodyMedium) },
                        onClick = { expanded = false; select(item) },
                        modifier = Modifier.testTag("yagi-profile-${item.id}"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun YagiHeading(label: Int) {
    Text(stringResource(label), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun YagiProperty(label: Int, value: String) {
    Text(stringResource(R.string.journal_property, stringResource(label), value))
}

@Composable
private fun YagiDiagram(
    elements: List<YagiElement>, isGtv: Boolean, tipPositionMm: Double,
    descriptionId: Int = R.string.yagi_xpol_scheme,
    measuredDriver: YagiDriverGeometry? = null,
    modelSpanMm: Double? = null,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val description = if (measuredDriver == null) stringResource(descriptionId) else stringResource(
        R.string.yagi_xpol_measured_scheme,
        yagiLength(context, measuredDriver.gapMm, LengthUnit.MILLIMETERS),
        yagiLength(context, checkNotNull(measuredDriver.bendMm), LengthUnit.MILLIMETERS),
        yagiLength(context, checkNotNull(measuredDriver.setbackMm), LengthUnit.MILLIMETERS),
    )
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val left = size.width * .08f
        val right = size.width - left
        val center = size.height / 2
        drawLine(colors.outline, Offset(0f, center), Offset(size.width, center), 3.dp.toPx())
        val longest = elements.maxOf(YagiElement::overallMm)
        val origin = elements.first().positionMm
        val boomScale = (right - left) / (elements.last().positionMm - origin).toFloat()
        elements.forEachIndexed { index, element ->
            val x = left + boomScale * (element.positionMm - origin).toFloat()
            val transverseScale = center * 1.44f / longest.toFloat()
            val span = if (element.id == "de" && measuredDriver != null) checkNotNull(modelSpanMm) else element.overallMm
            val half = transverseScale * (span / 2).toFloat()
            val stroke = 2.5.dp.toPx()
            val labelColor = yagiDesignationColor(element.id, colors)
            val layout = textMeasurer.measure(yagiDesignation(element.id), style = labelStyle)
            val labelY = if (index % 2 == 0) 0f else size.height - layout.size.height
            drawText(layout, color = labelColor, topLeft = Offset(x - layout.size.width / 2f, labelY))
            if (element.id == "de") {
                val bendHalf = measuredDriver?.bendMm?.let { transverseScale * it.toFloat() }
                    ?: if (isGtv) half * GTV_BEND_FRACTION.toFloat() else half
                val feedGapHalf = measuredDriver?.let { transverseScale * (it.gapMm / 2).toFloat() }
                    ?: minOf(5.dp.toPx(), bendHalf / 2f)
                val tipMm = measuredDriver?.setbackMm?.let { element.positionMm - it } ?: tipPositionMm
                for (side in listOf(-1f, 1f)) {
                    val bend = Offset(x, center + side * bendHalf)
                    drawLine(colors.tertiary, Offset(x, center + side * feedGapHalf), bend, stroke)
                    if (isGtv) {
                        val tip = Offset(left + boomScale * (tipMm - origin).toFloat(), center + side * half)
                        drawLine(colors.tertiary, bend, tip, stroke)
                    }
                }
            } else drawLine(colors.primary, Offset(x, center - half), Offset(x, center + half), stroke)
        }
    }
}

private fun yagiDesignation(id: String): String = id.uppercase()

private fun yagiDesignationColor(id: String, colors: ColorScheme): Color =
    if (id == "de") colors.tertiary else colors.secondary

@Composable
private fun YagiTable(elements: List<YagiElement>, unit: LengthUnit, isGtv: Boolean, rearMm: Double?,
                      axialCoordinates: Boolean = false, selectable: Boolean = true) {
    val context = LocalContext.current
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val width = if (rearMm != null) 700.dp else 580.dp
    ScrollableTable(Modifier.width(width * scale).testTag("yagi-dimensions-table")) {
        val transverseLabel = elements.first().transverseLabel()
        val headers = buildList {
            addAll(listOf(R.string.yagi_element, transverseLabel, if (axialCoordinates) R.string.yagi_gtv_position else R.string.yagi_position))
            if (rearMm != null) add(R.string.yagi_gtv_position)
            add(R.string.yagi_spacing)
            add(if (isGtv) R.string.yagi_gtv_table_length else R.string.yagi_overall)
        }
        YagiRow(headers.map { stringResource(it) }, header = true)
        elements.forEachIndexed { index, element ->
            HorizontalDivider()
            val gap = elements.getOrNull(index - 1)?.let { element.positionMm - it.positionMm }
            YagiRow(buildList {
                add(stringResource(yagiElementLabel(element.id)))
                add(yagiLength(context, element.transverseSizeMm, unit))
                add(yagiLength(context, element.positionMm, unit))
                if (rearMm != null) add(yagiLength(context, element.positionMm + rearMm, unit))
                add(gap?.let { yagiLength(context, it, unit) } ?: "—")
                add(yagiLength(context, element.overallMm, unit))
            }, selectable = selectable, elementId = element.id)
        }
    }
}

@Composable
private fun YagiRow(cells: List<String>, header: Boolean = false, selectable: Boolean = true, elementId: String? = null) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    val designationWidth = 34.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.forEachIndexed { index, cell ->
                val column = Modifier.weight(if (index == 0) 1.5f else 1f)
                if (index == 0 && elementId != null) {
                    Row(column, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(yagiDesignation(elementId), Modifier.width(designationWidth),
                            color = yagiDesignationColor(elementId, colors), fontWeight = FontWeight.SemiBold,
                            style = style)
                        Text(cell, Modifier.weight(1f), color = colors.onSurface, style = style)
                    }
                } else {
                    Text(cell, column, color = if (header) colors.secondary else colors.onSurface, style = style)
                }
            }
        }
    }
    if (selectable) SelectionContainer(content = content) else content()
}

internal fun yagiLength(context: Context, mm: Double, unit: LengthUnit): String {
    val factor = when (unit) {
        LengthUnit.MILLIMETERS -> 1.0
        LengthUnit.CENTIMETERS -> 10.0
        LengthUnit.METERS -> 1000.0
    }
    val locale = context.resources.configuration.locales[0]
    val value = java.text.NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = if (unit == LengthUnit.METERS) 6 else if (unit == LengthUnit.CENTIMETERS) 4 else 3
    }.format(mm / factor)
    return "$value ${context.getString(unit.labelResource())}"
}

internal fun Context.r3udkGuideProjectCalculation(): PendingProjectCalculation {
    val profile = YagiProfile.R3
    val unit = LengthUnit.MILLIMETERS
    val values = listOf(0.0, null, null)
    val cut = calculateR3udkCutList(allowanceMm = 0.0, rearMm = null, frontMm = null)
    return PendingProjectCalculation(
        yagiSnapshot(profile, values, cut, unit),
        note = yagiReport(this, profile, cut, values, unit),
        returnPageOverride = Page.CONSTRUCTION_YAGI,
    )
}

private fun yagiSnapshot(
    profile: YagiProfile, values: List<Double?>, cut: YagiCutList, unit: LengthUnit,
    driverGeometry: List<YagiDriverGeometry?> = emptyList(),
    driven: List<YagiDrivenCut?> = listOf(profile.design.drivenCut),
): CalculationSnapshot {
    val inputs = buildList {
        val primary = profile.hz.take(2).average()
        add(SnapshotValue("frequency", primary, SnapshotUnit.HERTZ))
        if (profile.hz.size > 1) {
            profile.hz.chunked(2).forEachIndexed { index, band ->
                add(SnapshotValue("yagi_band_${index + 1}_low", band.first().toDouble(), SnapshotUnit.HERTZ))
                add(SnapshotValue("yagi_band_${index + 1}_high", band.last().toDouble(), SnapshotUnit.HERTZ))
            }
        }
        if (profile != YagiProfile.R3) {
            profile.design.elements.forEach { element ->
                val dimension = if (element.tapeWidthMm != null) "width" else "diameter"
                add(lengthMetric("yagi_${element.id}_$dimension", element.transverseSizeMm))
            }
        }
        if (profile.dualPlane) {
            // Keep the published lengths in the inputs and the calculated blanks in the results.
            profile.design.elements.forEach { add(lengthMetric("yagi_${it.id}_table", it.overallMm)) }
            profile.design.verticalElements.forEach { add(lengthMetric("yagi_v${it.id}_table", it.overallMm)) }
        }
        add(SnapshotValue("yagi_unit", unit.ordinal.toDouble(), SnapshotUnit.FACTOR))
        add(lengthMetric("yagi_allowance", values[0] ?: 0.0))
        if (!profile.dualPlane) {
            values[1]?.let { add(lengthMetric("yagi_rear", it)) }
            values[2]?.let { add(lengthMetric("yagi_front", it)) }
        }
        driverGeometry.forEachIndexed { index, geometry ->
            if (geometry == null) return@forEachIndexed
            val prefix = profile.driverMetricPrefix(index)
            add(lengthMetric("${prefix}_gap", geometry.gapMm))
            geometry.bendMm?.let { add(lengthMetric("${prefix}_bend", it)) }
            geometry.setbackMm?.let { add(lengthMetric("${prefix}_setback", it)) }
        }
    }
    val outputs = buildList {
        add(lengthMetric("yagi_span", cut.spanMm))
        cut.boomMm?.let { add(lengthMetric("yagi_boom", it)) }
        profile.design.elements.forEachIndexed { index, element ->
            add(lengthMetric("yagi_${element.id}_position", element.positionMm))
            val lengthKey = if (profile.hasBentDipole) "table" else "overall"
            if (!profile.dualPlane) add(lengthMetric("yagi_${element.id}_$lengthKey", element.overallMm))
            if (profile == YagiProfile.R3) add(lengthMetric("yagi_${element.id}_diameter", checkNotNull(element.diameterMm)))
            cut.blanksMm[index]?.let { add(lengthMetric("yagi_${element.id}_blank", it)) }
        }
        profile.design.verticalElements.forEachIndexed { index, element ->
            add(lengthMetric("yagi_v${element.id}_position", element.positionMm))
            cut.verticalBlanksMm[index]?.let { add(lengthMetric("yagi_v${element.id}_blank", it)) }
        }
        driven.forEachIndexed { index, driver ->
            if (driver == null) return@forEachIndexed
            val prefix = profile.driverMetricPrefix(index)
            if (driverGeometry.getOrNull(index) == null) add(lengthMetric("${prefix}_gap", driver.gapMm))
            add(lengthMetric("${prefix}_half_blank", driver.halfBlankMm))
        }
        profile.matchingCables.forEach { add(lengthMetric(it.metricKey, it.sectionMm)) }
    }
    val partialCut = cut.blanksMm.indices.any { index ->
        cut.blanksMm[index] == null &&
            (profile.design.elements[index].id != "de" || driven.firstOrNull() == null)
    } || cut.verticalBlanksMm.indices.any { index ->
        cut.verticalBlanksMm[index] == null &&
            (profile.design.verticalElements[index].id != "de" || driven.getOrNull(1) == null)
    }
    val warnings = if (partialCut) listOf("YAGI_PARTIAL_CUT_LIST") else emptyList()
    val revision = if (driverGeometry.any { it != null }) 2 else profile.design.revision
    return CalculationSnapshot(profile.design.model, revision, inputs, outputs, warnings)
}

private fun YagiProfile.driverMetricPrefix(index: Int) = when {
    index == 1 -> "yagi_vdriven"
    dualPlane -> "yagi_hdriven"
    else -> "yagi_driven"
}

private fun lengthMetric(key: String, mm: Double) = SnapshotValue(key, mm / 1000, SnapshotUnit.METERS)

private fun drivenCutValue(context: Context, profile: YagiProfile, mm: Double, unit: LengthUnit): String {
    val value = yagiLength(context, mm, unit)
    return if (profile == YagiProfile.DUO) "≈ $value" else value
}

private fun YagiElement.transverseLabel() =
    if (tapeWidthMm != null) R.string.yagi_tape_width else R.string.yagi_diameter

private fun YagiProfile.cutConditions() =
    if (design.usesTape) R.string.yagi_nt1k_cut_conditions else R.string.yagi_model_conditions

private fun YagiProfile.cutNote() = when (this) {
    YagiProfile.R3 -> R.string.yagi_formed
    YagiProfile.NT1K -> R.string.yagi_nt1k_cut_note
    else -> R.string.yagi_limited
}

private fun yagiReport(
    context: Context, profile: YagiProfile, cut: YagiCutList, values: List<Double?>, unit: LengthUnit,
    driverGeometry: List<YagiDriverGeometry?> = emptyList(),
    driven: List<YagiDrivenCut?> = listOf(profile.design.drivenCut),
): String = buildString {
    fun line(label: Int, text: String) = appendLine(context.getString(R.string.journal_property, context.getString(label), text))
    fun driverLines(index: Int, driver: YagiDrivenCut) {
        line(R.string.yagi_driven_gap, drivenCutValue(context, profile, driver.gapMm, unit))
        line(R.string.yagi_driven_two_halves, drivenCutValue(context, profile, driver.halfBlankMm, unit))
        driverGeometry.getOrNull(index)?.let { measurement ->
            measurement.bendMm?.let { line(R.string.yagi_bend, yagiLength(context, it, unit)) }
            measurement.setbackMm?.let { line(R.string.yagi_setback, yagiLength(context, it, unit)) }
        }
    }
    val revision = if (driverGeometry.any { it != null }) 2 else profile.design.revision
    if (profile == YagiProfile.NT1K) {
        appendLine("${profile.label} · #$revision")
        line(R.string.yagi_nt1k_measure_frequency, profile.frequency(context))
    } else appendLine("${profile.label} · ${profile.frequency(context)} · #$revision")
    appendLine()
    appendLine(context.getString(profile.mount))
    appendLine(context.getString(profile.feed))
    appendLine(context.getString(profile.cutConditions()))
    appendLine()
    inputFields.forEachIndexed { index, label ->
        if (profile.dualPlane && index > 0) return@forEachIndexed
        val value = values[index] ?: if (index == 0) 0.0 else null
        line(label, value?.let { yagiLength(context, it, LengthUnit.MILLIMETERS) } ?: "—")
    }
    appendLine()
    appendLine(context.getString(R.string.assembly_dimensions_table))
    line(R.string.yagi_span, yagiLength(context, cut.spanMm, unit))
    line(R.string.yagi_boom, cut.boomMm?.let { yagiLength(context, it, unit) } ?: "—")
    if (profile.dualPlane) appendLine(context.getString(R.string.yagi_horizontal_plane))
    profile.design.elements.forEachIndexed { index, element ->
        appendLine()
        appendLine(context.getString(yagiElementLabel(element.id)))
        line(element.transverseLabel(), yagiLength(context, element.transverseSizeMm, unit))
        line(if (profile.dualPlane) R.string.yagi_gtv_position else R.string.yagi_position, yagiLength(context, element.positionMm, unit))
        profile.design.elements.getOrNull(index - 1)?.let { previous ->
            line(R.string.yagi_spacing, yagiLength(context, element.positionMm - previous.positionMm, unit))
        }
        if (!profile.dualPlane) values[1]?.let { rear -> line(R.string.yagi_gtv_position, yagiLength(context, element.positionMm + rear, unit)) }
        line(if (profile.hasBentDipole) R.string.yagi_gtv_table_length else R.string.yagi_overall,
            yagiLength(context, element.overallMm, unit))
        val driver = driven.firstOrNull()
        if (element.id == "de" && driver != null) {
            driverLines(0, driver)
        } else {
            line(R.string.yagi_record_blank, cut.blanksMm[index]?.let { yagiLength(context, it, unit) }
                ?: context.getString(R.string.yagi_blank_unknown))
        }
    }
    if (profile.dualPlane) {
        appendLine()
        appendLine(context.getString(R.string.yagi_vertical_plane))
        profile.design.verticalElements.forEachIndexed { index, element ->
            appendLine(context.getString(yagiElementLabel(element.id)))
            line(element.transverseLabel(), yagiLength(context, element.transverseSizeMm, unit))
            line(R.string.yagi_gtv_position, yagiLength(context, element.positionMm, unit))
            line(R.string.yagi_gtv_table_length, yagiLength(context, element.overallMm, unit))
            val driver = driven.getOrNull(1)
            if (element.id == "de" && driver != null) driverLines(1, driver)
            else line(R.string.yagi_record_blank, cut.verticalBlanksMm[index]?.let { yagiLength(context, it, unit) }
                ?: context.getString(R.string.yagi_blank_unknown))
        }
    }
    appendLine()
    profile.matchingCables.forEach { cable ->
        line(cable.label(), context.getString(R.string.yagi_matching_pair, yagiLength(context, cable.sectionMm, unit)))
    }
    appendLine(context.getString(profile.cutNote()))
    if (driverGeometry.any { it != null }) appendLine(context.getString(R.string.yagi_driver_input_basis))
    profile.publishedNotes?.let { appendLine(context.getString(it)) }
    if (profile.buildSource != profile.original) appendLine(profile.buildSource)
    profile.drivenCutSource?.let { appendLine(it) }
    append(profile.original)
    if (profile == YagiProfile.NT1K) {
        appendLine()
        line(R.string.yagi_nt1k_printables, HACKERMAGNET_TAPE_YAGI_URL)
    }
}
