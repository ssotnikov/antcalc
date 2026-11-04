package local.antenna.calculator.state

import androidx.datastore.core.CorruptionException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import local.antenna.calculator.data.*
import java.io.IOException

class CalculatorViewModel(
    private val repository: SettingsRepository,
    private val savedState: SavedStateHandle,
    private val persistenceScope: CoroutineScope,
) : ViewModel() {
    private val restoredDraft = DraftCodec.decode(savedState[DraftCodec.KEY])
    private val mutableState = MutableStateFlow(CalculatorUiState(draft = restoredDraft ?: CalculatorDraft.defaults()))
    val state: StateFlow<CalculatorUiState> = mutableState.asStateFlow()
    private var canonical = PersistedCalculatorState.defaults()
    private var lastWritten: PersistedCalculatorState? = null
    private var pendingDelay: Job? = null
    private var loadingJob: Job? = null
    private val writes = Mutex()
    private var storageReadable = false

    init {
        restoredDraft?.let { savedState[DraftCodec.KEY] = DraftCodec.encode(it) }
        load(initial = true)
    }

    /** Events and numerical results run synchronously on the main thread; persistence is independent. */
    fun onEvent(event: CalculatorEvent) {
        when (event) {
            CalculatorEvent.RetryStorage -> {
                if (state.value.storageIssue == StorageIssue.WRITE_FAILED) flush(retry = true) else load(initial = false)
                return
            }
            CalculatorEvent.ResetStorage -> { reset(); return }
            CalculatorEvent.Background -> { flush(); return }
            else -> Unit
        }
        if (!state.value.editingEnabled) return
        val draft = state.value.draft
        val updated = when (event) {
            is CalculatorEvent.Edit -> draft.update(event.key, draft[event.key].edit(event.text))
            is CalculatorEvent.ChangeUnit -> draft.update(event.key, draft[event.key].changeUnit(event.key, event.unit))
            is CalculatorEvent.Focus -> draft.update(event.key, draft[event.key].copy(
                focused = event.focused, checked = draft[event.key].checked || !event.focused))
            is CalculatorEvent.Check -> draft.copy(fields = draft.fields.mapValues { (key, field) ->
                if (key in event.keys) field.copy(checked = true) else field
            })
            is CalculatorEvent.Settings -> draft.copy(settings = event.value)
            else -> return
        }
        publish(updated)
        scheduleWrite()
    }

    private fun publish(draft: CalculatorDraft) {
        val current = draft
        canonical = canonical.advance(current)
        savedState[DraftCodec.KEY] = DraftCodec.encode(current)
        mutableState.value = state.value.copy(draft = current)
    }

    private fun load(initial: Boolean) {
        if (loadingJob?.isActive == true) return
        loadingJob = viewModelScope.launch {
            try {
                val stored = repository.read()
                lastWritten = stored
                canonical = stored
                storageReadable = true
                val draft = if (initial) restoredDraft ?: stored.toDraft() else state.value.draft
                mutableState.value = state.value.copy(loading = false, storageIssue = null)
                publish(draft)
                scheduleWrite()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                storageReadable = false
                mutableState.value = state.value.copy(loading = false, storageIssue = readIssue(error))
            }
        }
    }

    private fun scheduleWrite() {
        pendingDelay?.cancel()
        if (!storageReadable || state.value.storageIssue != null || canonical == lastWritten) return
        pendingDelay = viewModelScope.launch {
            delay(250)
            pendingDelay = null
            flush()
        }
    }

    private fun flush(retry: Boolean = false) {
        pendingDelay?.cancel()
        pendingDelay = null
        if (!storageReadable || state.value.loading || state.value.storageIssue != null && !(retry && state.value.storageIssue == StorageIssue.WRITE_FAILED)) return
        // Application scope lets an initiated background flush outlive Activity/ViewModel destruction.
        persistenceScope.launch {
            writes.withLock {
                if (!storageReadable || canonical == lastWritten ||
                    state.value.storageIssue != null && !(retry && state.value.storageIssue == StorageIssue.WRITE_FAILED)) return@withLock
                val snapshot = canonical
                mutableState.value = state.value.copy(saving = true)
                try {
                    repository.write(snapshot)
                    lastWritten = snapshot
                    mutableState.value = state.value.copy(saving = false, storageIssue = null)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val issue = when (error) {
                        is CorruptionException, is InvalidSettingsException -> StorageIssue.CORRUPT
                        is UnsupportedSchemaException -> StorageIssue.UNSUPPORTED_SCHEMA
                        else -> StorageIssue.WRITE_FAILED
                    }
                    if (issue != StorageIssue.WRITE_FAILED) storageReadable = false
                    mutableState.value = state.value.copy(saving = false, storageIssue = issue)
                }
            }
        }
    }

    private fun reset() {
        if (state.value.loading || loadingJob?.isActive == true) return
        pendingDelay?.cancel()
        persistenceScope.launch {
            writes.withLock {
                try {
                    val defaults = repository.reset()
                    storageReadable = true
                    canonical = defaults
                    lastWritten = defaults
                    mutableState.value = CalculatorUiState(defaults.toDraft(), loading = false)
                    savedState[DraftCodec.KEY] = DraftCodec.encode(state.value.draft)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    storageReadable = false
                    mutableState.value = state.value.copy(storageIssue = readIssue(error))
                }
            }
        }
    }

    private fun readIssue(error: Exception): StorageIssue = when (error) {
        is UnsupportedSchemaException -> StorageIssue.UNSUPPORTED_SCHEMA
        is InvalidSettingsException, is CorruptionException -> StorageIssue.CORRUPT
        is IOException -> StorageIssue.READ_FAILED
        else -> StorageIssue.READ_FAILED
    }
}

