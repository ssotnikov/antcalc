package local.antenna.calculator.update

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.ui.CalculatorApp
import org.junit.Rule
import org.junit.Test

class StoreUpdateMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun storeMenuOmitsDirectUpdates() {
        compose.setContent {
            val state = CalculatorUiState(loading = false)
            CompositionLocalProvider(LocalActivity provides compose.activity) {
                CalculatorApp(state, {}) { state }
            }
        }
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-settings").assertExists()
        compose.onNodeWithTag("check-for-updates").assertDoesNotExist()
        compose.onNodeWithTag("open-github-releases").assertDoesNotExist()
        compose.onNodeWithTag("update-offer").assertDoesNotExist()
    }
}
