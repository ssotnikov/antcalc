package local.antenna.calculator.core

import org.junit.Assert.*
import org.junit.Test

class CantennaTest {
    @Test fun publishedEquationReferenceCase() {
        val band = Cantenna.Band(2_412e6, 2_462e6)
        val result = Cantenna.analyze(Cantenna.Can(.085, .170, .001, .006), 2_437e6, band)
        assertEquals(2067.0, result.te11Hz / 1e6, .1)
        assertEquals(2699.8, result.tm01Hz / 1e6, .1)
        assertEquals(.0308, result.probe, .0001)
        assertEquals(.0248, result.addedProbe!!, .0001)
        assertEquals(.0581, result.offset!!, .0001)
        assertEquals(.1742, result.suggestedDepth!!, .0001)
        assertEquals(.1119, result.clearance!!, .0001)
        assertEquals(.0564, result.toleranceOffsets!!.start, .0001)
        assertEquals(.0599, result.toleranceOffsets.endInclusive, .0001)
        assertTrue(result.bandFits)
        val interval = Cantenna.diameterRange(band)!!
        assertEquals(.07284, interval.first, .000005)
        assertEquals(.09321, interval.second, .000005)
        assertNull(Cantenna.diameterRange(Cantenna.Band(1e9, 2e9)))
    }

    @Test fun cutoffBoundariesAndPhysicalLimits() {
        val can = Cantenna.Can(diameter = .085, depth = .01, tolerance = .010, contact = .05)
        val baseline = Cantenna.analyze(can, 2_437e6)
        val atLower = Cantenna.analyze(can, baseline.te11Hz)
        assertEquals(Cantenna.Mode.BELOW_CUTOFF, atLower.mode)
        assertNull(atLower.offset)
        val aboveLower = Cantenna.analyze(can, Math.nextUp(baseline.te11Hz))
        assertTrue(aboveLower.guideWavelength!!.isFinite())
        assertNull(aboveLower.toleranceOffsets)
        val upper = Cantenna.analyze(can, baseline.tm01Hz)
        assertEquals(Cantenna.Mode.HIGHER_MODES, upper.mode)
        assertNotNull(upper.guideWavelength)
        assertTrue(baseline.clearance!! < 0)
        assertNull(baseline.addedProbe)
    }

    @Test fun ringTableAndSheetDevelopment() {
        assertEquals(.106, Cantenna.ringPosition(2, false)!!, 1e-12)
        assertEquals(.132, Cantenna.ringPosition(4, true)!!, 1e-12)
        assertNull(Cantenna.ringPosition(5, true))
        val sector = Cantenna.flare(.080, .120, .060)!!
        assertEquals(.12649, sector.innerRadius, .000005)
        assertEquals(.18974, sector.outerRadius, .000005)
        assertEquals(113.84, sector.angle, .005)
        assertNull(Cantenna.flare(.085, .085, .05))
        assertNull(Cantenna.flare(.085, .145, 0.0))
    }
}
