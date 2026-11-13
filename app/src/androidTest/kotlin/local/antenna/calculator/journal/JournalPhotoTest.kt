package local.antenna.calculator.journal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.PhotoFormat
import local.antenna.calculator.core.trimming.AntennaType
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalPhotoTest {
    private val directory = Files.createTempDirectory(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "journal-photo-test-",
    ).toFile()
    private val project = AntennaProject(UUID.randomUUID(), "Dipole", AntennaType.SYMMETRIC_DIPOLE)
    private val repo = ProjectsRepository(directory)

    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun legacyStoreUpgradesAndExportsPhotosWithIndependentComments() = runBlocking {
        repo.update { listOf(project) }
        val store = File(directory, "projects.json")
        assertEquals(1, JSONObject(store.readText()).getInt("schemaVersion"))
        val original = image(Bitmap.CompressFormat.PNG, width = 2048)
        val saved = repo.addPhoto(project.id) { original.inputStream() }.single()
        val photo = saved.photos.single().copy(comment = "Крепление <в сборе>")
        val png = BitmapFactory.decodeFile(repo.photoFile(photo, false).path)
        assertEquals(2048, png.width)
        assertEquals(0x70, png.getPixel(0, 0) ushr 24)
        png.recycle()
        repo.update { listOf(saved.copy(photos = listOf(photo))) }
        val restarted = ProjectsRepository(directory)
        val restored = restarted.read().single()
        assertEquals(photo, restored.photos.single())
        assertEquals(2, JSONObject(store.readText()).getInt("schemaVersion"))
        val copy = restored.duplicate(UUID.randomUUID(), UUID::randomUUID)
        val copyPhoto = copy.photos.single().copy(comment = "Independent")
        assertNotEquals(photo.id, copyPhoto.id)
        assertEquals(photo.fileId, copyPhoto.fileId)
        restarted.update { it + copy.copy(photos = listOf(copyPhoto)) }
        assertEquals(photo.comment, restarted.read().first().photos.single().comment)
        restarted.update { projects -> projects.filterNot { it.id == project.id } }
        assertTrue(restarted.photoFile(copyPhoto, true).isFile)
        assertTrue(restarted.photoFile(copyPhoto, false).isFile)
        val exported = restarted.preparePhotoExport(copy.id, null)
        ZipFile(exported).use { zip ->
            val metadata = JSONObject(zip.getInputStream(zip.getEntry("photos.json")).bufferedReader().readText())
            assertEquals(1, metadata.getInt("schemaVersion"))
            val entry = metadata.getJSONArray("photos").getJSONObject(0)
            assertEquals(copyPhoto.comment, entry.getString("comment"))
            assertEquals(copyPhoto.addedAt.toString(), entry.getString("addedAt"))
            val path = "photos/${copyPhoto.id}.png"
            assertEquals(path, entry.getString("path"))
            assertArrayEquals(restarted.photoFile(copyPhoto, false).readBytes(), zip.getInputStream(zip.getEntry(path)).readBytes())
        }
        val single = restarted.preparePhotoExport(copy.id, copyPhoto.id)
        assertArrayEquals(restarted.photoFile(copyPhoto, false).readBytes(), single.readBytes())
        assertEquals(copy.copy(photos = emptyList()), JournalJson.decodeTransfer(JournalJson.encodeTransfer(copy).inputStream()))
        restarted.update { emptyList() }
        assertFalse(restarted.photoFile(copyPhoto, false).exists())
        assertFalse(restarted.photoFile(copyPhoto, true).exists())
        assertEquals(2, JSONObject(store.readText()).getInt("schemaVersion"))
    }

    @Test fun jpegOrientationAndPrivateMetadataAreNormalized() = runBlocking {
        repo.update { listOf(project) }
        val original = File(directory, "original.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets.open("journal-photo-orientation.jpg").use { source ->
            original.outputStream().use { source.copyTo(it) }
        }
        assertEquals("Private label", ExifInterface(original.path).getAttribute(ExifInterface.TAG_MAKE))
        val photo = repo.addPhoto(project.id) { original.inputStream() }.single().photos.single()
        val file = repo.photoFile(photo, false)
        assertEquals(PhotoFormat.JPEG, photo.format)
        val bitmap = BitmapFactory.decodeFile(file.path)
        assertEquals(60, bitmap.width)
        assertEquals(100, bitmap.height)
        bitmap.recycle()
        assertNull(ExifInterface(file.path).getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("Private label", ExifInterface(original.path).getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test fun malformedFilesAndFailedMetadataCommitDoNotLeaveAttachments() = runBlocking {
        repo.update { listOf(project) }
        val original = image(Bitmap.CompressFormat.PNG)
        val bytes = original.readBytes()
        val animated = java.io.ByteArrayOutputStream().also { buffer ->
            java.io.DataOutputStream(buffer).use { out ->
                val chunk = "acTL".encodeToByteArray() + byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0)
                out.write(bytes, 0, 33)
                out.writeInt(8)
                out.write(chunk)
                out.writeInt(CRC32().apply { update(chunk) }.value.toInt())
                out.write(bytes, 33, bytes.size - 33)
            }
        }.toByteArray()
        val cases = listOf(
            "fake png".encodeToByteArray() to PhotoImportProblem.FORMAT,
            byteArrayOf(0xff.toByte(), 0xd8.toByte()) to PhotoImportProblem.DAMAGED,
            bytes.copyOf(bytes.size - 4) to PhotoImportProblem.DAMAGED,
            animated to PhotoImportProblem.FORMAT,
            ByteArray(20 * 1024 * 1024 + 1) to PhotoImportProblem.LIMIT,
            image(Bitmap.CompressFormat.PNG, width = 2049).readBytes() to PhotoImportProblem.LIMIT,
            image(Bitmap.CompressFormat.PNG, height = 2049).readBytes() to PhotoImportProblem.LIMIT,
        )
        original.writeBytes(bytes)
        for ((input, problem) in cases) {
            val error = runCatching { repo.addPhoto(project.id) { input.inputStream() } }.exceptionOrNull()
            assertEquals(problem, (error as PhotoImportException).problem)
        }
        val pending = File(directory, ".projects.pending")
        assertTrue(pending.mkdir())
        File(pending, "keep").writeText("force failure")
        assertEquals(JournalProblem.WRITE, (runCatching {
            repo.addPhoto(project.id) { original.inputStream() }
        }.exceptionOrNull() as JournalException).problem)
        assertEquals(listOf(project), repo.read())
        assertTrue(File(directory, "photos").list().orEmpty().isEmpty())
        assertTrue(File(directory, "photo-staging").list().orEmpty().isEmpty())
    }

    @Test fun missingPhotoDoesNotBlockJournalAndOrphansAreCleanedOnRestart() = runBlocking {
        repo.update { listOf(project) }
        val original = image(Bitmap.CompressFormat.PNG)
        val photo = repo.addPhoto(project.id) { original.inputStream() }.single().photos.single()
        val full = repo.photoFile(photo, false)
        assertTrue(full.delete())
        val orphan = File(full.parentFile, "orphan.png").apply { writeBytes(original.readBytes()) }
        val restarted = ProjectsRepository(directory)
        assertEquals(listOf(photo), restarted.read().single().photos)
        assertFalse(orphan.exists())
        assertTrue(runCatching { restarted.preparePhotoExport(project.id, null) }.isFailure)
        assertTrue(File(directory, "photo-staging").list().orEmpty().isEmpty())
    }

    private fun image(format: Bitmap.CompressFormat, width: Int = 24, height: Int = 12): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0x7000ff00)
        return File(directory, "input.bin").also { file ->
            file.outputStream().use { assertTrue(bitmap.compress(format, 90, it)) }
            bitmap.recycle()
        }
    }
}
