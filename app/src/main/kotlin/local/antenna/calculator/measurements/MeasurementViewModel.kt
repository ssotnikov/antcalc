package local.antenna.calculator.measurements

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import local.antenna.calculator.core.journal.SweepAttachment
import local.antenna.calculator.core.measurements.Touchstone
import local.antenna.calculator.core.measurements.TouchstoneException
import local.antenna.calculator.core.measurements.TouchstoneProblem

internal data class MeasurementState(
    val traces: List<MeasurementTrace?> = listOf(null, null),
    val busy: Boolean = true,
    val importing: Boolean = false,
    val error: MeasurementError? = null,
    val showTraces: Boolean = true,
)

internal enum class MeasurementProblem { FORMAT, FREQUENCY, LIMIT, READ, WRITE, WORKSPACE, MISSING }
internal data class MeasurementError(val problem: MeasurementProblem, val line: Int = 0)

internal class MeasurementViewModel(private val storage: MeasurementStorage) : ViewModel() {
    private val current = MutableStateFlow(MeasurementState())
    val state = current.asStateFlow()
    private var load: Job? = null
    private var workspaceReadable = false

    init {
        viewModelScope.launch {
            try {
                val traces = withContext(Dispatchers.IO) { storage.readWorkspace() }
                workspaceReadable = true
                current.value = MeasurementState(traces, busy = false)
            } catch (_: IOException) {
                current.value = MeasurementState(busy = false, error = MeasurementError(MeasurementProblem.WORKSPACE))
            } catch (_: SecurityException) {
                current.value = MeasurementState(busy = false, error = MeasurementError(MeasurementProblem.WORKSPACE))
            }
        }
    }

    fun import(slot: Int, resolver: ContentResolver, uri: Uri) = replace(MeasurementProblem.READ, cancellable = true) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException()
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
            ?.take(120)?.dropLastWhile(Char::isHighSurrogate)?.trim()?.takeIf { it.isNotEmpty() } ?: "S11.s1p"
        val job = currentCoroutineContext()
        val sweep = runInterruptible {
            (resolver.openInputStream(uri) ?: throw IOException()).use { input ->
                Touchstone.read(input) { job.ensureActive() }
            }
        }
        val traces = if (current.value.showTraces) current.value.traces else listOf(null, null)
        traces.toMutableList().apply { this[slot] = MeasurementTrace(name, sweep) }
    }

    fun cancelImport() { load?.cancel() }

    fun openAttachments(refs: List<SweepAttachment>) = replace(MeasurementProblem.READ, cancellable = true, hideOnFailure = true) {
        refs.map { storage.readAttachment(it) ?: throw MissingAttachmentException() }
            .let { listOf(it.first(), it.getOrNull(1)) }
    }

    fun showWorkspace() { if (!current.value.busy) current.value = current.value.copy(showTraces = true) }

    fun remove(slot: Int) = replace(MeasurementProblem.WRITE) {
        current.value.traces.toMutableList().apply { this[slot] = null }
    }

    fun setPlane(slot: Int, plane: SweepPlane) = replace(MeasurementProblem.WRITE) {
        current.value.traces.toMutableList().apply { this[slot] = this[slot]?.copy(plane = plane) }
    }

    fun pickerUnavailable() {
        if (!current.value.busy) current.value = current.value.copy(error = MeasurementError(MeasurementProblem.READ))
    }

    private fun replace(
        fallback: MeasurementProblem,
        cancellable: Boolean = false,
        hideOnFailure: Boolean = false,
        loadTraces: suspend () -> List<MeasurementTrace?>,
    ) {
        if (current.value.busy || !workspaceReadable) return
        current.value = current.value.copy(busy = true, importing = cancellable, error = null,
            showTraces = current.value.showTraces && !hideOnFailure)
        val task = viewModelScope.launch {
            try {
                val traces = withContext(Dispatchers.IO) { loadTraces() }
                ensureActive()
                // Once commit starts, finish the disk and UI update together.
                withContext(NonCancellable) {
                    try {
                        withContext(Dispatchers.IO) { storage.writeWorkspace(traces) }
                        current.value = current.value.copy(traces = traces, showTraces = true)
                    } catch (_: IOException) {
                        current.value = current.value.copy(error = MeasurementError(MeasurementProblem.WRITE))
                    }
                }
            } catch (error: TouchstoneException) {
                val problem = when (error.problem) {
                    TouchstoneProblem.FORMAT -> MeasurementProblem.FORMAT
                    TouchstoneProblem.FREQUENCY -> MeasurementProblem.FREQUENCY
                    TouchstoneProblem.LIMIT -> MeasurementProblem.LIMIT
                }
                current.value = current.value.copy(error = MeasurementError(problem, error.line))
            } catch (_: MissingAttachmentException) {
                current.value = current.value.copy(error = MeasurementError(MeasurementProblem.MISSING))
            } catch (_: IOException) {
                current.value = current.value.copy(error = MeasurementError(fallback))
            } catch (_: SecurityException) {
                current.value = current.value.copy(error = MeasurementError(fallback))
            } finally {
                current.value = current.value.copy(busy = false, importing = false)
            }
        }
        load = if (cancellable) task else null
    }

    private class MissingAttachmentException : IOException()
}
