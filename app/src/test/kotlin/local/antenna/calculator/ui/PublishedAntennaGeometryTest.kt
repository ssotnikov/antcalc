package local.antenna.calculator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedAntennaGeometryTest {
    @Test fun satelliteCoupledElementUsesTheSameScaleAsTheRectangle() {
        val profile = moxonProfiles.single { it.hybrid }
        val rods = checkNotNull(moxonPublishedVector(profile)).rods
        val coupled = rods.single { it.material == 2 }
        val reflector = rods.first { it.first.x < 0 && it.last.x > 0 && it.first.z == it.last.z }
        val scale = reflector.last.x - reflector.first.x
        assertEquals(127f, (reflector.first.z - coupled.first.z) * 700f / scale, 1e-4f)
        assertEquals(334f, (coupled.last.x - coupled.first.x) * 700f / scale, 1e-4f)
        assertTrue(coupled.first.z > rods.first().first.z)
    }

    @Test fun stockRetainsBothTubeSizesAndCutQuantities() {
        val baker = moxonProfiles.single { it.view == MoxonView.STEPPED_H }.stock
        assertEquals(listOf("7/8″", "1″"), baker.takeLast(2).map { it.name })
        assertEquals(listOf(34.0, 34.0), baker.takeLast(2).map { it.inches })
        val qst = moxonProfiles.single { it.view == MoxonView.PVC_FRAME }.stock
        assertEquals(listOf(4, 1, 1, 1, 4), qst.map { it.quantity })
    }

    @Test fun openRibbonAndSlimJimHaveDifferentTopConnections() {
        fun PublishedVerticalAntenna.hasTopBridge() = wireSections().any { (a, b) -> a.y == heightMm && b.y == heightMm }
        assertFalse(ok1ikeVertical.hasTopBridge())
        assertTrue(sv1jrfVertical.hasTopBridge())
    }
}
