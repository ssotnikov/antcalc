package local.antenna.calculator.journal

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.JournalLimits

/** A private, versioned file. Replacing it is the final step of each transaction. */
internal class ProjectsRepository(private val directory: File) {
    private val file = File(directory, "projects.json")
    private val mutex = Mutex()

    suspend fun read(): List<AntennaProject> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }
    }

    suspend fun update(change: (List<AntennaProject>) -> List<AntennaProject>): List<AntennaProject> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val result = change(readLocked())
                if (result.size > JournalLimits.PROJECTS) throw JournalException(JournalProblem.LIMIT)
                writeLocked(result)
                result
            }
        }

    private fun readLocked(): List<AntennaProject> = try {
        if (file.exists()) file.inputStream().buffered().use(JournalJson::decodeStore) else emptyList()
    } catch (_: IOException) {
        throw JournalException(JournalProblem.READ)
    } catch (_: SecurityException) {
        throw JournalException(JournalProblem.READ)
    }

    private fun writeLocked(projects: List<AntennaProject>) {
        try {
            Files.createDirectories(directory.toPath())
            val temporary = File(directory, ".projects.pending").toPath()
            try {
                FileOutputStream(temporary.toFile()).use {
                    JournalJson.writeStore(projects, it)
                    it.fd.sync()
                }
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
