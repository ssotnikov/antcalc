package local.antenna.calculator

import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.measurements.MeasurementViewModel
import local.antenna.calculator.state.*
import local.antenna.calculator.ui.CalculatorApp

class MainActivity : ComponentActivity() {
    private lateinit var calculator: CalculatorViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = application as CalculatorApplication
        calculator = ViewModelProvider(this, viewModelFactory {
            initializer { CalculatorViewModel(container.settingsRepository, createSavedStateHandle(), container.persistenceScope) }
        })[CalculatorViewModel::class.java]
        val journal = ViewModelProvider(this, viewModelFactory {
            initializer { JournalViewModel(container.projectsRepository) }
        })[JournalViewModel::class.java]
        val measurements = ViewModelProvider(this, viewModelFactory {
            initializer { MeasurementViewModel(container.measurementStorage) }
        })[MeasurementViewModel::class.java]
        splash.setKeepOnScreenCondition { calculator.state.value.loading }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xFF101D26.toInt()),
        )
        setContent {
            val state by calculator.state.collectAsState()
            val dark = if (state.loading) true else when (state.draft.settings.theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            SideEffect {
                if (!state.loading) window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF101D26.toInt() else 0xFFF4F7FA.toInt()))
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x80101D26.toInt()) { dark },
                )
            }
            CompositionLocalProvider(
                LocalActivity provides this@MainActivity,
                LocalActivityResultRegistryOwner provides this@MainActivity,
            ) {
                CalculatorApp(state, calculator::onEvent, journal, measurements) { calculator.state.value }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        calculator.onEvent(CalculatorEvent.Background)
    }
}

