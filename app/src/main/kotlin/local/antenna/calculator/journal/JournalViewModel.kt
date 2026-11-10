package local.antenna.calculator.journal

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.JournalLimits
import local.antenna.calculator.core.journal.JournalRecord

internal data class JournalUiState(
    val projects: List<AntennaProject> = emptyList(),
    val busy: Boolean = true,
    val problem: JournalProblem? = null,
    val readable: Boolean = false,
    val preview: AntennaProject? = null,
)

internal class JournalViewModel(private val repository: ProjectsRepository) : ViewModel() {
    private val current = MutableStateFlow(JournalUiState(busy = false))
    val state = current.asStateFlow()
    private var pendingExport: ByteArray? = null

    init { reload() }

    fun reload() = operation(JournalProblem.READ) {
        current.value = current.value.copy(projects = repository.read(), readable = true)
    }

    fun saveProject(project: AntennaProject, create: Boolean, done: (UUID) -> Unit) =
        mutate({ projects ->
            if (create) projects + project else projects.replace(project.id) {
                project.copy(records = it.records)
            }
        }) { done(project.id) }

    fun addRecord(projectId: UUID, record: JournalRecord, done: () -> Unit) =
        mutate({ projects ->
            projects.replace(projectId) {
                if (it.records.size >= JournalLimits.RECORDS) throw JournalException(JournalProblem.LIMIT)
                it.copy(records = it.records + record)
            }
        }, done)

    fun saveTransferredCalculation(
        projectId: UUID, record: JournalRecord, newProject: AntennaProject? = null,
    ) = mutate({ projects ->
        if (newProject != null && projects.none { it.id == projectId }) {
            projects + newProject.copy(records = listOf(record))
        } else {
            projects.replace(projectId) {
                if (it.antennaType != record.antennaType) throw JournalException(JournalProblem.INVALID)
                if (it.records.any { saved -> saved.id == record.id }) it else {
                    if (it.records.size >= JournalLimits.RECORDS) throw JournalException(JournalProblem.LIMIT)
                    it.copy(records = it.records + record.copy(installation = it.installation, feedline = it.feedline))
                }
            }
        }
    }, {})

    fun removeRecord(projectId: UUID, recordId: UUID, done: () -> Unit) =
        mutate({ projects ->
            projects.replace(projectId) { it.copy(records = it.records.filterNot { record -> record.id == recordId }) }
        }, done)

    fun removeProject(id: UUID, done: () -> Unit) =
        mutate({ projects -> projects.filterNot { it.id == id } }, done)

    fun duplicate(id: UUID, title: String, done: (UUID) -> Unit) {
        val newId = UUID.randomUUID()
        mutate({ projects ->
            val original = projects.find { it.id == id } ?: throw JournalException(JournalProblem.MISSING)
            projects + original.duplicate(newId, UUID::randomUUID).copy(title = title)
        }) { done(newId) }
    }

    fun previewImport(resolver: ContentResolver, uri: Uri) = operation(JournalProblem.READ) {
        current.value = current.value.copy(preview = null)
        val project = withContext(Dispatchers.IO) {
            require(uri.scheme == ContentResolver.SCHEME_CONTENT)
            (resolver.openInputStream(uri) ?: throw IOException()).use(JournalJson::decodeTransfer)
        }
        current.value = current.value.copy(preview = project)
    }

    fun clearPreview() {
        current.value = current.value.copy(preview = null)
    }

    fun importCopy(done: (UUID) -> Unit) {
        val project = current.value.preview ?: return
        val id = UUID.randomUUID()
        mutate({ it + project.duplicate(id, UUID::randomUUID) }) {
            clearPreview()
            done(id)
        }
    }

    fun prepareExport(project: AntennaProject, openPicker: () -> Unit) = operation(JournalProblem.WRITE) {
        pendingExport = withContext(Dispatchers.IO) { JournalJson.encodeTransfer(project) }
        openPicker()
    }

    fun cancelExport() { pendingExport = null }

    fun writeExport(resolver: ContentResolver, uri: Uri, done: () -> Unit) = operation(JournalProblem.WRITE) {
        val bytes = pendingExport ?: throw JournalException(JournalProblem.MISSING)
        pendingExport = null
        withContext(Dispatchers.IO) {
            require(uri.scheme == ContentResolver.SCHEME_CONTENT)
            (resolver.openOutputStream(uri, "wt") ?: throw IOException()).use { it.write(bytes) }
        }
        done()
    }

    fun filePickerUnavailable() {
        current.value = current.value.copy(problem = JournalProblem.MISSING)
    }

    fun dismissProblem() {
        current.value = current.value.copy(problem = null)
    }

    private fun mutate(change: (List<AntennaProject>) -> List<AntennaProject>, done: () -> Unit) =
        operation(JournalProblem.WRITE) {
            current.value = current.value.copy(projects = repository.update(change), readable = true)
            done()
        }

    private fun operation(fallback: JournalProblem, block: suspend () -> Unit) {
        if (current.value.busy) return
        current.value = current.value.copy(busy = true, problem = null)
        viewModelScope.launch {
            try {
                block()
            } catch (error: JournalException) {
                current.value = current.value.copy(problem = error.problem)
            } catch (_: IOException) {
                current.value = current.value.copy(problem = fallback)
            } catch (_: SecurityException) {
                current.value = current.value.copy(problem = fallback)
            } catch (_: IllegalArgumentException) {
                current.value = current.value.copy(problem = JournalProblem.INVALID)
            } finally {
                current.value = current.value.copy(busy = false)
            }
        }
    }

    private fun List<AntennaProject>.replace(id: UUID, update: (AntennaProject) -> AntennaProject): List<AntennaProject> {
        if (none { it.id == id }) throw JournalException(JournalProblem.MISSING)
        return map { if (it.id == id) update(it) else it }
    }
}
