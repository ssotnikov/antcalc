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

internal data class NecWorstSwrChange(val before: OpenNec.Point, val after: OpenNec.Point) {
    val delta get() = after.swr - before.swr
}

internal data class NecImpedanceDelta(val resistance: Double, val reactance: Double) {
    val ohms get() = hypot(resistance, reactance)
}

internal data class NecDirectionPoint(
    val frequencyHz: Long,
    val coarse: NecImpedanceDelta,
    val fine: NecImpedanceDelta,
    val commonShift: NecImpedanceDelta,
    val residual: NecImpedanceDelta,
)

internal data class NecDirectionRange(
    val coarse: NecWorstSwrChange,
    val fine: NecWorstSwrChange,
    val largestCommonShift: NecDirectionPoint,
    val largestResidual: NecDirectionPoint,
) {
    val low get() = minOf(coarse.delta, fine.delta)
    val high get() = maxOf(coarse.delta, fine.delta)
    val indeterminate get() = low <= 0.0 && high >= 0.0
}

internal fun necDirectionRange(
    coarseBase: List<OpenNec.Point>, coarseCandidate: List<OpenNec.Point>,
    fineBase: List<OpenNec.Point>, fineCandidate: List<OpenNec.Point>, frequencies: List<Long>,
): NecDirectionRange? {
    if (frequencies.isEmpty() || listOf(coarseBase, coarseCandidate, fineBase, fineCandidate)
            .any { series -> series.map { it.frequencyHz } != frequencies }) return null

    val points = frequencies.mapIndexed { index, frequency ->
        val before = coarseBase[index]
        val after = coarseCandidate[index]
        val fineBefore = fineBase[index]
        val fineAfter = fineCandidate[index]
        val coarse = impedanceChange(before, after)
        val fine = impedanceChange(fineBefore, fineAfter)
        NecDirectionPoint(
            frequency,
            coarse,
            fine,
            NecImpedanceDelta(
                ((fineBefore.resistance - before.resistance) + (fineAfter.resistance - after.resistance)) / 2,
                ((fineBefore.reactance - before.reactance) + (fineAfter.reactance - after.reactance)) / 2,
            ),
            NecImpedanceDelta(fine.resistance - coarse.resistance, fine.reactance - coarse.reactance),
        )
    }
    return NecDirectionRange(
        NecWorstSwrChange(worstNecSwr(coarseBase), worstNecSwr(coarseCandidate)),
        NecWorstSwrChange(worstNecSwr(fineBase), worstNecSwr(fineCandidate)),
        points.minWith(compareByDescending<NecDirectionPoint> { it.commonShift.ohms }.thenBy { it.frequencyHz }),
        points.minWith(compareByDescending<NecDirectionPoint> { it.residual.ohms }.thenBy { it.frequencyHz }),
    )
}

private fun impedanceChange(before: OpenNec.Point, after: OpenNec.Point) =
    NecImpedanceDelta(after.resistance - before.resistance, after.reactance - before.reactance)
