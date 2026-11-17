package local.antenna.calculator.nec

import kotlinx.coroutines.runBlocking
import local.antenna.calculator.core.layout.YagiScaling.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NecLengthOptimizerTest {
    private val elements = listOf(Element(0.0, 1.04, .008), Element(.215, 1.0, .008), Element(.4, .92, .008))

    @Test fun improvesWorstPointAndPreservesPositionsAndDiameters() = runBlocking {
        val result = checkNotNull(optimizeNecLengths(elements, 1.2, ::evaluate))
        assertTrue(result.reachedGoal)
        assertEquals(1.02, result.best.elements[1].length, 1e-12)
        assertEquals(elements.map { it.position }, result.best.elements.map { it.position })
        assertEquals(elements.map { it.diameter }, result.best.elements.map { it.diameter })
        assertEquals(1.1, result.best.worstSwr, 1e-12)
    }

    @Test fun unreachableGoalUsesBestConsistentCandidateWithinBudget() = runBlocking {
        var attempts = 0
        val impossible = checkNotNull(optimizeNecLengths(elements, 1.01) {
            attempts++
            evaluate(it)
        })
        assertFalse(impossible.reachedGoal)
        assertTrue(attempts <= 25)
        assertEquals(1.1, impossible.best.worstSwr, 1e-12)
        val repeated = optimizeNecLengths(elements, 1.01, ::evaluate)
        assertEquals(impossible, repeated)
        assertNull(optimizeNecLengths(elements, 1.5) { null })
    }

    private fun evaluate(candidate: List<Element>): List<OpenNec.Point> {
        val error = abs(candidate[1].length - 1.02) * 30
        return listOf(
            OpenNec.Point(144_000_000, 50.0, 0.0, 1.0 + error, emptyList()),
            OpenNec.Point(146_000_000, 50.0, 0.0, 1.1 + error, emptyList()),
        )
    }
}
