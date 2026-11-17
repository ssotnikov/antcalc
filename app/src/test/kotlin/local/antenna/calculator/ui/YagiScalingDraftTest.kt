package local.antenna.calculator.ui

import local.antenna.calculator.core.layout.YagiScaling
import local.antenna.calculator.core.units.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YagiScalingDraftTest {
    @Test fun missingDataOnlyIncludesFieldsRequiredByTheSelectedMode() {
        val frequency = YagiScalingDraft.example().copy(targetDiameter = "")
        assertFalse(frequency.hasMissingRequiredData())
        assertTrue(frequency.copy(target = "").hasMissingRequiredData())

        val diameter = frequency.copy(mode = YagiScaling.Mode.DIAMETER, target = "")
        assertTrue(diameter.hasMissingRequiredData())
        val individualDiameters = diameter.rows.mapIndexed { index, row ->
            row.copy(target = if (index == 1) "" else "6")
        }
        assertTrue(diameter.copy(rows = individualDiameters).hasMissingRequiredData())
        val split = diameter.copy(rows = individualDiameters, gap = "10")
        assertFalse(split.hasMissingRequiredData())
        assertFalse(split.copy(source = "0").hasMissingRequiredData())
        assertNull(split.copy(source = "0").result())
    }

    @Test fun frequenciesAndDiameterNeedElementLengthsAndSpacing() {
        val draft = YagiScalingDraft(source = "145", target = "152", diameter = "6")
        assertNull(draft.result())
        val reflector = ScalingRow("0", "1040")
        assertNull(draft.copy(rows = listOf(reflector, ScalingRow("", "970"))).result())

        val complete = draft.copy(rows = listOf(reflector, ScalingRow("215", "970")))
        val result = requireNotNull(complete.result())
        val factor = 145.0 / 152.0
        assertEquals(1.040 * factor, result.elements[0].length, 1e-12)
        assertEquals(0.970 * factor, result.elements[1].length, 1e-12)
        assertEquals(0.215 * factor, result.elements[1].position, 1e-12)
        assertEquals(0.006 * factor, result.elements[1].diameter, 1e-12)
    }

    @Test fun savedCombinedCalculationRestoresEffectiveDiametersAndGeometry() {
        val example = YagiScalingDraft.example()
        val draft = example.copy(mode = YagiScaling.Mode.BOTH, gap = "14,5",
            rows = example.rows.mapIndexed { index, row -> if (index == 2) row.copy(diameter = "10", target = "7") else row })
            .inUnit(LengthUnit.CENTIMETERS)
        val result = requireNotNull(draft.result())
        val snapshot = draft.snapshot(result)
        val reopened = requireNotNull(YagiScalingDraft.restore(snapshot))

        assertEquals(draft.mode, reopened.mode)
        assertEquals(draft.unit, reopened.unit)
        assertEquals(draft.geometry, reopened.geometry)
        assertEquals(draft.sourceHz, reopened.sourceHz)
        assertEquals(draft.targetHz, reopened.targetHz)
        assertEquals(draft.enteredGap!!, reopened.enteredGap!!, 1e-12)
        assertEquals(0.007, result.elements[2].diameter, 1e-12)
        assertEquals(0.970 * result.factor, result.elements[1].length, 1e-12)
        assertEquals(0.008 * result.factor, result.elements[1].diameter, 1e-12)
        assertEquals(0.0145 * result.factor, result.feedGap!!, 1e-12)
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

    @Test fun necSnapshotRetainsOriginalFormulaAndAppliedGeometry() {
        val draft = YagiScalingDraft.example().copy(mode = YagiScaling.Mode.BOTH)
        val analytic = checkNotNull(draft.result())
        val lengths = analytic.elements.map { it.length * 1.02 }
        val saved = draft.snapshot(analytic, lengths)
        assertEquals(2, saved.revision)
        assertEquals(20401, saved.appliedNecVersion())
        val reopened = checkNotNull(YagiScalingDraft.restore(saved))
        val restored = checkNotNull(reopened.result())
        assertEquals(lengths, saved.appliedNecLengths(restored))
        analytic.elements.zip(restored.elements).forEach { (expected, actual) ->
            assertEquals(expected.length, actual.length, 1e-12)
            assertEquals(expected.position, actual.position, 1e-12)
            assertEquals(expected.diameter, actual.diameter, 1e-12)
        }
        val invalid = draft.snapshot(analytic, analytic.elements.map { it.length * 1.1 })
        assertNull(YagiScalingDraft.restore(invalid))
    }
}
