package local.antenna.calculator.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class YagiLayoutTest {
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
}
