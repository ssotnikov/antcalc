package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import local.antenna.calculator.data.PersistedCalculatorState
import local.antenna.calculator.data.SettingsRepository
import local.antenna.calculator.state.*
import org.junit.After
import org.junit.Rule

abstract class CalculatorUiFixture {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    protected lateinit var model: CalculatorViewModel
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected fun start(draft: CalculatorDraft = CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(language = AppLanguage.RUSSIAN)) }, fontScale: Float = 1f, layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        compose.runOnUiThread {
            model = CalculatorViewModel(object : SettingsRepository {
                var value = PersistedCalculatorState.defaults().advance(draft)
                override suspend fun read() = value
                override suspend fun write(state: PersistedCalculatorState) { value = state }
                override suspend fun reset(): PersistedCalculatorState { value = PersistedCalculatorState.defaults(); return value }
            }, SavedStateHandle(mapOf(DraftCodec.KEY to DraftCodec.encode(draft))), persistenceScope)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides layoutDirection) {
                CalculatorApp(state, model::onEvent) { model.state.value }
            }
        }
        compose.waitUntil(timeoutMillis = 10000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("field-${if (draft.settings.section == Section.SIZING) "SIZING_FREQUENCY" else "ACTUAL_LENGTH"}").assertExists()
    }

    @After fun stop() { if (::model.isInitialized) model.viewModelScope.cancel(); persistenceScope.cancel() }

    protected fun field(key: FieldKey) = compose.onNodeWithTag("field-${key.name}")
    protected fun edit(key: FieldKey, text: String) { field(key).performScrollTo().performClick().performTextReplacement(text) }
    protected fun trimming() { compose.onNodeWithTag("nav-TRIMMING").performClick() }
    protected fun settings() { compose.onNodeWithTag("menu").performClick(); compose.onNodeWithTag("open-settings").performClick() }
    protected fun back() { compose.onNodeWithTag("back").performClick() }
}
