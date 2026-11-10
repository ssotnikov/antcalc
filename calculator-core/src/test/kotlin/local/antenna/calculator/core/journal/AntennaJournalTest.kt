package local.antenna.calculator.core.journal

import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import org.junit.Assert.*
import org.junit.Test

class AntennaJournalTest {
    private val before = JournalMeasurement(
        PhysicalLength.ofNanometers(1_000_000_000), Frequency.ofHertz(142_800_000),
        Frequency.ofHertz(145_000_000), 2.4, point = MeasurementPoint.FEED_POINT,
    )
    private val after = before.copy(
        length = PhysicalLength.ofNanometers(984_000_000), resonance = Frequency.ofHertz(145_000_000), swr = 1.3,
    )

    @Test fun comparableMeasurementsUseExactFrequencyAndLengthDeltas() {
        val result = compareMeasurements(before, after)
        assertEquals(2_200_000L, result.resonanceDeltaHz)
        assertEquals(-16_000_000L, result.lengthDeltaNm)
        assertEquals(-1.1, result.swrDelta!!, 1e-12)
        assertTrue(result.differences.isEmpty())
    }

    @Test fun movingTheAnalyzerOrChangingTestFrequencySuppressesSwrDelta() {
        val result = compareMeasurements(before, after.copy(
            point = MeasurementPoint.TRANSCEIVER, testFrequency = Frequency.ofHertz(146_000_000),
        ))
        assertNull(result.swrDelta)
        assertEquals(setOf(MeasurementDifference.DIFFERENT_POINT, MeasurementDifference.DIFFERENT_FREQUENCY), result.differences)
        assertEquals(2_200_000L, result.resonanceDeltaHz)
    }

    @Test fun unspecifiedMeasurementConditionsDoNotImplyComparableSwr() {
        val result = compareMeasurements(before.copy(testFrequency = null), after.copy(point = MeasurementPoint.UNSPECIFIED))
        assertNull(result.swrDelta)
        assertEquals(setOf(MeasurementDifference.UNKNOWN_POINT, MeasurementDifference.UNKNOWN_FREQUENCY), result.differences)
    }

    @Test fun measurementRejectsOutOfRangeValuesAndIncompleteImpedance() {
        assertThrows(IllegalArgumentException::class.java) { before.copy(swr = 0.99) }
        assertThrows(IllegalArgumentException::class.java) { before.copy(swr = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { before.copy(resistanceOhms = 50.0) }
        assertThrows(IllegalArgumentException::class.java) { JournalMeasurement() }
        assertEquals(-5.0, JournalMeasurement(resistanceOhms = 50.0, reactanceOhms = -5.0).reactanceOhms!!, 0.0)
    }
}
