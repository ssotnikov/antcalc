package local.antenna.calculator.ui

import local.antenna.calculator.core.trimming.AntennaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaloCbDraftTest {
    @Test fun savesTargetAndRoundTripsTheInputWithoutInventingGammaDimensions() {
        val draft = HaloDraft(HaloVariant.SQUARE, "27,405", "1,02", "200",
            listOf("12", "5", "", "30", "", "95,5"))
        val snapshot = requireNotNull(draft.snapshot())
        val pending = PendingProjectCalculation(snapshot)
        val project = pending.newProject(AntennaType.HALO_CB, "Halo CB")
        assertEquals(27_405_000L, project.target?.hertz)
        assertNull(project.initialLength)
        assertEquals(snapshot, HaloDraft.fromSnapshot(snapshot)?.snapshot())
        assertEquals(snapshot, pending.record(AntennaType.HALO_CB, "Dimensions").calculation)
        assertEquals("", HaloDraft.fromSnapshot(snapshot)?.gamma?.get(HaloGammaField.TAP.ordinal))
    }

    @Test fun refusesIncompleteGeometryAndOverlappingGammaParts() {
        val draft = HaloDraft(gamma = listOf("20", "10", "120", "14", "121", "1000,1"))
        assertEquals(setOf(HaloGammaField.SPACING, HaloGammaField.TAP, HaloGammaField.CAPACITANCE), draft.gammaErrors())
        assertNull(draft.snapshot())
        assertNull(HaloDraft(gap = "").snapshot())
        assertNull(HaloDraft(variant = HaloVariant.CSQ11).snapshot())
    }
}
