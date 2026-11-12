package local.antenna.calculator.core.journal

import kotlin.math.abs
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.core.trimming.AntennaType

data class SizingLengthComparison(val estimatedMeters: Double, val measuredMeters: Double)

fun compareSizingLength(calculation: JournalRecord, measured: JournalRecord): SizingLengthComparison? {
    if (calculation.antennaType != measured.antennaType) return null
    val length = measured.measurement?.length ?: return null
    val snapshot = calculation.calculation?.takeIf { it.model == "sizing" && it.revision == 1 } ?: return null
    val key = if (calculation.antennaType == AntennaType.MONOPOLE) "monopole_length" else "dipole_total"
    val estimated = snapshot.outputs.firstOrNull { it.key == key && it.unit == SnapshotUnit.METERS && it.value > 0 }
        ?: return null
    return SizingLengthComparison(estimated.value, length.nanometers / 1e9)
}

enum class SweepComparisonIssue { FREQUENCY_UNKNOWN, FREQUENCY_UNSAMPLED, PLANE_MISMATCH, REFERENCE_UNKNOWN, REFERENCE_MISMATCH, INVALID_POINT }

data class ManualSweepComparison(
    val point: S11Point?,
    val resistanceOhms: Double?,
    val reactanceOhms: Double?,
    val swrDelta: Double?,
    val resistanceDelta: Double?,
    val reactanceDelta: Double?,
    val issues: Set<SweepComparisonIssue>,
)

fun compareManualToSweep(
    manual: JournalMeasurement, sweep: S11Sweep, plane: MeasurementPoint,
): ManualSweepComparison {
    val hz = manual.testFrequency?.hertz?.toDouble()
    val point = hz?.let { requested ->
        val nearest = sweep.nearestIndex(requested.coerceIn(sweep.points.first().hertz, sweep.points.last().hertz))
        nearest?.let(sweep.points::get)
    }
    val impedance = point?.impedance(sweep.referenceOhms)
    // Allow only the rounding error of converting S1P frequency units to hertz.
    val exact = point != null && abs(point.hertz - hz) <= 0.000001
    val samePlane = plane != MeasurementPoint.UNSPECIFIED && plane == manual.point
    val validPoint = point?.swr != null
    val comparable = exact && samePlane && validPoint
    val sameReference = manual.referenceOhms == sweep.referenceOhms
    val issues = buildSet {
        if (hz == null) add(SweepComparisonIssue.FREQUENCY_UNKNOWN)
        else if (!exact) add(SweepComparisonIssue.FREQUENCY_UNSAMPLED)
        if (!samePlane) add(SweepComparisonIssue.PLANE_MISMATCH)
        if (point != null && !validPoint) add(SweepComparisonIssue.INVALID_POINT)
        if (manual.swr != null && manual.referenceOhms == null) add(SweepComparisonIssue.REFERENCE_UNKNOWN)
        else if (manual.swr != null && !sameReference) add(SweepComparisonIssue.REFERENCE_MISMATCH)
    }
    return ManualSweepComparison(
        point, impedance?.first, impedance?.second,
        if (comparable && sameReference) manual.swr?.let { value -> point?.swr?.takeIf(Double::isFinite)?.minus(value) } else null,
        if (comparable) manual.resistanceOhms?.let { impedance?.first?.minus(it) } else null,
        if (comparable) manual.reactanceOhms?.let { impedance?.second?.minus(it) } else null,
        issues,
    )
}
