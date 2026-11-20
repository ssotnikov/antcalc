package local.antenna.calculator.nec

import kotlin.math.hypot

internal fun worstNecSwr(points: List<OpenNec.Point>): OpenNec.Point =
    points.minWith(compareByDescending<OpenNec.Point> { it.swr }.thenBy { it.frequencyHz })

internal data class NecImpedanceShift(val ohms: Double, val frequencyHz: Long)

internal fun maximumNecShift(
    coarse: List<OpenNec.Point>, fine: List<OpenNec.Point>, frequencies: List<Long>,
): NecImpedanceShift? {
    if (frequencies.isEmpty() || coarse.map { it.frequencyHz } != frequencies ||
        fine.map { it.frequencyHz } != frequencies) return null
    return coarse.zip(fine) { a, b ->
        NecImpedanceShift(hypot(b.resistance - a.resistance, b.reactance - a.reactance), a.frequencyHz)
    }.minWith(compareByDescending<NecImpedanceShift> { it.ohms }.thenBy { it.frequencyHz })
}

internal data class NecDirectionRange(val low: Double, val high: Double) {
    val indeterminate get() = low <= 0.0 && high >= 0.0
}

internal fun necDirectionRange(
    coarseBase: List<OpenNec.Point>, coarseCandidate: List<OpenNec.Point>,
    fineBase: List<OpenNec.Point>, fineCandidate: List<OpenNec.Point>, frequencies: List<Long>,
): NecDirectionRange? {
    if (frequencies.isEmpty() || listOf(coarseBase, coarseCandidate, fineBase, fineCandidate)
            .any { series -> series.map { it.frequencyHz } != frequencies }) return null
    val coarse = worstNecSwr(coarseCandidate).swr - worstNecSwr(coarseBase).swr
    val fine = worstNecSwr(fineCandidate).swr - worstNecSwr(fineBase).swr
    return NecDirectionRange(minOf(coarse, fine), maxOf(coarse, fine))
}
