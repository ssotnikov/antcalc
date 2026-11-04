package local.antenna.calculator

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import local.antenna.calculator.data.DataStoreSettingsRepository
import java.io.File

class CalculatorApplication : Application() {
    val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settingsRepository by lazy {
        DataStoreSettingsRepository(File(filesDir, "datastore/calculator.preferences_pb"), persistenceScope)
    }
}
