package local.antenna.calculator.journal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectsRepositoryTest {
    private val directory = Files.createTempDirectory(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "journal-test-",
    ).toFile()

    @Test fun recordsAndHistoricalCalculationsRoundTripAndDuplicateIndependently() = runBlocking {
        val record = JournalRecord(UUID.randomUUID(), "Before tuning", OffsetDateTime.parse("2026-11-09T10:15:00+03:00"),
            AntennaType.SYMMETRIC_DIPOLE, note = "<b>Literal note</b>", measurement = JournalMeasurement(
                resonance = Frequency.ofHertz(142_800_001), swr = 2.4, point = MeasurementPoint.FEED_POINT,
            ))
        val values = listOf(SnapshotValue("length", 0.9820787417, SnapshotUnit.METERS))
        val old = record.copy(id = UUID.randomUUID(), measurement = null,
            calculation = CalculationSnapshot("future_model", 99, values, values, listOf("FUTURE_WARNING")))
        val first = sample().copy(records = listOf(record, old))
        val repo = ProjectsRepository(directory)
        repo.update { listOf(first) }
        assertEquals(listOf(first), ProjectsRepository(directory).read())

        val imported = JournalJson.decodeTransfer(JournalJson.encodeTransfer(first).inputStream())
        assertEquals(first, imported)
        val duplicate = imported.duplicate(UUID.randomUUID(), UUID::randomUUID)
        repo.update { it + duplicate }
        assertNotEquals(first.id, duplicate.id)
        assertNotEquals(record.id, duplicate.records[0].id)
        assertEquals(old.calculation, duplicate.records[1].calculation)
        assertEquals(listOf(first, duplicate), repo.read())
    }

    @Test fun interruptedOrFailedWriteKeepsCommittedBytes() = runBlocking {
        val repo = ProjectsRepository(directory)
        val first = sample()
        repo.update { listOf(first) }
        val saved = File(directory, "projects.json")
        val bytes = saved.readBytes()
        val pending = File(directory, ".projects.pending")
        pending.writeText("incomplete")
        assertEquals(listOf(first), ProjectsRepository(directory).read())
        pending.delete()
        assertTrue(pending.mkdir())
        File(pending, "unavailable").writeText("block the write")
        assertEquals(JournalProblem.WRITE, failure { repo.update { it + sample() } })
        assertArrayEquals(bytes, saved.readBytes())
        assertEquals(listOf(first), repo.read())
    }

    @Test fun unknownStoreAndInvalidOrOversizedTransferAreRejected() = runBlocking {
        val repo = ProjectsRepository(directory)
        val store = File(directory, "projects.json")
        val original = """{"schemaVersion":9,"future":true,"projects":[]}"""
        store.writeText(original)
        assertEquals(JournalProblem.UNSUPPORTED, failure { repo.update { listOf(sample()) } })
        assertEquals(original, store.readText())
        assertEquals(JournalProblem.INVALID, failure {
            JournalJson.decodeTransfer("""{"schemaVersion":1,"schemaVersion":1}""".byteInputStream())
        })
        assertEquals(JournalProblem.INVALID, failure {
            val bad = JournalJson.encodeTransfer(sample()).decodeToString().replace("\"targetHz\":145000000", "\"targetHz\":\"145000000\"")
            JournalJson.decodeTransfer(bad.byteInputStream())
        })
        assertEquals(JournalProblem.LIMIT, failure {
            JournalJson.decodeTransfer(ByteArray(JournalLimits.TRANSFER_BYTES + 1) { ' '.code.toByte() }.inputStream())
        })
    }

    @Test fun projectCountLimitKeepsExistingProject() = runBlocking {
        val repo = ProjectsRepository(directory)
        val first = sample()
        repo.update { listOf(first) }
        assertEquals(JournalProblem.LIMIT, failure { repo.update { List(JournalLimits.PROJECTS + 1) { sample() } } })
        assertEquals(listOf(first), repo.read())
    }

    private fun sample() = AntennaProject(UUID.randomUUID(), "Диполь", AntennaType.SYMMETRIC_DIPOLE,
        target = Frequency.ofHertz(145_000_000), installation = "3 м", feedline = "6 м")

    private suspend fun failure(action: suspend () -> Unit): JournalProblem = try {
        action()
        throw AssertionError("Operation unexpectedly succeeded")
    } catch (error: JournalException) {
        error.problem
    }
}
