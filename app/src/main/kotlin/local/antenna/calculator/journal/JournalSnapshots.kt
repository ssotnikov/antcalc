package local.antenna.calculator.journal

import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotUnit.*
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.state.GroundPlaneDraft
import local.antenna.calculator.state.GroundPlaneField
import local.antenna.calculator.state.GroundPlaneMode
import local.antenna.calculator.state.InvertedVDraft
import local.antenna.calculator.state.InvertedVMode
import local.antenna.calculator.state.Section
import local.antenna.calculator.state.FullWaveLoopDraft
import local.antenna.calculator.state.LoopField
import local.antenna.calculator.state.LoopMode
import local.antenna.calculator.state.validValue
import local.antenna.calculator.core.layout.LoopModel

internal fun journalSnapshot(state: CalculatorUiState, type: AntennaType, section: Section): CalculationSnapshot? {
    if (!state.editingEnabled || type == AntennaType.FULL_WAVE_LOOP) return null
    return when (section) {
        Section.SIZING -> sizingSnapshot(state)?.let { it.copy(outputs = it.outputsForAntenna(type)) }
        Section.TRIMMING -> (if (state.draft.settings.antennaType == type) state.trimming.result
            else state.trimmingProjection(type))?.let { result ->
            CalculationSnapshot("trimming", 1, listOf(
                snapshot("length", result.input.actualLength.nanometers, NANOMETERS),
                snapshot("resonance", result.input.measuredFrequency.hertz, HERTZ),
                snapshot("target", result.input.targetFrequency.hertz, HERTZ),
            ), buildList {
                add(snapshot("new_length", result.newLengthMeters, METERS))
                add(snapshot("adjustment", result.deltaMeters, METERS))
                result.dipoleArmAdjustment?.let {
                    add(snapshot("new_arm", it.newLengthMeters, METERS))
                    add(snapshot("arm_adjustment", it.deltaMeters, METERS))
                }
            }, result.warnings.map { it.name })
        }
        Section.TOOLS -> null
    }
}

// Capture both sets of sizes while the antenna choice is still pending.
internal fun sizingSnapshot(state: CalculatorUiState): CalculationSnapshot? {
    if (!state.editingEnabled) return null
    val result = state.sizing.result ?: return null
    return CalculationSnapshot("sizing", 1, listOf(
        snapshot("frequency", result.input.frequency.hertz, HERTZ),
        snapshot("factor", result.input.factor.tenThousandths / 10_000.0, FACTOR),
    ), listOf(
        snapshot("wavelength", result.ideal.wavelengthMeters, METERS),
        snapshot("ideal_quarter", result.ideal.monopoleMeters, METERS),
        snapshot("ideal_half", result.ideal.dipoleTotalMeters, METERS),
        snapshot("ideal_five_eighths", result.ideal.fiveEighthsMeters, METERS),
        snapshot("monopole_length", result.monopoleMeters, METERS),
        snapshot("dipole_total", result.dipoleTotalMeters, METERS),
        snapshot("dipole_arm", result.dipoleArmMeters, METERS),
    ))
}

internal fun CalculationSnapshot.outputsForAntenna(type: AntennaType): List<SnapshotValue> {
    if (model != "sizing" || revision != 1) return outputs
    return outputs.filter { metric ->
        when (metric.key) {
            "monopole_length", "ideal_five_eighths" -> type == AntennaType.MONOPOLE
            "ideal_half", "dipole_total", "dipole_arm" -> type == AntennaType.SYMMETRIC_DIPOLE
            else -> true
        }
    }
}

