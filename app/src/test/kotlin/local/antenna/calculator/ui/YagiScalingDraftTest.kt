package local.antenna.calculator.ui

import local.antenna.calculator.core.layout.YagiScaling
import local.antenna.calculator.core.units.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YagiScalingDraftTest {
    @Test fun savedCombinedCalculationRestoresEffectiveDiametersAndGeometry() {
        val draft = YagiScalingDraft.example().copy(mode = YagiScaling.Mode.BOTH, gap = "14,5")
        val result = requireNotNull(draft.result())
        val snapshot = draft.snapshot(result)
        val reopened = requireNotNull(YagiScalingDraft.restore(snapshot))

        assertEquals(draft.mode, reopened.mode)
        assertEquals(draft.enteredGap!!, reopened.enteredGap!!, 1e-12)
        val newResult = requireNotNull(reopened.result())
        assertEquals(result.factor, newResult.factor, 1e-12)
        result.elements.zip(newResult.elements).forEach { (before, after) ->
            assertEquals(before.length, after.length, 1e-12)
            assertEquals(before.position, after.position, 1e-12)
            assertEquals(before.diameter, after.diameter, 1e-12)
        }
    }

    @Test fun unitChangesRetainPhysicalGeometryAndBadFrequencyBlocksResult() {
        val draft = YagiScalingDraft.example()
        val millimetres = requireNotNull(draft.result())
        val metres = requireNotNull(draft.inUnit(LengthUnit.METERS).result())
        assertEquals(millimetres.elements[1].length, metres.elements[1].length, 1e-12)
        assertNull(draft.copy(target = "400,0000001").result())
        assertNull(draft.copy(target = "0").result())
    }
}
