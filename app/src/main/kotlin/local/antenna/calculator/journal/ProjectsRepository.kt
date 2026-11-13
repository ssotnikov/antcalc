package local.antenna.calculator.journal

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.JournalLimits
import local.antenna.calculator.core.journal.PhotoAttachment
import local.antenna.calculator.core.journal.SweepAttachment
import local.antenna.calculator.core.journal.SweepComparison
import local.antenna.calculator.measurements.AttachmentLimitException
import local.antenna.calculator.measurements.MeasurementStorage
import local.antenna.calculator.measurements.MeasurementTrace

/** A private, versioned file. Replacing it is the final step of each transaction. */
internal class ProjectsRepository(
    private val directory: File,
    private val measurements: MeasurementStorage? = null,
) {
    suspend fun saveSweeps(
        id: UUID, traces: List<MeasurementTrace>, newProject: AntennaProject? = null,
    ): List<AntennaProject> =
        update { projects ->
            val existing = projects.find { it.id == id }
            val project = existing ?: newProject ?: throw JournalException(JournalProblem.MISSING)
            if (existing == null && projects.size >= JournalLimits.PROJECTS) throw JournalException(JournalProblem.LIMIT)
            val used = projects.flatMap { it.sweeps }.flatMap { it.traces }.map { it.id }.toSet()
            val comparison = try {
                SweepComparison(checkNotNull(measurements).addAttachments(traces, used))
            } catch (_: AttachmentLimitException) {
                throw JournalException(JournalProblem.LIMIT)
            }
            if (comparison in project.sweeps) projects else {
                if (project.sweeps.size >= JournalLimits.RECORDS) throw JournalException(JournalProblem.LIMIT)
                val updated = project.copy(sweeps = project.sweeps + comparison)
                if (existing == null) projects + updated else projects.map { if (it.id == id) updated else it }
            }
        }
    private val file = File(directory, "projects.json")
    private val mutex = Mutex()
    private val photoFiles = JournalPhotoFiles(directory)
    private var photoStore = false
    private var initializedPhotos = false

    fun photoFile(photo: PhotoAttachment, thumbnail: Boolean): File = photoFiles.image(photo, thumbnail)

    suspend fun addPhoto(projectId: UUID, open: () -> InputStream): List<AntennaProject> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val projects = readLocked()
                val project = projects.find { it.id == projectId } ?: throw JournalException(JournalProblem.MISSING)
                if (project.photos.size >= PhotoAttachment.MAX_PER_PROJECT) throw PhotoImportException(PhotoImportProblem.LIMIT)
                val source = photoFiles.stageSource(open)
                try {
                    val photo = PhotoAttachment(UUID.randomUUID(), UUID.randomUUID(), photoFiles.format(source), OffsetDateTime.now())
                    try {
                        photoFiles.import(photo, source)
                        currentCoroutineContext().ensureActive()
                        if (!photoFiles.withinQuota()) throw PhotoImportException(PhotoImportProblem.LIMIT)
                        val result = projects.map { if (it.id == projectId) it.copy(photos = it.photos + photo) else it }
                        writeLocked(result, usePhotoStore = true)
                        result
                    } catch (error: Exception) {
                        photoFile(photo, false).delete()
                        photoFile(photo, true).delete()
                        throw error
                    }
                } finally {
                    source.delete()
                }
            }
        }

    suspend fun preparePhotoExport(projectId: UUID, photoId: UUID?): File =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val project = readLocked().find { it.id == projectId } ?: throw JournalException(JournalProblem.MISSING)
                val photos = if (photoId == null) project.photos.asReversed() else project.photos.filter { it.id == photoId }
                if (photos.isEmpty()) throw JournalException(JournalProblem.MISSING)
                photoFiles.prepareExport(photos, archive = photoId == null)
            }
        }

    suspend fun readSweep(ref: SweepAttachment): MeasurementTrace? =
        withContext(Dispatchers.IO) {
            mutex.withLock { measurements?.readAttachment(ref) }
        }

    suspend fun read(): List<AntennaProject> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }
    }

    suspend fun update(change: (List<AntennaProject>) -> List<AntennaProject>): List<AntennaProject> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val result = change(readLocked())
                if (result.size > JournalLimits.PROJECTS) throw JournalException(JournalProblem.LIMIT)
                writeLocked(result)
                photoFiles.cleanup(result.flatMap { it.photos })
                result
            }
        }

    private fun readLocked(): List<AntennaProject> = try {
        val projects = if (file.exists()) {
            file.inputStream().buffered().use { JournalJson.decodeStore(it) { version -> photoStore = version } }
        } else emptyList()
        if (!initializedPhotos) {
            photoFiles.cleanStaging()
            photoFiles.cleanup(projects.flatMap { it.photos })
            initializedPhotos = true
        }
        projects
    } catch (_: IOException) {
        throw JournalException(JournalProblem.READ)
    } catch (_: SecurityException) {
        throw JournalException(JournalProblem.READ)
    }

    private fun writeLocked(projects: List<AntennaProject>, usePhotoStore: Boolean = photoStore) {
        try {
            Files.createDirectories(directory.toPath())
            val temporary = File(directory, ".projects.pending").toPath()
            try {
                FileOutputStream(temporary.toFile()).use {
                    JournalJson.writeStore(projects, it, usePhotoStore)
                    it.fd.sync()
                }
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                photoStore = usePhotoStore
            } finally {
                // A failed write leaves the previously committed journal intact.
                temporary.toFile().delete()
            }
        } catch (_: IOException) {
            throw JournalException(JournalProblem.WRITE)
        } catch (_: SecurityException) {
            throw JournalException(JournalProblem.WRITE)
        }
    }
}
