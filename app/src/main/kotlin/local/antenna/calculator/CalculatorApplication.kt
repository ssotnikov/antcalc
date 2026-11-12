package local.antenna.calculator

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import local.antenna.calculator.data.DataStoreSettingsRepository
import local.antenna.calculator.journal.ProjectsRepository
import local.antenna.calculator.measurements.MeasurementStorage
import java.io.File

class CalculatorApplication : Application() {
    val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val measurementStorage by lazy {
        MeasurementStorage(File(filesDir, "measurements"))
    }
    internal val projectsRepository by lazy { ProjectsRepository(File(filesDir, "journal"), measurementStorage) }
    val settingsRepository by lazy {
        DataStoreSettingsRepository(File(filesDir, "datastore/calculator.preferences_pb"), persistenceScope)
    }
}
