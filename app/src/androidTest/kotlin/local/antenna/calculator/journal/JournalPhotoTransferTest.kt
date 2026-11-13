package local.antenna.calculator.journal

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.trimming.AntennaType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class JournalPhotoTransferTest {
    @Test fun partialImportCancellationAndFailedExportPreserveCommittedPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = Files.createTempDirectory(context.cacheDir.toPath(), "photo-transfer-").toFile()
        val repository = ProjectsRepository(dir)
        val project = AntennaProject(UUID.randomUUID(), "Antenna", AntennaType.MONOPOLE)
        repository.update { listOf(project) }
        val input = File(dir, "fixture.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) = null
            override fun getType(uri: Uri) = "image/png"
            override fun insert(uri: Uri, values: ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                when (uri.lastPathSegment) {
                    "good" -> Unit
                    "blocked" -> {
                        started.complete(Unit)
                        check(release.await(15, TimeUnit.SECONDS))
                    }
                    else -> throw FileNotFoundException()
                }
                return ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        val resolver = ContentResolver.wrap(provider)
        fun uri(name: String) = Uri.parse("content://photo-fixture/document/$name")
        val model = JournalViewModel(repository)
        try {
            withTimeout(25_000) {
                model.state.first { !it.busy }
                withContext(Dispatchers.Main) { model.importPhotos(project.id, resolver, listOf(uri("bad"), uri("good"))) }
                val partial = model.state.first { it.photoImport != null }
                assertEquals(1, partial.photoImport?.added)
                assertEquals(mapOf(PhotoImportProblem.READ to 1), partial.photoImport?.problems)
                withContext(Dispatchers.Main) {
                    model.importPhotos(project.id, resolver, listOf(uri("blocked")))
                }
                started.await()
                withContext(Dispatchers.Main) { model.cancelPhotoImport() }
                release.countDown()
                val cancelled = model.state.first { it.photoImport?.cancelled == true }
                assertEquals(0, cancelled.photoImport?.added)
                assertEquals(1, repository.read().single().photos.size)

                val picker = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main) { model.preparePhotoExport(project.id, null) { picker.complete(Unit) } }
                picker.await()
                model.state.first { !it.busy }
                withContext(Dispatchers.Main) { model.cancelPhotoExport() }
                assertTrue(File(dir, "photo-staging").list().orEmpty().isEmpty())

                val nextPicker = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main) { model.preparePhotoExport(project.id, null) { nextPicker.complete(Unit) } }
                nextPicker.await()
                model.state.first { !it.busy }
                val failed = CompletableDeferred<Boolean>()
                withContext(Dispatchers.Main) {
                    model.writePhotoExport(resolver, uri("unwritable")) { success, _ -> failed.complete(success) }
                }
                assertFalse(failed.await())
                assertEquals(1, repository.read().single().photos.size)
                assertTrue(File(dir, "photo-staging").list().orEmpty().isEmpty())
            }
        } finally {
            release.countDown()
            model.viewModelScope.cancel()
            dir.deleteRecursively()
        }
    }
}
