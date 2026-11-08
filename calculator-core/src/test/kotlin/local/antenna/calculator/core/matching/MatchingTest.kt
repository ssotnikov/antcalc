package local.antenna.calculator.core.matching

import org.junit.Assert.*
import org.junit.Test

class MatchingTest {
    private fun finite(value: MatchingMeasure) = (value as MatchingMeasure.Finite).value

    @Test fun swrTwoAndRoundTrips() {
        val r = matchingFromSwr(2.0)
        assertEquals(1.0 / 3, r.magnitude, 1e-12)
        assertEquals(9.542425094393248, finite(r.returnLoss), 1e-9)
        assertEquals(0.511525224473813, finite(r.mismatchLoss), 1e-9)
        assertEquals(1.0 / 9, r.reflectedFraction, 1e-12)
        for (s in listOf(1.000001, 1.5, 2.0, 10.0, 1e6)) {
            val first = matchingFromSwr(s)
            assertEquals(s, finite(matchingFromReturnLoss(finite(first.returnLoss)).swr), s * 1e-9)
        }
    }
    @Test fun perfectMatchAndTotalReflection() {
        val matched = matchingFromMagnitude(0.0)
        assertEquals(1.0, finite(matched.swr), 0.0)
        assertEquals(MatchingMeasure.Infinite, matched.returnLoss)
        assertEquals(0.0, finite(matched.mismatchLoss), 0.0)
        val reflected = matchingFromMagnitude(1.0)
        assertEquals(MatchingMeasure.Infinite, reflected.swr)
        assertEquals(MatchingMeasure.Infinite, reflected.mismatchLoss)
        assertEquals(0.0, finite(reflected.returnLoss), 0.0)
    }
    @Test fun complexLoadsAndPhase() {
        val r = matchingFromImpedance(50.0, 50.0, 50.0)
        assertEquals(0.2, r.complex!!.real, 1e-12)
        assertEquals(0.4, r.complex!!.imaginary, 1e-12)
        assertEquals(20.0, 100 * r.reflectedFraction, 1e-12)
        assertEquals(-r.complex!!.phaseDegrees!!, matchingFromImpedance(50.0, -50.0, 50.0).complex!!.phaseDegrees!!, 1e-12)
        assertNull(matchingFromImpedance(50.0, 0.0, 50.0).complex!!.phaseDegrees)
        assertEquals(MatchingMeasure.Infinite, matchingFromImpedance(0.0, -50.0, 50.0).swr)
    }
    @Test fun extremePassiveMismatchDoesNotBecomeInfiniteThroughCancellation() {
        val r = matchingFromImpedance(1e-25, 1e6, 1000.0)
        assertTrue(r.swr is MatchingMeasure.Finite)
        assertTrue(r.mismatchLoss is MatchingMeasure.Finite)
        assertTrue(finite(r.swr) > 1e30)
    }
    @Test fun boundedSeparateNumericParser() {
        assertEquals(-50.5, parseMatchingNumber(" -50,5 ", MatchingField.REACTANCE).value!!, 0.0)
        assertEquals(0.0, parseMatchingNumber("0", MatchingField.RESISTANCE).value!!, 0.0)
        for (text in listOf("NaN", "Infinity", "1e3", "1 000", "1,2.3", "1".repeat(33)))
            assertEquals(MatchingError.INVALID, parseMatchingNumber(text, MatchingField.REACTANCE).error)
        assertEquals(MatchingError.INCOMPLETE, parseMatchingNumber("-", MatchingField.REACTANCE).error)
        assertEquals(MatchingError.INCOMPLETE, parseMatchingNumber("2,", MatchingField.SWR).error)
        assertEquals(MatchingError.RANGE, parseMatchingNumber("1.0000000000000000000001", MatchingField.GAMMA).error)
        assertEquals(MatchingError.INVALID, parseMatchingNumber("-1", MatchingField.RESISTANCE).error)
        assertEquals(MatchingError.RANGE, parseMatchingNumber("0", MatchingField.REFERENCE).error)
    }
    @Test fun invalidDirectInputsAreRejected() {
        listOf<() -> Any>({ matchingFromSwr(0.0) }, { matchingFromMagnitude(Double.NaN) },
            { matchingFromMagnitude(1.01) }, { matchingFromReturnLoss(-1.0) },
            { matchingFromImpedance(-1.0, 0.0, 50.0) }).forEach {
            try { it(); fail("Expected invalid input") } catch (_: IllegalArgumentException) { }
        }
    }
}
