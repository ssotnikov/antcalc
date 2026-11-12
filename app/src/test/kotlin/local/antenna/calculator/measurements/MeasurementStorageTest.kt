package local.antenna.calculator.measurements

import java.io.File
import java.io.IOException
import local.antenna.calculator.core.measurements.Touchstone
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MeasurementStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun trace(name: String = "example.s1p") = MeasurementTrace(
        name, Touchstone.read("# MHz S RI R 50\n145 0.2 0.3\n146 0.1 0".byteInputStream()), SweepPlane.FEED_POINT,
    )

    @Test fun workspaceSurvivesColdStartAndUnknownSchemaIsPreserved() {
        val directory = temporary.newFolder()
        val storage = MeasurementStorage(directory)
        assertEquals(listOf(null, null), storage.readWorkspace())
        storage.writeWorkspace(listOf(trace(), trace("second.s1p")))
        assertEquals(listOf(trace(), trace("second.s1p")), MeasurementStorage(directory).readWorkspace())
        val workspace = File(directory, "workspace.bin")
        val newer = byteArrayOf(0, 0, 0, 2, 42)
        workspace.writeBytes(newer)
        assertThrows(IOException::class.java) { storage.readWorkspace() }
        assertThrows(IOException::class.java) { storage.writeWorkspace(listOf(trace(), null)) }
        assertArrayEquals(newer, workspace.readBytes())
        val damaged = byteArrayOf(0, 0, 0, 1, 1, 42)
        workspace.writeBytes(damaged)
        assertThrows(IOException::class.java) { storage.writeWorkspace(listOf(trace(), null)) }
        assertArrayEquals(damaged, workspace.readBytes())
    }

    @Test fun attachmentsDeduplicateAndRetainReferencedFiles() {
        val directory = temporary.newFolder()
        val first = trace()
        val storage = MeasurementStorage(directory)
        val ref = storage.addAttachments(listOf(first, first), emptySet()).singleDistinct()
        assertEquals(first, MeasurementStorage(directory).readAttachment(ref))
        val another = storage.addAttachments(listOf(trace("another.s1p")), setOf(ref.id)).single()
        assertNotNull(storage.readAttachment(ref))
        assertNotNull(storage.readAttachment(another))
        storage.addAttachments(listOf(trace("third.s1p")), setOf(another.id))
        assertNull(storage.readAttachment(ref))
    }

    @Test fun attachmentBudgetRejectsOversizedWrite() {
        val directory = temporary.newFolder()
        val attachments = File(directory, "attachments").apply { mkdir() }
        val id = "a".repeat(64)
        val blob = File(attachments, "$id.bin")
        java.io.RandomAccessFile(blob, "rw").use { it.setLength(20L * 1024 * 1024) }
        assertThrows(AttachmentLimitException::class.java) {
            MeasurementStorage(directory).addAttachments(listOf(trace()), setOf(id))
        }
        assertArrayEquals(arrayOf("$id.bin"), attachments.list())
    }

    private fun <T> List<T>.singleDistinct(): T {
        assertEquals(2, size)
        return distinct().single()
    }
}
