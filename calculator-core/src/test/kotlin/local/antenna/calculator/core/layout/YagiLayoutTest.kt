package local.antenna.calculator.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class YagiLayoutTest {
    @Test
    fun keepsPublishedPositionAndDifferentLengthsForEachPlane() {
        val sat = YagiDesign.GTV_SAT
        assertEquals(514.0, sat.verticalElements[1].positionMm - sat.elements[1].positionMm, 0.0)
        assertEquals(2924.5, checkNotNull(calculateYagiCutList(sat, 0.0, null, null).boomMm), 0.0)

        val through = calculateYagiCutList(YagiDesign.GTV_XPOL_THROUGH4, 0.0, null, null)
        assertEquals(3258.0, checkNotNull(through.boomMm), 0.0)
        assertEquals(866.83, checkNotNull(through.blanksMm.last()), 0.0)
        assertEquals(866.33, checkNotNull(through.verticalBlanksMm.last()), 0.0)
        assertNull(through.verticalBlanksMm[1])
    }

    @Test
    fun markAndCutPublishedR3udkGeometry() {
        assertEquals(listOf(0.0, 211.0, 389.0, 868.0), R3udkYagi.elements.map { it.positionMm })
        assertEquals(listOf(1016.0, 990.0, 955.0, 929.0), R3udkYagi.elements.map { it.overallMm })
        val cut = calculateR3udkCutList(5.0, 30.0, 40.0)
        assertEquals(868.0, cut.spanMm, 0.0)
        assertEquals(938.0, checkNotNull(cut.boomMm), 0.0)
        assertEquals(listOf(1026.0, 995.0, 965.0, 939.0), cut.blanksMm)
    }

    @Test
    fun missingMarginKeepsSpanAndZeroMarginIsExplicit() {
        val cut = calculateR3udkCutList(0.0, 30.0, null)
        assertEquals(868.0, cut.spanMm, 0.0)
        assertNull(cut.boomMm)
        assertEquals(868.0, checkNotNull(calculateR3udkCutList(0.0, 0.0, 0.0).boomMm), 0.0)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { calculateR3udkCutList(-1.0, null, null) }
        assertThrows(IllegalArgumentException::class.java) { calculateR3udkCutList(0.0, 1001.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { calculateR3udkCutList(0.0, 0.0, Double.NaN) }
    }
    @Test
    fun publishedProfilesKeepUnknownBlanksAndUseConfirmedLengths() {
        val expectedSpans = listOf(868.0, 1485.0, 470.0, 590.0, 630.0, 392.0, 2358.0)
        val expectedCounts = listOf(4, 5, 3, 5, 5, 4, 6)
        val designs = YagiDesign.entries
        assertEquals(designs.size, designs.map(YagiDesign::model).distinct().size)
        assertEquals(expectedCounts, designs.take(expectedCounts.size).map { it.elements.size })
        assertEquals(expectedSpans, designs.take(expectedSpans.size).map { calculateYagiCutList(it, 0.0, null, null).spanMm })
        val datv = calculateYagiCutList(YagiDesign.GTV_DATV_THROUGH, 0.0, 40.0, 40.0)
        assertEquals(2400.0, checkNotNull(datv.boomMm), 0.0)
        assertEquals(993.74, checkNotNull(datv.blanksMm.first()), 0.0)
        assertNull(datv.blanksMm[1])
        val ybn = calculateYagiCutList(YagiDesign.YBN, 5.0, 30.0, 40.0)
        assertEquals(1555.0, checkNotNull(ybn.boomMm), 0.0)
        assertEquals(1042.9, checkNotNull(ybn.blanksMm.first()), 0.0)
        assertNull(ybn.blanksMm[1])
        assertEquals(listOf(null, null, null), calculateYagiCutList(YagiDesign.DK2, 5.0, null, null).blanksMm)

        val gtv = calculateYagiCutList(YagiDesign.GTV, 5.0, 40.0, 40.0)
        assertEquals(2438.0, checkNotNull(gtv.boomMm), 0.0)
        assertNull(gtv.blanksMm[1])
        assertEquals(856.83, checkNotNull(gtv.blanksMm.last()), 0.000001)
        assertEquals(listOf("r2m", "r70", "de", "os"), YagiDesign.DUO.elements.map(YagiElement::id))
        assertEquals(22.0, YagiDesign.DUO.elements[3].positionMm - YagiDesign.DUO.elements[2].positionMm, 0.0)
        assertEquals(listOf(120.0, 115.0, 141.0), YagiMatchingCable.entries.map(YagiMatchingCable::sectionMm))
    }
}
