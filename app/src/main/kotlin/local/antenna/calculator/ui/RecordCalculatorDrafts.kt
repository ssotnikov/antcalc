package local.antenna.calculator.ui

import local.antenna.calculator.core.journal.SnapshotUnit.*
import local.antenna.calculator.core.layout.*
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.core.units.LengthUnit
import local.antenna.calculator.state.*

internal fun RecordCalculatorRequest.restoreDraft(current: CalculatorDraft): CalculatorDraft {
    return when (page) {
        Page.CALCULATOR -> {
            val trimming = snapshot?.model == "trimming" || snapshot == null && lengthNm != null && resonanceHz != null
            val fields = current.fields.toMutableMap()
            if (trimming) {
                fields[FieldKey.ACTUAL_LENGTH] = NumericDraft(if (snapshot == null) lengthNm?.let { recordNumber(it / 1e6) }.orEmpty()
                    else text("length", NANOMETERS, 1e-6), LengthUnit.MILLIMETERS.name)
                fields[FieldKey.MEASURED] = NumericDraft(if (snapshot == null) resonanceHz?.let { recordNumber(it / 1e6) }.orEmpty()
                    else text("resonance", HERTZ, 1e-6), FrequencyUnit.MEGAHERTZ.name)
                fields[FieldKey.TARGET] = NumericDraft(text("target", HERTZ, 1e-6), FrequencyUnit.MEGAHERTZ.name)
            } else {
                fields[FieldKey.SIZING_FREQUENCY] = NumericDraft(text("frequency", HERTZ, 1e-6), FrequencyUnit.MEGAHERTZ.name)
                fields[FieldKey.FACTOR] = NumericDraft(text("factor", FACTOR), "FACTOR")
            }
            current.copy(fields = fields, settings = current.settings.copy(antennaType = type,
                section = if (trimming) Section.TRIMMING else Section.SIZING))
        }
        Page.INVERTED_V -> current.copy(invertedV = InvertedVDraft(
            mode = if (value("frequency", HERTZ) != null) InvertedVMode.ESTIMATED else InvertedVMode.MEASURED,
            fields = mapOf(InvertedVField.ARM to text("dipole_arm", METERS), InvertedVField.HEIGHT to text("apex_height", METERS),
                InvertedVField.ANGLE to text("included_angle", DEGREES), InvertedVField.FREQUENCY to text("frequency", HERTZ, 1e-6),
                InvertedVField.FACTOR to text("factor", FACTOR))))
        Page.GROUND_PLANE -> current.copy(groundPlane = GroundPlaneDraft(
            mode = if (value("frequency", HERTZ) != null) GroundPlaneMode.ESTIMATED else GroundPlaneMode.MANUAL,
            fields = mapOf(GroundPlaneField.FREQUENCY to text("frequency", HERTZ, 1e-6),
                GroundPlaneField.WHIP_FACTOR to text("gp_whip_factor", FACTOR), GroundPlaneField.RADIAL_FACTOR to text("gp_radial_factor", FACTOR),
                GroundPlaneField.WHIP to text("gp_whip", METERS), GroundPlaneField.RADIAL to text("gp_radial", METERS),
                GroundPlaneField.HEIGHT to text("gp_base", METERS), GroundPlaneField.ANGLE to text("gp_slope", DEGREES))))
        Page.FULL_WAVE_LOOP -> current.copy(fullWaveLoop = FullWaveLoopDraft(
            shape = LoopShape.entries.firstOrNull { it.sides.toDouble() == value("loop_shape", FACTOR) } ?: LoopShape.SQUARE,
            mode = if (value("frequency", HERTZ) != null) LoopMode.FREQUENCY else LoopMode.PERIMETER,
            model = value("loop_model", FACTOR)?.toInt()?.let { LoopModel.entries.getOrNull(it) } ?: LoopModel.CUSTOM,
            feed = value("loop_feed", FACTOR)?.toInt()?.let { LoopFeed.entries.getOrNull(it) } ?: LoopFeed.BASE,
            fields = FullWaveLoopDraft().fields.mapValues { (field, _) -> when (field) {
                LoopField.FREQUENCY -> text("frequency", HERTZ, 1e-6)
                LoopField.PERIMETER -> if (snapshot == null) lengthNm?.let { recordNumber(it / 1e9) }.orEmpty() else text("loop_perimeter", METERS)
                LoopField.FACTOR -> text("loop_factor", FACTOR)
                else -> ""
            } }))
        else -> current
    }
}
