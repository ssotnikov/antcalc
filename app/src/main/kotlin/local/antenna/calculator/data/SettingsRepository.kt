package local.antenna.calculator.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.core.FileStorage
import androidx.datastore.core.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

interface SettingsRepository {
    suspend fun read(): PersistedCalculatorState
    suspend fun write(state: PersistedCalculatorState)
    suspend fun reset(): PersistedCalculatorState
}

/** One instance per process/file. A corrupt file can only be replaced during explicit reset(). */
class DataStoreSettingsRepository(
    private val file: File,
    private val scope: CoroutineScope,
    private val storageFactory: () -> Storage<Preferences> = { FileStorage(PreferencesFileSerializer) { file } },
) : SettingsRepository {
    private val operations = Mutex()
    private var storeScope = newStoreScope()
    private var store = createStore()

    private fun newStoreScope() = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + Dispatchers.IO)
    private fun createStore() = PreferenceDataStoreFactory.create(storage = storageFactory(), scope = storeScope)

    override suspend fun read(): PersistedCalculatorState = operations.withLock {
        // Persist the initial system-language choice once, including migration from schema 1.
        // Decoding first preserves corrupt or unsupported files without modifying their bytes.
        val preferences = store.updateData { current ->
            val decoded = PreferencesStateCodec.decode(current)
            PreferencesStateCodec.encode(decoded, current)
        }
        PreferencesStateCodec.decode(preferences)
    }

    override suspend fun write(state: PersistedCalculatorState) {
        operations.withLock {
            store.updateData { current ->
                PreferencesStateCodec.encode(state, current)
            }
        }
    }

    override suspend fun reset(): PersistedCalculatorState = operations.withLock {
        // Closing this store first releases DataStore's per-file ownership. No corruption handler
        // is installed: normal reads/retries can never replace bytes. Only this explicit reset can.
        storeScope.coroutineContext.job.cancelAndJoin()
        try {
            withContext(Dispatchers.IO) {
                if (file.exists() && !file.delete()) throw IOException("Cannot reset settings file")
            }
        } finally {
            storeScope = newStoreScope()
            store = createStore()
        }
        val defaults = PersistedCalculatorState.defaults()
        store.updateData { PreferencesStateCodec.encode(defaults) }
        defaults
    }
}
