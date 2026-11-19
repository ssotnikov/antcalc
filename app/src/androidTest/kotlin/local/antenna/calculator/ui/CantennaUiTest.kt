package local.antenna.calculator.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.R
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson
import local.antenna.calculator.measurements.BeforeAfterBandwidth
import local.antenna.calculator.measurements.MeasurementTrace
import local.antenna.calculator.measurements.SweepPlane
import local.antenna.calculator.state.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CantennaUiTest : CalculatorUiFixture() {
    @Test fun referenceTableStaysFixedWhileTheGuideCurveTracksTheCan() {
        compose.activity.getSharedPreferences("cantenna_calculator", 0).edit().clear().commit()
        compose.setContent {
            LocalizedContent(AppLanguage.ENGLISH) {
                MaterialTheme { CantennaScreen(null, null, {}) }
            }
        }
        fun panel(name: String) = compose.onNodeWithTag("cantenna-reference-$name").performScrollTo()
        panel("table").performClick()
        compose.onNodeWithText("752").assertExists()
        compose.onNodeWithTag("table-horizontal-scrollbar").performScrollTo().assertIsDisplayed()
        panel("curve").performClick()
        compose.onNodeWithTag("cantenna-guide-curve").performScrollTo().assertIsDisplayed()
        val point = compose.onNodeWithTag("cantenna-curve-point")
        point.assertTextContains("Selected D · 85 mm", substring = true)
        compose.onNodeWithTag("cantenna-input-${R.string.cantenna_diameter}")
            .performScrollTo().performTextReplacement("90")
        point.assertTextContains("Selected D · 90 mm", substring = true)
        compose.onNodeWithText("752").assertExists()
        panel("build").performClick()
        compose.onNodeWithTag("cantenna-aperture").performScrollTo().assertIsDisplayed()
    }

    @Test fun allPublishedDesignsSaveInBothLanguages() {
        compose.activity.getSharedPreferences("cantenna_calculator", 0).edit().clear().commit()
        val language = mutableStateOf(AppLanguage.ENGLISH)
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            LocalizedContent(language.value) {
                MaterialTheme {
                    CantennaScreen(null, { snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) }, {})
                }
            }
        }
        compose.onNodeWithText(localizedLabel(AppLanguage.ENGLISH, R.string.cantenna_check)).performScrollTo().performClick()
        compose.onNodeWithText(localizedLabel(AppLanguage.ENGLISH, R.string.cantenna_profiles)).performClick()
        for (locale in AppLanguage.entries) {
            compose.runOnIdle { language.value = locale }
            for (design in CantennaDesign.entries) {
                compose.onNodeWithTag("cantenna-profiles").performScrollTo().performClick()
                compose.onAllNodesWithText(localizedLabel(locale, design.title)).onLast().performClick()
                compose.onNodeWithTag("cantenna-save").performScrollTo().performClick()
                compose.runOnIdle {
                    val snapshot = checkNotNull(pending.record(AntennaType.CANTENNA, "Geometry").calculation)
                    assertEquals(design.model, snapshot.model)
                    assertEquals((design.mhz * 1e6).toLong(), checkNotNull(pending.newProject(AntennaType.CANTENNA, "C").target).hertz)
                }
            }
        }
    }

    @Test fun rejectsIncompatibleMeasurementPlanesAndImpedancesInBothLanguages() {
        val points = listOf(S11Point(1.25e9, .1, 0.0), S11Point(1.35e9, .2, 0.0))
        val before = MeasurementTrace("before.s1p", S11Sweep(50.0, points), SweepPlane.FEED_POINT)
        val after = MeasurementTrace("after.s1p", S11Sweep(75.0, points), SweepPlane.TRANSCEIVER)
        val language = mutableStateOf(AppLanguage.ENGLISH)
        compose.setContent {
            LocalizedContent(language.value) {
                MaterialTheme { Column { BeforeAfterBandwidth(before, after, null) {} } }
            }
        }
        for (locale in AppLanguage.entries) {
            compose.runOnIdle { language.value = locale }
            compose.onNodeWithText(localizedLabel(locale, R.string.sweep_reference_mismatch)).assertIsDisplayed()
            compose.onNodeWithText(localizedLabel(locale, R.string.sweep_plane_mismatch)).assertIsDisplayed()
            compose.onNodeWithTag("cantenna-copy-before-after").assertDoesNotExist()
        }
    }

    @Test fun transfersTheSelectedDiameterFromIndependentBandEdges() {
        compose.activity.getSharedPreferences("cantenna_calculator", 0).edit().clear().commit()
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme {
                CantennaScreen(null, { snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) }, {})
            }
        }
        fun label(id: Int) = compose.activity.getString(id)
        fun input(id: Int) = compose.onNodeWithTag("cantenna-input-$id")
        compose.onNodeWithText(label(R.string.cantenna_check)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.cantenna_select)).performClick()
        compose.onNodeWithTag("cantenna-band").performScrollTo().performClick()
        input(R.string.frequency).assertDoesNotExist()
        input(R.string.cantenna_low).performScrollTo().performTextReplacement("1250")
        input(R.string.cantenna_high).performScrollTo().performTextReplacement("1350")
        input(R.string.cantenna_chosen_d).performScrollTo().performTextReplacement("90")
        compose.onNodeWithTag("cantenna-transfer-diameter").performScrollTo().assertIsNotEnabled()
        input(R.string.cantenna_chosen_d).performScrollTo().performTextReplacement("150")
        compose.onNodeWithTag("cantenna-transfer-diameter").performScrollTo().performClick()
        compose.onNodeWithTag("cantenna-save").performScrollTo().performClick()
        compose.runOnIdle {
            val project = pending.newProject(AntennaType.CANTENNA, "Cantenna")
            assertEquals(1_300_000_000L, checkNotNull(project.target).hertz)
            val snapshot = checkNotNull(pending.record(AntennaType.CANTENNA, "Geometry").calculation)
            assertEquals(.15, snapshot.inputs.single { it.key == "cantenna_diameter" }.value, 1e-10)
        }
    }
    @Test fun opensFromToolsWithLargeTextAndCanExpandModel() {
        start(fontScale = 2f)
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-antenna-parameter-tools").performScrollTo().performClick()
        compose.scrollToAntennaTool("open-cantenna").performClick()
        compose.onNodeWithTag("cantenna-screen").assertIsDisplayed()
        compose.onNodeWithTag("antenna-rotating-view").performTouchInput { doubleClick() }
        compose.onNodeWithTag("antenna-full-view").assertIsDisplayed()
        back()
        compose.onNodeWithTag("antenna-full-view").assertDoesNotExist()
    }

    @Test fun preservesSelectedGeometryInProjectExchange() {
        compose.activity.getSharedPreferences("cantenna_calculator", 0).edit().clear().commit()
        lateinit var pending: PendingProjectCalculation
        compose.setContent {
            MaterialTheme {
                CantennaScreen(null, { snapshot, note -> pending = PendingProjectCalculation(snapshot, note = note) }, {})
            }
        }
        compose.onNodeWithTag("cantenna-save").performScrollTo().performClick()
        compose.runOnIdle {
            val type = AntennaType.CANTENNA
            val project = pending.newProject(type, "Cantenna").copy(records = listOf(pending.record(type, "Geometry")))
            val restored = JournalJson.decodeTransfer(JournalJson.encodeTransfer(project).inputStream())
            assertEquals(project, restored)
            val snapshot = checkNotNull(restored.records.single().calculation)
            assertEquals("cantenna_calculated", snapshot.model)
            assertEquals(.085, snapshot.inputs.single { it.key == "cantenna_diameter" }.value, 1e-10)
            assertEquals(.0581, snapshot.outputs.single { it.key == "cantenna_offset" }.value, .0001)
        }
    }

    private fun localizedLabel(language: AppLanguage, id: Int): String {
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language.tag))
        }
        return compose.activity.createConfigurationContext(configuration).getString(id)
    }
}
