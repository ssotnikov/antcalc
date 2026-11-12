package local.antenna.calculator.core.journal

import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import org.junit.Assert.*
import org.junit.Test

class JournalMixedComparisonTest {
    private val manual = JournalMeasurement(
        testFrequency = Frequency.ofHertz(145_000_000), swr = 1.3, resistanceOhms = 60.0, reactanceOhms = 5.0,
        point = MeasurementPoint.FEED_POINT, referenceOhms = 50.0,
    )
    private val sweep = S11Sweep(50.0, listOf(S11Point(145_000_000.0, 0.1, 0.0)))

    @Test fun exactFrequencyAndPlaneCompareCommonQuantities() {
        val result = compareManualToSweep(manual, sweep, MeasurementPoint.FEED_POINT)
        assertTrue(result.issues.isEmpty())
        assertEquals(1.1 / 0.9 - 1.3, result.swrDelta!!, 1e-12)
        assertEquals(50.0 * 1.1 / 0.9 - 60.0, result.resistanceDelta!!, 1e-12)
        assertEquals(-5.0, result.reactanceDelta!!, 1e-12)
    }

    @Test fun unknownOrDifferentReferencePreventsOnlySwrDifference() {
        for ((reference, issue) in listOf(
            null to SweepComparisonIssue.REFERENCE_UNKNOWN, 75.0 to SweepComparisonIssue.REFERENCE_MISMATCH,
        )) {
            val result = compareManualToSweep(manual.copy(referenceOhms = reference), sweep, MeasurementPoint.FEED_POINT)
            assertEquals(setOf(issue), result.issues)
            assertNull(result.swrDelta)
            assertNotNull(result.resistanceDelta)
        }
    }

    @Test fun nearestFrequencyIsShownWithoutDeltasAndResonanceIsNeverUsedAsTestFrequency() {
        val frequency = Frequency.ofHertz(145_000_001)
        val result = compareManualToSweep(manual.copy(testFrequency = frequency), sweep, MeasurementPoint.FEED_POINT)
        assertEquals(145_000_000.0, result.point!!.hertz, 0.0)
        assertNull(result.swrDelta)
        assertNull(result.resistanceDelta)
        assertEquals(setOf(SweepComparisonIssue.FREQUENCY_UNSAMPLED), result.issues)
        val missing = compareManualToSweep(manual.copy(testFrequency = null, resonance = frequency), sweep, MeasurementPoint.FEED_POINT)
        assertNull(missing.point)
        assertEquals(setOf(SweepComparisonIssue.FREQUENCY_UNKNOWN), missing.issues)
    }

    @Test fun aDifferentPlaneOrInvalidTracePointSuppressesDeltas() {
        for (plane in listOf(MeasurementPoint.TRANSCEIVER, MeasurementPoint.UNSPECIFIED)) {
            val result = compareManualToSweep(manual, sweep, plane)
            assertNull(result.swrDelta)
            assertNull(result.resistanceDelta)
            assertEquals(setOf(SweepComparisonIssue.PLANE_MISMATCH), result.issues)
        }
        val invalid = compareManualToSweep(manual, sweep.copy(points = listOf(sweep.points.single().copy(real = 1.1))),
            MeasurementPoint.FEED_POINT)
        assertNull(invalid.resistanceDelta)
        assertEquals(setOf(SweepComparisonIssue.INVALID_POINT), invalid.issues)
    }

    @Test fun sizingComparesPhysicalLengthOfTheMatchingAntenna() {
        val type = AntennaType.SYMMETRIC_DIPOLE
        val snapshot = CalculationSnapshot("sizing", 1,
            listOf(SnapshotValue("frequency", 145e6, SnapshotUnit.HERTZ)),
            listOf(SnapshotValue("dipole_total", 0.9821, SnapshotUnit.METERS)))
        val estimate = JournalRecord(UUID.randomUUID(), "Sizing", OffsetDateTime.now(), type, calculation = snapshot)
        val measured = JournalRecord(UUID.randomUUID(), "Measured", OffsetDateTime.now(), type,
            measurement = manual.copy(length = PhysicalLength.ofNanometers(980_000_000)))
        val result = compareSizingLength(estimate, measured)!!
        assertEquals(0.9821, result.estimatedMeters, 0.0)
        assertEquals(0.98, result.measuredMeters, 0.0)
        assertNull(compareSizingLength(estimate, measured.copy(antennaType = AntennaType.MONOPOLE)))
        assertNull(compareSizingLength(estimate, measured.copy(measurement = manual)))
    }
}
