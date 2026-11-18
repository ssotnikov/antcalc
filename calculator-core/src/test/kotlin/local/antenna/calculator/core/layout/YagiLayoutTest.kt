package local.antenna.calculator.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class YagiLayoutTest {
    @Test
    fun retainsArchivedModelIdsWithoutTheirCatalogGeometry() {
        val archived = YagiDesign.entries.filter { it.restricted && it !in setOf(YagiDesign.YBN, YagiDesign.GTV) }
        assertEquals(12, archived.size)
        assertEquals(5, archived.count { it.dualPlane })
        archived.forEach { assertEquals(false, it.available) }
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
        val designs = YagiDesign.entries
        assertEquals(designs.size, designs.map(YagiDesign::model).distinct().size)
        val publicDesigns = designs.filterNot { it.restricted }
        assertEquals(listOf(4, 3, 5, 5, 4), publicDesigns.map { it.elements.size })
        assertEquals(listOf(868.0, 470.0, 590.0, 630.0, 392.0),
            publicDesigns.map { calculateYagiCutList(it, 0.0, null, null).spanMm })
        assertEquals(listOf(null, null, null), calculateYagiCutList(YagiDesign.DK2, 5.0, null, null).blanksMm)

        assertEquals(listOf("r2m", "r70", "de", "os"), YagiDesign.DUO.elements.map(YagiElement::id))
        assertEquals(22.0, YagiDesign.DUO.elements[3].positionMm - YagiDesign.DUO.elements[2].positionMm, 0.0)
        assertEquals(listOf(120.0, 115.0, 141.0), YagiMatchingCable.entries.map(YagiMatchingCable::sectionMm))
    }
}
