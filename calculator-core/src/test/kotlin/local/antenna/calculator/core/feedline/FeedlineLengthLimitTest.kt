package local.antenna.calculator.core.feedline

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class FeedlineLengthLimitTest {
    private fun n(value: String) = BigDecimal(value)
    private fun length(loss: String, attenuation: String, connections: String = "0",
        unit: AttenuationUnit = AttenuationUnit.PER_100_METRES) =
        (maximumFeedlineLength(n(loss), n(attenuation), unit, n(connections)) as FeedlineLengthLimit.Finite).metres

    @Test fun publishedRadiolabPointsAndConnections() {
        assertEquals(47.61904761904762, length("3", "6.3").toDouble(), 1e-12)
        assertEquals(24.79338842975207, length("3", "12.1").toDouble(), 1e-12)
        assertEquals(16.48351648351648, length("3", "18.2").toDouble(), 1e-12)
        assertEquals(41.26984126984127, length("3", "6.3", "0.4").toDouble(), 1e-12)
        val safe = length("3", "6.3", "0.4")
        assertTrue(safe * n("6.3") / n("100") + n("0.4") <= n("3"))
    }

    @Test fun metricAndImperialUseTheirExactReferenceLengths() {
        assertEquals(0, length("3", "12.8", "0.4").compareTo(
            length("3", "3.90144", "0.4", AttenuationUnit.PER_100_FEET)))
    }

    @Test fun distinguishesNoPositiveLengthImpossibleAndUnbounded() {
        assertEquals(0, length("3", "10", "3").signum())
        assertEquals(FeedlineLengthLimit.Exceeded,
            maximumFeedlineLength(n("3"), n("10"), AttenuationUnit.PER_100_METRES, n("3.1")))
        for (allowed in listOf("0", "3")) assertEquals(FeedlineLengthLimit.Unbounded,
            maximumFeedlineLength(n(allowed), BigDecimal.ZERO, AttenuationUnit.PER_100_METRES, n(allowed)))
        assertEquals(FeedlineLengthLimit.Exceeded,
            maximumFeedlineLength(BigDecimal.ZERO, BigDecimal.ZERO, AttenuationUnit.PER_100_METRES, n("1")))
    }

    @Test fun verySmallPositiveAttenuationIsNotTreatedAsZero() {
        assertEquals(0, n("3e32").compareTo(length("3", "0.000000000000000000000000000001")))
        assertThrows(IllegalArgumentException::class.java) { length("-1", "10") }
        assertThrows(IllegalArgumentException::class.java) { length("3", "-1") }
        assertThrows(IllegalArgumentException::class.java) { length("1001", "1") }
        assertThrows(IllegalArgumentException::class.java) { length("3", "1", "1001") }
    }
    @Test fun totalLossRejectsNegativeValuesEvenBelowDoublePrecision() {
        assertThrows(IllegalArgumentException::class.java) {
            transmissionAtFeedlineLoss(n("-1e-1000"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            transmissionAtFeedlineLoss(n("1e1000"))
        }
    }

}
