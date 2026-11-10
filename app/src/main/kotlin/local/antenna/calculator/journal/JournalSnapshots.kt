package local.antenna.calculator.journal

import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotUnit.*
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.Section

internal fun journalSnapshot(state: CalculatorUiState, type: AntennaType, section: Section): CalculationSnapshot? {
    if (!state.editingEnabled) return null
    return when (section) {
        Section.SIZING -> state.sizing.result?.let { result ->
            CalculationSnapshot("sizing", 1, listOf(
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
        Section.TRIMMING -> state.trimming.result?.takeIf { it.input.antennaType == type }?.let { result ->
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

private fun snapshot(key: String, value: Number, unit: SnapshotUnit) = SnapshotValue(key, value.toDouble(), unit)
