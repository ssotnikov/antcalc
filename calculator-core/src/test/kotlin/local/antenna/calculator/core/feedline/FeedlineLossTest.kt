package local.antenna.calculator.core.feedline

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class FeedlineLossTest {
    @Test fun metricAndImperialInputsGiveTheSameResult() {
        val length = BigDecimal("10")
        val input = BigDecimal("100")
        val metric = calculateFeedlineLoss(length, BigDecimal("12.8"), AttenuationUnit.PER_100_METRES, input)
        val imperial = calculateFeedlineLoss(length, BigDecimal("3.90144"), AttenuationUnit.PER_100_FEET, input)
        assertEquals(metric, imperial)
        assertEquals(1.28, metric.decibels, 1e-9)
        assertEquals(74.4731973906, metric.outputWatts!!, 1e-9)
        assertEquals(25.5268026094, metric.lostWatts!!, 1e-9)
    }

    @Test fun acceptsZeroAndKeepsPowerOptional() {
        val zero = calculateFeedlineLoss(BigDecimal.ZERO, BigDecimal.TEN, AttenuationUnit.PER_100_METRES, BigDecimal.TEN)
        assertEquals(1.0, zero.transmittedFraction, 0.0)
        assertEquals(10.0, zero.outputWatts!!, 0.0)
        assertEquals(0.0, zero.lostWatts!!, 0.0)
        val optional = calculateFeedlineLoss(BigDecimal.TEN, BigDecimal.ZERO, AttenuationUnit.PER_100_METRES)
        assertEquals(1.0, optional.transmittedFraction, 0.0)
        assertNull(optional.outputWatts)
        assertNull(optional.lostWatts)
    }

    @Test fun boundsAndVerySmallLoss() {
        val small = calculateFeedlineLoss(BigDecimal.ONE, BigDecimal("1e-15"), AttenuationUnit.PER_100_METRES, BigDecimal.ONE)
        assertTrue(small.lostWatts!! > 0)
        assertThrows(IllegalArgumentException::class.java) {
            calculateFeedlineLoss(BigDecimal("10001"), BigDecimal.ONE, AttenuationUnit.PER_100_METRES)
        }
    }
}
