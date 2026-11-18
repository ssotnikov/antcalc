package local.antenna.calculator.ui

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import local.antenna.calculator.journal.JournalDetails
import local.antenna.calculator.state.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalLinksUiTest : CalculatorUiFixture() {
    @Test fun darkThemeLinkIsClickableWithinSelectableText() = checkLink(AppTheme.DARK)
    @Test fun lightThemeLinkIsClickableWithinSelectableText() = checkLink(AppTheme.LIGHT)

    private fun checkLink(theme: AppTheme) {
        val text = "Материалы конструкции: $LIZAALERT_YAGI_URL"
        var opened: Intent? = null
        val browserContext = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { opened = intent }
        }
        compose.setContent {
            CalculatorTheme(theme) {
                CompositionLocalProvider(LocalContext provides browserContext) {
                    JournalDetails(listOf("Заметки" to text))
                }
            }
        }
        compose.onNodeWithText(text).performFirstLinkClick()
        compose.runOnIdle {
            assertEquals(LIZAALERT_YAGI_URL, opened?.dataString)
            assertEquals(Intent.ACTION_VIEW, opened?.action)
            assertEquals(setOf(Intent.CATEGORY_BROWSABLE), opened?.categories)
        }
        compose.onNodeWithText(text).assertExists()
    }
}
