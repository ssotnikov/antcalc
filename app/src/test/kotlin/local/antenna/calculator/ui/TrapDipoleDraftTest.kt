package local.antenna.calculator.ui

import local.antenna.calculator.core.TrapDipole.Band
import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.core.trimming.AntennaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrapDipoleDraftTest {
    @Test fun onlyExplicitFrequencyTransfersAndOtherValuesRoundTrip() {
        val draft = TrapDraft("50", listOf("", "7,155", ""), listOf("1,2", "", "", "2", "", ""))
        val snapshot = requireNotNull(draft.snapshot(Profile.AA4LR, Band.M40))
        val (profile, band, restored) = requireNotNull(TrapDraft.restore(snapshot))
        assertEquals(Profile.AA4LR, profile)
        assertEquals(Band.M40, band)
        assertEquals(snapshot, restored.snapshot(profile, band))
        val pending = PendingProjectCalculation(snapshot)
        assertEquals(7_155_000L, pending.newProject(AntennaType.TRAP_DIPOLE, "Trap").target?.hertz)
        assertEquals(snapshot, pending.record(AntennaType.TRAP_DIPOLE, "Trap").calculation)
        assertNull(PendingProjectCalculation(requireNotNull(draft.snapshot(Profile.AA4LR, Band.M20))).target)
        assertNull(TrapDraft.restore(snapshot.copy(revision = 2)))
    }

    @Test fun explicitZeroAllowanceWorksAndConflictingStripLengthsBlockTransfer() {
        val draft = TrapDraft("0", dimensions = listOf("1", "0.4", "0.6", "", "", ""))
        assertEquals(0.0, draft.allowanceMm)
        assertNull(draft.snapshot(Profile.R2AUK, Band.M20))
        val partial = draft.copy(dimensions = listOf("1", "0.4", "", "", "", ""))
        val snapshot = requireNotNull(partial.snapshot(Profile.R2AUK, Band.M20))
        assertEquals(listOf("trap_user_0_0", "trap_user_0_1"), snapshot.inputs.filter { it.key.startsWith("trap_user_") }.map { it.key })
    }
}
