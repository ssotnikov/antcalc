package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.SnapshotUnit
import local.antenna.calculator.core.journal.SnapshotValue
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.journal.ProjectsRepository
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectPickerSpacingTest : CalculatorUiFixture() {
    @Test fun summaryAndSearchRemainSeparatedWhileTyping() = exercise(AppTheme.DARK)

    @Test fun lightThemeKeepsClearanceWithLargerText() = exercise(AppTheme.LIGHT, fontScale = 1.3f)

    @Test fun measurementHeaderAndSearchRemainSeparated() = exercise(AppTheme.DARK, measurements = true)

    private fun exercise(theme: AppTheme, fontScale: Float = 1f, measurements: Boolean = false) {
        val pending = PendingProjectCalculation(CalculationSnapshot("cantenna", 1,
            listOf(SnapshotValue("frequency", 2_437_000_000.0, SnapshotUnit.HERTZ)),
            listOf(SnapshotValue("diameter", 0.085, SnapshotUnit.METERS))))
        val journal = JournalViewModel(ProjectsRepository(compose.activity.cacheDir.resolve("spacing-projects")))
        compose.setContent {
            LocalizedContent(AppLanguage.RUSSIAN) {
                CalculatorTheme(theme) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        ProjectPicker(journal, if (measurements) null else AntennaType.CANTENNA,
                            {}, pending.target?.hertz, {}, header = {
                                Box(Modifier.testTag("spacing-summary")) {
                                    if (measurements) Column {
                                        Text("VNA")
                                        Text("before.s1p")
                                        Text("after.s1p")
                                    } else TransferSummary(pending, AntennaType.CANTENNA)
                                }
                            })
                    }
                }
            }
        }
        fun assertGap() {
            val summary = compose.onNodeWithTag("spacing-summary").getUnclippedBoundsInRoot()
            val search = compose.onNodeWithTag("project-transfer-project-search").getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("Search must have at least 16 dp clearance below summary: $summary, $search",
                search.top >= summary.bottom + 16.dp)
        }
        assertGap()
        compose.onNodeWithTag("project-transfer-project-search").performClick().performTextInput("test")
        assertGap()
        compose.onNodeWithTag("journal-clear-search").performClick()
        assertGap()
    }
}
