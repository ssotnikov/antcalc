package local.antenna.calculator.nec

import local.antenna.calculator.core.layout.YagiScaling.Element
import org.junit.Assert.*
import org.junit.Test

class YagiSegmentationTest {
    private val dk7zb = listOf(Element(0.0, 1.010, .010), Element(.210, .920, .010))

    @Test fun publishedExampleAndUpperBandEdge() {
        val plan = OpenNec.segmentation(dk7zb, 146_000_000)
        assertEquals(listOf(11, 11), plan.counts)
        assertEquals(6, plan.counts[0] / 2 + 1)
        assertEquals(2.053373, plan.wavelength, 1e-6)
        assertEquals(listOf(21, 21), OpenNec.finerSegments(dk7zb, plan.counts))
        assertEquals(listOf(13, 13), OpenNec.finerSegments(dk7zb.map { it.copy(diameter = .03) }, plan.counts))
        assertEquals(listOf(15, 13), OpenNec.segmentCounts(dk7zb, 200_000_000))
    }

    @Test fun failuresIdentifyStepBudgetAndOverlappingElements() {
        val thick = listOf(Element(0.0, 1.0, .06))
        val step = OpenNec.segmentation(thick, 146_000_000)
        assertEquals(OpenNec.SegmentationIssue.STEP, step.issue)
        assertEquals(0, step.elementIndex)
        assertNull(OpenNec.finerSegments(listOf(Element(0.0, 1.0, .04)), listOf(11)))
        val many = (0 until 23).map { Element(it.toDouble(), 1.0, .01) }
        assertEquals(OpenNec.SegmentationIssue.LIMIT, OpenNec.segmentation(many, 146_000_000).issue)
        assertEquals(OpenNec.SegmentationIssue.POSITIONS, OpenNec.segmentation(
            listOf(dk7zb[0], dk7zb[1].copy(position = .005)), 146_000_000).issue)
    }
}
