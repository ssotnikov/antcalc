package local.antenna.calculator.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.CalculatorDraft
import local.antenna.calculator.state.CalculatorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

class AuthorSupportTest : CalculatorUiFixture() {
    @Test fun showsBothEntriesOnlyInRussianDirectAndOpensTheSameLink() {
        start()
        val requests = mutableListOf<String>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                requests += intent.dataString.orEmpty()
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            val menu = compose.onNodeWithTag("menu")
            val entry = compose.onNodeWithTag("menu-support-author")
            val button = compose.onNodeWithTag("support-author")
            menu.performClick()
            val address = authorSupportPaymentUrl
            if (address == null) {
                entry.assertDoesNotExist()
                compose.onNodeWithTag("open-settings").performClick()
                button.assertDoesNotExist()
            } else {
                entry.assertTextContains("Поддержать автора").performClick()
                settings()
                button.performScrollTo().assertTextContains("Поддержать автора")
                val help = compose.onNodeWithTag("settings-help").getUnclippedBoundsInRoot()
                val support = button.getUnclippedBoundsInRoot()
                assertTrue(support.left >= help.right && support.top < help.bottom)
                button.performClick()
            }
            compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
            button.assertDoesNotExist()
            menu.performClick()
            entry.assertDoesNotExist()
            compose.runOnIdle { assertEquals(listOfNotNull(address, address), requests) }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun supportButtonMovesToTheNextRowWhenAvailableWidthIsSmall() {
        assumeNotNull(authorSupportPaymentUrl)
        val draft = CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(language = AppLanguage.RUSSIAN)) }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(260.dp)) {
                        SettingsScreen(CalculatorUiState(draft = draft, loading = false), {}, {})
                    }
                }
            }
        }
        val support = compose.onNodeWithTag("support-author").performScrollTo().assertIsDisplayed()
        val helpBounds = compose.onNodeWithTag("settings-help").getUnclippedBoundsInRoot()
        val supportBounds = support.getUnclippedBoundsInRoot()
        assertTrue(supportBounds.top >= helpBounds.bottom)
    }
}