internal fun InvertedVDraft.invertedVSnapshot(): CalculationSnapshot? {
    val result = result()?.takeIf { it.endHeightMeters >= 0 } ?: return null
    return CalculationSnapshot("inverted_v", 1, buildList {
        add(snapshot("apex_height", result.apexMeters, METERS))
        add(snapshot("included_angle", result.angleTenths / 10.0, DEGREES))
        if (mode == InvertedVMode.MEASURED) {
            add(snapshot("dipole_arm", result.armMeters, METERS))
        } else {
            val estimate = checkNotNull(estimateInput())
            add(snapshot("frequency", estimate.frequency.hertz, HERTZ))
            add(snapshot("factor", estimate.factor.tenThousandths / 10_000.0, FACTOR))
        }
    }, listOf(
        snapshot("dipole_arm", result.armMeters, METERS),
        snapshot("dipole_total", result.wireMeters, METERS),
        snapshot("end_span", result.spanMeters, METERS),
        snapshot("end_height", result.endHeightMeters, METERS),
    ), if (result.endHeightMeters == 0.0) listOf("ENDS_TOUCH_GROUND") else emptyList())
}

internal fun GroundPlaneDraft.groundPlaneSnapshot(): CalculationSnapshot? {
    val layout = result() as? GroundPlaneResult.Layout ?: return null
    return CalculationSnapshot("ground_plane", 1, buildList {
        add(snapshot("gp_base", layout.baseMeters, METERS))
        add(snapshot("gp_slope", layout.angleTenths / 10.0, DEGREES))
        if (mode == GroundPlaneMode.ESTIMATED) {
            add(snapshot("frequency", checkNotNull(value(GroundPlaneField.FREQUENCY)), HERTZ))
            add(snapshot("gp_whip_factor", checkNotNull(value(GroundPlaneField.WHIP_FACTOR)) / 10_000.0, FACTOR))
            add(snapshot("gp_radial_factor", checkNotNull(value(GroundPlaneField.RADIAL_FACTOR)) / 10_000.0, FACTOR))
        } else {
            add(snapshot("gp_whip", layout.whipMeters, METERS))
            add(snapshot("gp_radial", layout.radialMeters, METERS))
        }
    }, listOf(
        snapshot("gp_whip", layout.whipMeters, METERS),
        snapshot("gp_radial", layout.radialMeters, METERS),
        snapshot("gp_radius", layout.radiusMeters, METERS),
        snapshot("gp_diameter", layout.diameterMeters, METERS),
        snapshot("gp_drop", layout.dropMeters, METERS),
        snapshot("gp_end_height", layout.endHeightMeters, METERS),
        snapshot("gp_tip", layout.tipHeightMeters, METERS),
        snapshot("gp_wire", layout.wireMeters, METERS),
    ), if (layout.endHeightMeters == 0.0) listOf("RADIALS_TOUCH_GROUND") else emptyList())
}

internal fun FullWaveLoopDraft.fullWaveLoopSnapshot(): CalculationSnapshot? {
    val layout = layout() ?: return null
    return CalculationSnapshot("full_wave_loop", 1, buildList {
        add(snapshot("loop_shape", shape.sides, FACTOR))
        add(snapshot("loop_model", model.ordinal, FACTOR))
        add(snapshot("loop_feed", feed.ordinal, FACTOR))
        if (model == LoopModel.CUSTOM) {
            add(snapshot("loop_factor", checkNotNull(number(LoopField.FACTOR).validValue()), FACTOR))
        }
        if (mode == LoopMode.FREQUENCY) add(snapshot("frequency", layout.frequencyMhz * 1e6, HERTZ))
        else add(snapshot("loop_perimeter", layout.perimeterMeters, METERS))
    }, listOf(
        snapshot("loop_frequency", layout.frequencyMhz * 1e6, HERTZ),
        snapshot("loop_perimeter", layout.perimeterMeters, METERS),
        snapshot("loop_side", layout.sideMeters, METERS),
        snapshot("loop_width", layout.widthMeters, METERS),
        snapshot("loop_height", layout.heightMeters, METERS),
    ))
}

private fun snapshot(key: String, value: Number, unit: SnapshotUnit) = SnapshotValue(key, value.toDouble(), unit)
