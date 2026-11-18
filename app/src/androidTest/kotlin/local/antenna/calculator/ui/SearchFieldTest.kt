package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.R
import local.antenna.calculator.state.AppTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchFieldTest : CalculatorUiFixture() {
    @Test fun searchClearsWithoutLosingFocusInBothThemes() {
        val theme = mutableStateOf(AppTheme.DARK)
        val query = mutableStateOf("")
        compose.setContent {
            CalculatorTheme(theme.value) {
                Column {
                    ProjectSearchField(query.value, { query.value = it }, R.string.journal_project_search,
                        Modifier.fillMaxWidth().testTag("search"), maxLength = 32)
                }
            }
        }
        for (mode in listOf(AppTheme.DARK, AppTheme.LIGHT)) {
            compose.runOnIdle { theme.value = mode }
            val search = compose.onNodeWithTag("search")
            search.assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
            compose.onNodeWithTag("journal-clear-search").assertDoesNotExist()
            search.performClick().performTextInput("x".repeat(31) + "📡" + "extra")
            search.assertTextContains("x".repeat(31) + "📡")
            compose.onNodeWithTag("journal-clear-search").performClick()
            compose.runOnIdle { org.junit.Assert.assertEquals("", query.value) }
            search.assertIsFocused()
            compose.onNodeWithTag("journal-clear-search").assertDoesNotExist()
            search.performTextInput("145,5")
            search.assertTextContains("145,5")
            compose.onNodeWithTag("journal-clear-search").performClick()
        }
    }
}
