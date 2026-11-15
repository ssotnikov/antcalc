package local.antenna.calculator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class SlimJimTest : CalculatorUiFixture() {
    @Test fun publishedUhfDesignKeepsTheAuthorsDimensionsAndFrequency() {
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SlimJimScreen(onSave = { pending = PendingProjectCalculation(it) }, onMeasurements = null, onCopied = {})
            }
        }
        compose.onNodeWithTag("slim-jim-band-SV1JRF_70CM").performScrollTo().performClick()
        val diagram = compose.onNodeWithTag("slim-jim-diagram-SV1JRF_70CM").performScrollTo().assertIsDisplayed()
        val pixels = diagram.captureToImage().toPixelMap()
        val feedX = (.33f * pixels.width).roundToInt()
        val feedY = ((.86f - .76f * 17f / 503f) * pixels.height).roundToInt()
        assertEquals(Color(0xFFF7C879), pixels[feedX, feedY])
        compose.onNodeWithTag("slim-jim-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(436_000_000L, pending.newProject(AntennaType.SLIM_JIM, "70 cm").target?.hertz)
            assertEquals(listOf(.503, .331, .166, .017, .007, .015), pending.snapshot.outputs.map { it.value })
        }
    }

    @Test fun ribbonJPoleKeepsTheGapCoordinatesAndCanReturnToTheCalculator() {
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme { JPoleScreen(onSave = { pending = PendingProjectCalculation(it) }, onOpenCable = {}, onCopied = {}) }
        }
        compose.onNodeWithTag("jpole-profile-true").performScrollTo().performClick()
        compose.onNodeWithTag("published-vertical-diagram").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("jpole-save").performScrollTo().performClick()
        compose.runOnIdle {
            val project = pending.newProject(AntennaType.J_POLE, "Ribbon")
            assertEquals(435_000_000L, project.target?.hertz)
            assertEquals(472_500_000L, project.initialLength?.nanometers)
            assertEquals(Page.J_POLE, pending.returnPage)
            val rows = pending.snapshot.outputs.associate { it.key to it.value }
            assertEquals(.1395, rows.getValue("slim_jim_gap_start"), 1e-12)
            assertEquals(.1445, rows.getValue("slim_jim_gap_end"), 1e-12)
        }
        compose.onNodeWithTag("jpole-profile-false").performScrollTo().performClick()
        compose.onNodeWithTag("jpole-frequency").assertExists()
    }

    @Test fun dimensionsAndReferencesAreReachableInBothLanguagesAtLargeFont() {
        start(fontScale = 2f)
        for (language in AppLanguage.entries) {
            val russian = language == AppLanguage.RUSSIAN
            compose.runOnIdle {
                val settings = model.state.value.draft.settings
                val theme = if (russian) AppTheme.DARK else AppTheme.LIGHT
                model.onEvent(CalculatorEvent.Settings(settings.copy(language = language, theme = theme)))
            }
            compose.onNodeWithTag("nav-TOOLS").performClick()
            compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
            compose.onNodeWithTag("open-slim-jim").performScrollTo().performClick()
            compose.onNodeWithText(if (russian) "просвету E = 25 мм" else "25 mm gap (E)", substring = true)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("slim-jim-copy").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("menu").performClick()
            compose.onNodeWithTag("open-references").performClick()
            compose.onNodeWithTag("source-sj-10m-en").performScrollTo().assertIsDisplayed()
            val russianSource = compose.onNodeWithTag("source-sj-10m-ru")
            if (russian) russianSource.assertExists() else russianSource.assertDoesNotExist()
            back()
            back()
        }
    }

    @Test fun opensAtLargeFontAndScrollsToTop() {
        start(fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-slim-jim").performScrollTo().performClick()
        compose.onNodeWithTag("slim-jim-copy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("screen-title").performClick()
        compose.onNodeWithTag("slim-jim-hero").assertIsDisplayed()
    }

    @Test fun eachBandShowsItsDiagramAndTheMatchingConstructionArtwork() {
        compose.setContent {
            MaterialTheme {
                SlimJimScreen(onSave = null, onMeasurements = null, onCopied = {})
            }
        }
        assertEquals(5, SlimJimProfile.entries.map { it.illustration }.distinct().size)
        for (profile in SlimJimProfile.entries) {
            compose.onNodeWithTag("slim-jim-band-$profile").performScrollTo().performClick()
            compose.onNodeWithTag("slim-jim-hero").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("slim-jim-diagram-$profile").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun savedProjectKeepsGeometryAndDoesNotInventMeasurements() {
        var pending: PendingProjectCalculation? = null
        compose.setContent {
            MaterialTheme {
                SlimJimScreen(onSave = { pending = PendingProjectCalculation(it) }, onMeasurements = null, onCopied = {})
            }
        }
        compose.onNodeWithTag("slim-jim-save").performScrollTo().performClick()
        compose.runOnIdle {
            val calculation = checkNotNull(pending)
            val type = AntennaType.SLIM_JIM
            val record = calculation.record(type, "Slim Jim")
            val project = calculation.newProject(type, "Wire antenna").copy(records = listOf(record))
            val restored = JournalJson.decodeTransfer(JournalJson.encodeTransfer(project).inputStream())
            assertEquals(project, restored)
            assertEquals(145_000_000L, restored.target?.hertz)
            assertNull(restored.initialLength)
            val dimensions = restored.records.single().calculation!!.outputs.associate { it.key to it.value }
            assertEquals(0.973, dimensions.getValue("slim_jim_return"), 1e-9)
            assertEquals(3.0, dimensions.getValue("slim_jim_wire_stock"), 1e-9)
        }
    }

    @Test fun selectedBandUsesItsOwnDimensionsAndSurvivesProjectExport() {
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme {
                SlimJimScreen(onSave = { pending = PendingProjectCalculation(it) }, onMeasurements = null, onCopied = {})
            }
        }
        compose.onNodeWithTag("slim-jim-band-G0KYA_10M").performScrollTo().performClick()
        compose.onNodeWithTag("slim-jim-save").performScrollTo().performClick()
        compose.runOnIdle {
            val type = AntennaType.SLIM_JIM
            val project = pending.newProject(type, "10 m Slim Jim")
                .copy(records = listOf(pending.record(type, "Starting dimensions")))
            val restored = JournalJson.decodeTransfer(JournalJson.encodeTransfer(project).inputStream())
            assertEquals(28_500_000L, restored.target?.hertz)
            val snapshot = checkNotNull(restored.records.single().calculation)
            assertEquals("slim_jim_g0kya_10m", snapshot.model)
            assertEquals(7.48, snapshot.outputs.single { it.key == "slim_jim_line_stock" }.value, 1e-9)
            assertNull(restored.initialLength)
        }
    }
}
