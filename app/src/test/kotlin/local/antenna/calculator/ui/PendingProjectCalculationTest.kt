package local.antenna.calculator.ui

import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingProjectCalculationTest {
    @Test fun returnsToTheSourceOfEachCalculation() {
        val routes = listOf(
            "sizing" to Page.CALCULATOR,
            "trimming" to Page.CALCULATOR,
            "inverted_v" to Page.INVERTED_V,
            "ground_plane" to Page.GROUND_PLANE,
            "j_pole" to Page.J_POLE,
            Ok1ikeJPoleModel to Page.J_POLE,
            "full_wave_loop" to Page.FULL_WAVE_LOOP,
            "moxon_calculated" to Page.MOXON,
        ) +
            SlimJimProfile.entries.map { it.model to Page.SLIM_JIM } +
            moxonProfiles.map { it.id to Page.MOXON } +
            YagiProfile.entries.map { it.design.model to Page.YAGI_CALCULATOR }

        routes.forEach { (model, page) ->
            val snapshot = CalculationSnapshot(
                model, revision = 1,
                inputs = listOf(SnapshotValue("frequency", 145_000_000.0, SnapshotUnit.HERTZ)),
                outputs = listOf(SnapshotValue("length", 1.0, SnapshotUnit.METERS)),
            )
            assertEquals(model, page, PendingProjectCalculation(snapshot).returnPage)
        }
    }
}
