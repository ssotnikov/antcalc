package local.antenna.calculator.journal

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.JournalLimits
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.journal.PhotoAttachment
import local.antenna.calculator.core.journal.SweepAttachment
import local.antenna.calculator.core.journal.SweepComparison
import local.antenna.calculator.measurements.MeasurementTrace

internal data class JournalUiState(
    val projects: List<AntennaProject> = emptyList(),
    val busy: Boolean = true,
    val problem: JournalProblem? = null,
    val readable: Boolean = false,
    val preview: AntennaProject? = null,
    val savedSweepTransfer: Pair<UUID, UUID>? = null,
    val photoProgress: Pair<Int, Int>? = null,
    val photoImport: PhotoImportResult? = null,
)

internal data class PhotoImportResult(
    val projectId: UUID, val added: Int, val problems: Map<PhotoImportProblem, Int>, val cancelled: Boolean,
    val photoId: UUID?,
)

internal class JournalViewModel(private val repository: ProjectsRepository) : ViewModel() {
    private val current = MutableStateFlow(JournalUiState(busy = false))
    val state = current.asStateFlow()
    private var pendingExport: ByteArray? = null
    private var pendingPhotoExport: File? = null
    private var photoImportJob: Job? = null

    fun photoFile(photo: PhotoAttachment, thumbnail: Boolean) = repository.photoFile(photo, thumbnail)

    fun clearPhotoImport() { current.value = current.value.copy(photoImport = null) }

    fun importPhotos(projectId: UUID, resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty() || current.value.busy) return
        val distinct = uris.distinct()
        val files = distinct.take(PHOTO_BATCH_LIMIT)
        val problems = mutableMapOf<PhotoImportProblem, Int>()
        val excess = distinct.size - files.size
        if (excess > 0) problems[PhotoImportProblem.QUOTA] = excess
        current.value = current.value.copy(busy = true, problem = null, photoProgress = 0 to files.size, photoImport = null)
        photoImportJob = viewModelScope.launch {
            val original = current.value.projects.find { it.id == projectId }?.photos.orEmpty().map { it.id }.toSet()
            var cancelled = false
            try {
                files.forEachIndexed { index, uri ->
                    val problem = try {
                        val projects = repository.addPhoto(projectId) {
                            if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException()
                            resolver.openInputStream(uri) ?: throw IOException()
                        }
                        current.value = current.value.copy(projects = projects)
                        null
                    } catch (error: PhotoImportException) {
                        error.problem
                    } catch (error: JournalException) {
                        when (error.problem) {
                            JournalProblem.LIMIT -> PhotoImportProblem.QUOTA
                            JournalProblem.WRITE -> PhotoImportProblem.WRITE
                            else -> PhotoImportProblem.READ
                        }
                    } catch (_: IOException) {
                        PhotoImportProblem.READ
                    } catch (_: SecurityException) {
                        PhotoImportProblem.READ
                    }
                    if (problem != null) problems[problem] = (problems[problem] ?: 0) + 1
                    current.value = current.value.copy(photoProgress = index + 1 to files.size)
                }
            } catch (_: CancellationException) {
                cancelled = true
            } finally {
                withContext(NonCancellable) {
                    val projects = if (cancelled) try {
                        repository.read()
                    } catch (_: JournalException) {
                        current.value.projects
                    } else current.value.projects
                    val added = projects.find { it.id == projectId }?.photos.orEmpty().filter { it.id !in original }
                    current.value = current.value.copy(projects = projects, busy = false, photoProgress = null,
                        photoImport = PhotoImportResult(projectId, added.size, problems.toMap(), cancelled, added.singleOrNull()?.id))
                }
            }
        }
    }

    fun cancelPhotoImport() { photoImportJob?.cancel() }

    fun updatePhotoComment(projectId: UUID, photoId: UUID, comment: String, done: () -> Unit) =
        mutate({ projects ->
            projects.replace(projectId) { project ->
                if (project.photos.none { it.id == photoId }) throw JournalException(JournalProblem.MISSING)
                project.copy(photos = project.photos.map { if (it.id == photoId) it.copy(comment = comment.trim()) else it })
            }
        }, done)

    fun removePhoto(projectId: UUID, photoId: UUID, done: () -> Unit) =
        mutate({ projects ->
            projects.replace(projectId) { it.copy(photos = it.photos.filterNot { photo -> photo.id == photoId }) }
        }, done)

    fun preparePhotoExport(projectId: UUID, photoId: UUID?, openPicker: () -> Unit) = operation(JournalProblem.WRITE) {
        cancelPhotoExport()
        withContext(NonCancellable) { pendingPhotoExport = repository.preparePhotoExport(projectId, photoId) }
        try {
            currentCoroutineContext().ensureActive()
        } catch (error: CancellationException) {
            cancelPhotoExport()
            throw error
        }
        openPicker()
    }

    fun cancelPhotoExport() {
        pendingPhotoExport?.delete()
        pendingPhotoExport = null
    }

    fun writePhotoExport(resolver: ContentResolver, uri: Uri, done: (Boolean, Boolean) -> Unit) =
        operation(JournalProblem.WRITE) {
            val source = pendingPhotoExport ?: throw JournalException(JournalProblem.MISSING)
            pendingPhotoExport = null
            val result = try {
                withContext(Dispatchers.IO) {
                    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
                    try {
                        (resolver.openOutputStream(uri, "wt") ?: throw IOException()).use { output ->
                            source.inputStream().use { it.copyTo(output) }
                        }
                        true to false
                    } catch (_: IOException) {
                        false to !deleteIncompleteExport(resolver, uri)
                    } catch (_: SecurityException) {
                        false to !deleteIncompleteExport(resolver, uri)
                    }
                }
            } finally {
                source.delete()
            }
            done(result.first, result.second)
        }

    private fun deleteIncompleteExport(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)

    override fun onCleared() { cancelPhotoExport() }

    init { reload() }

    fun reload() = operation(JournalProblem.READ) {
        current.value = current.value.copy(projects = repository.read(), readable = true)
    }

    fun saveProject(project: AntennaProject, create: Boolean, done: (UUID) -> Unit) =
        mutate({ projects ->
            if (create) projects + project else projects.replace(project.id) {
                project.copy(records = it.records, sweeps = it.sweeps, photos = it.photos)
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

    fun saveSweeps(
        transferId: UUID, projectId: UUID, traces: List<MeasurementTrace>, newProject: AntennaProject? = null,
    ) = operation(JournalProblem.WRITE) {
        current.value = current.value.copy(
            projects = repository.saveSweeps(projectId, traces, newProject),
            readable = true,
            savedSweepTransfer = transferId to projectId,
        )
    }

    suspend fun readSweep(ref: SweepAttachment): MeasurementTrace? =
        repository.readSweep(ref)

    fun removeSweeps(projectId: UUID, sweep: SweepComparison) =
        mutate({ projects -> projects.replace(projectId) { it.copy(sweeps = it.sweeps - sweep) } }, {})

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
