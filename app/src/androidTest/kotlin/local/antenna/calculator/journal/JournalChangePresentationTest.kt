package local.antenna.calculator.journal

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import local.antenna.calculator.core.journal.JournalComparison
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalChangePresentationTest {
    private fun presentation(language: String): JournalPresentation {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale(language)) }
        return JournalPresentation(context.createConfigurationContext(configuration))
    }

    @Test fun zeroChangesAreExplicitInBothLanguages() {
        for ((language, expected) in listOf("ru" to "(без изменений)", "en" to "(unchanged)")) {
            val fields = presentation(language).changes(JournalComparison(0, 0, -0.0, emptySet()))
            assertEquals(listOf(expected, expected, expected), fields.map { it.second })
        }
    }

    @Test fun signsUnitsAndSmallDeltasArePreserved() {
        val fields = presentation("en").changes(JournalComparison(500_000, -3_060_000, 0.25, emptySet()))
        assertEquals(listOf("(+0.500 MHz)", "(−3.06 mm)", "(+0.25)"), fields.map { it.second })
        val small = presentation("en").changes(JournalComparison(1, 1, -0.125, emptySet()))
        assertEquals(listOf("(+0.000001 MHz)", "(+0.000001 mm)", "(−0.125)"), small.map { it.second })
    }

    @Test fun unavailableDifferencesAreNotReportedAsUnchanged() {
        assertEquals(emptyList<Pair<String, String>>(),
            presentation("ru").changes(JournalComparison(null, null, null, emptySet())))
    }
}
