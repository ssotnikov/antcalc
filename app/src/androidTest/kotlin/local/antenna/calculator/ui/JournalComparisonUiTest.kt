package local.antenna.calculator.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.core.journal.JournalMeasurement
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.journal.MeasurementPoint
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.journal.JournalComparisonScreen
import local.antenna.calculator.journal.JournalPresentation
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalComparisonUiTest : CalculatorUiFixture() {
    @Test fun russianDarkComparisonShowsSignedChangesAndCopiesThem() = checkComparison(false)
    @Test fun englishLightComparisonShowsUnchangedAndCopiesIt() = checkComparison(true)
    @Test fun largeTextKeepsStackedHeadingsTheSameSize() = checkComparison(true, 1.6f)

    private fun checkComparison(unchanged: Boolean, fontScale: Float = 1f) {
        val measurement = JournalMeasurement(
            resonance = Frequency.ofHertz(144_000_000),
            testFrequency = Frequency.ofHertz(145_000_000),
            swr = 3.0, point = MeasurementPoint.FEED_POINT,
        )
        val before = JournalRecord(UUID.randomUUID(), "A", OffsetDateTime.now(),
            AntennaType.SYMMETRIC_DIPOLE, note = "Before note", measurement = measurement)
        val after = before.copy(id = UUID.randomUUID(), title = "Follow-up measurement without Z₀", note = "After note", measurement = if (unchanged) measurement
            else measurement.copy(resonance = Frequency.ofHertz(145_000_000), swr = 2.0))
        var copied = ""
        compose.setContent {
            LocalizedContent(if (unchanged) AppLanguage.ENGLISH else AppLanguage.RUSSIAN) {
                CalculatorTheme(if (unchanged) AppTheme.LIGHT else AppTheme.DARK) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        JournalComparisonScreen("Test", before, after, JournalPresentation(LocalContext.current)) { copied = it }
                    }
                }
            }
        }
        val first = compose.onNodeWithTag("journal-before-heading").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("journal-after-heading").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, second.width, 1f)
        assertEquals(first.height, second.height, 1f)
        compose.onNodeWithText(after.title).assertExists()
        compose.onNodeWithText(before.note).assertExists()
        compose.onNodeWithText(after.note).assertExists()
        compose.onNodeWithText("− ${before.note}").assertDoesNotExist()
        compose.onNodeWithText("+ ${after.note}").assertDoesNotExist()
        if (unchanged) {
            compose.onAllNodesWithText("(unchanged)").assertCountEquals(2)
        } else {
            compose.onNodeWithText("(+1,000 МГц)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("(−1)").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("journal-copy-comparison").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(copied.contains(if (unchanged) "(unchanged)" else "(+1,000 МГц)"))
            if (!unchanged) assertTrue(copied.contains("(−1)"))
        }
    }
}
