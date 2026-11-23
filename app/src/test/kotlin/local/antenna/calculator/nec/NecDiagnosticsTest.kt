package local.antenna.calculator.nec

import local.antenna.calculator.core.matching.MatchingMeasure
import local.antenna.calculator.core.matching.matchingFromImpedance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NecDiagnosticsTest {
    private val frequencies = listOf(144_000_000L, 146_000_000L)

    private fun point(i: Int, swr: Double, r: Double = 50.0, x: Double = 0.0) =
        OpenNec.Point(frequencies[i], r, x, swr, emptyList())

    @Test fun worstFrequencyMovesAndAnExactTieUsesTheLowerFrequency() {
        assertEquals(frequencies[1], worstNecSwr(listOf(point(0, 1.2), point(1, 1.5))).frequencyHz)
        assertEquals(frequencies[0], worstNecSwr(listOf(point(1, 1.5), point(0, 1.5))).frequencyHz)
    }

    @Test fun usesComplexDifferenceWithinOneMatchedFrequency() {
        val coarse = listOf(point(0, 1.0), point(1, 1.0))
        val fine = listOf(point(0, 1.0, r = 55.0), point(1, 1.0, x = 6.0))
        assertEquals(NecImpedanceShift(6.0, frequencies[1]), maximumNecShift(coarse, fine, frequencies))
        assertNull(maximumNecShift(coarse, fine.reversed(), frequencies))
    }

    @Test fun meshDirectionUsesTheSeparateWorstPointsOfEachGeometry() {
        val base = listOf(point(0, 1.0), point(1, 1.5))
        val improvement = listOf(point(0, 1.3), point(1, 1.0))
        val degradation = listOf(point(0, 1.6), point(1, 1.0))
        val result = checkNotNull(necDirectionRange(base, improvement, base, degradation, frequencies))
        assertEquals(-0.2, result.low, 1e-12)
        assertEquals(0.1, result.high, 1e-12)
        assertTrue(result.indeterminate)
        assertTrue(checkNotNull(necDirectionRange(base, improvement, base, base, frequencies)).indeterminate)
        assertNull(necDirectionRange(base, improvement, base, emptyList(), frequencies))
    }

    @Test fun reportsCommonShiftAndIncrementErrorAtTheirOwnFrequencies() {
        val before = listOf(point(0, 1.3, 35.0, 1.0), point(1, 1.5, 30.0, 0.0))
        val after = listOf(point(0, 1.4, 37.0, 0.0), point(1, 1.2, 31.0, -2.0))
        val fineBefore = listOf(point(0, 1.4, 38.0, -3.0), point(1, 1.2, 30.0, 0.0))
        val fineAfter = listOf(point(0, 1.2, 40.0, -4.0), point(1, 1.6, 34.0, 2.0))
        val result = checkNotNull(necDirectionRange(before, after, fineBefore, fineAfter, frequencies))
        assertEquals(frequencies[1], result.coarse.before.frequencyHz)
        assertEquals(frequencies[0], result.coarse.after.frequencyHz)
        assertEquals(frequencies[0], result.fine.before.frequencyHz)
        assertEquals(frequencies[1], result.fine.after.frequencyHz)
        assertEquals(frequencies[0], result.largestCommonShift.frequencyHz)
        assertEquals(NecImpedanceDelta(3.0, -4.0), result.largestCommonShift.commonShift)
        assertEquals(NecImpedanceDelta(0.0, 0.0), result.largestCommonShift.residual)
        assertEquals(frequencies[1], result.largestResidual.frequencyHz)
        assertEquals(NecImpedanceDelta(1.0, -2.0), result.largestResidual.coarse)
        assertEquals(NecImpedanceDelta(4.0, 2.0), result.largestResidual.fine)
        assertEquals(NecImpedanceDelta(3.0, 4.0), result.largestResidual.residual)
        assertEquals(5.0, result.largestResidual.residual.ohms, 1e-12)
    }

    @Test fun directYagiSweepKeepsDirectionUndeterminedDespiteCloseIncrements() {
        val result = archivedDirection("near-997", "OpenNEC 2.4.2 h4", "NGSolve PML8")
        assertEquals(147_000_000L, result.coarse.before.frequencyHz)
        assertEquals(147_000_000L, result.coarse.after.frequencyHz)
        assertEquals(143_000_000L, result.fine.before.frequencyHz)
        assertEquals(143_000_000L, result.fine.after.frequencyHz)
        assertEquals(-0.002339238465255411, result.low, 1e-12)
        assertEquals(0.0025777756220262127, result.high, 1e-12)
        assertEquals(4.729448770478136, result.largestCommonShift.commonShift.ohms, 1e-12)
        assertEquals(147_000_000L, result.largestCommonShift.frequencyHz)
        assertEquals(0.008501366273302405, result.largestResidual.residual.ohms, 1e-12)
        assertEquals(145_906_250L, result.largestResidual.frequencyHz)
        assertTrue(result.indeterminate)
    }

    @Test fun directYagiSweepAgreesOnImprovementForTwoOpenNecMeshes() {
        val result = archivedDirection("gap-12", "OpenNEC h8", "OpenNEC h4")
        assertEquals(147_000_000L, result.coarse.before.frequencyHz)
        assertEquals(147_000_000L, result.fine.after.frequencyHz)
        assertEquals(-0.17089307721327707, result.low, 1e-12)
        assertEquals(-0.16985465054969717, result.high, 1e-12)
        assertFalse(result.indeterminate)
    }

    @Test fun displayedArchiveMatchesAllThreeReferenceSweeps() {
        val reference = archivedDirection("near-997", "OpenNEC 2.4.2 h8", "OpenNEC 2.4.2 h4")
        val pml = archivedDirection("near-997", "OpenNEC 2.4.2 h4", "NGSolve PML8")
        val expected = listOf(reference.coarse, reference.fine, pml.fine)
        val displayed = listOf(NecReferenceExamples.wire8, NecReferenceExamples.wire4, NecReferenceExamples.pml8)
        assertEquals(expected, displayed)
        assertEquals(-0.0022696096784320385, displayed[0].delta, 1e-12)
        assertEquals(-0.002339238465255411, displayed[1].delta, 1e-12)
        assertEquals(0.0025777756220262127, displayed[2].delta, 1e-12)

        val lower = archivedDirection("near-997", "OpenNEC 2.4.2 h8", "NGSolve PML8")
        assertTrue(lower.largestResidual.coarse.resistance > 0.0)
        assertTrue(lower.largestResidual.fine.reactance > 0.0)
        assertEquals(0.005035, lower.largestResidual.residual.ohms, 1e-6)
    }

    @Test fun archivedRadialRefinementPreservesSignedSwrDifferences() {
        val before = listOf(
            fromImpedance(143_000_000L, 29.78160969202366, 6.170185190589157),
            fromImpedance(147_000_000L, 29.2932281249687, -2.4800314497613263),
        )
        val after = listOf(
            fromImpedance(143_000_000L, 29.78015625554852, 6.169313328158862),
            fromImpedance(147_000_000L, 29.294433754377515, -2.4788028157271307),
        )
        val samples = NecReferenceExamples.radial
        assertEquals(before, samples.map { it.eightLayers })
        assertEquals(after, samples.map { it.twelveLayers })
        assertEquals(0.001694881023739839, samples[0].deltaImpedance, 1e-12)
        assertEquals(0.00007091935322312004, samples[0].deltaSwr, 1e-12)
        assertEquals(-0.0000765469694807841, samples[1].deltaSwr, 1e-12)
        val shift = checkNotNull(maximumNecShift(before, after, before.map { it.frequencyHz }))
        assertEquals(147_000_000L, shift.frequencyHz)
        assertEquals(0.0017213610491074633, shift.ohms, 1e-12)
        assertEquals(0.00007091935322312004, after[0].swr - before[0].swr, 1e-12)
        assertEquals(-0.0000765469694807841, after[1].swr - before[1].swr, 1e-12)
    }

    private fun archivedDirection(case: String, coarse: String, fine: String): NecDirectionRange {
        val rows = checkNotNull(javaClass.getResourceAsStream("/nec/yagi-paired-sweeps.csv"))
            .bufferedReader().useLines { lines ->
                lines.drop(1).map { it.split(',') }.filter { it[0] == case }.toList()
            }
        fun series(model: String, state: String) = rows.filter { it[1] == model && it[2] == state }.map {
            fromImpedance(it[3].toLong(), it[4].toDouble(), it[5].toDouble())
        }
        val before = series(coarse, "before")
        assertEquals(129, before.size)
        return checkNotNull(necDirectionRange(
            before, series(coarse, "after"), series(fine, "before"), series(fine, "after"),
            before.map { it.frequencyHz },
        ))
    }

    private fun fromImpedance(hertz: Long, r: Double, x: Double): OpenNec.Point {
        val swr = matchingFromImpedance(r, x, 50.0).swr as MatchingMeasure.Finite
        return OpenNec.Point(hertz, r, x, swr.value, emptyList())
    }
}
