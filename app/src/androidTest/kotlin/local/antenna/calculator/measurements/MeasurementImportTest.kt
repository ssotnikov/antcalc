package local.antenna.calculator.measurements

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import local.antenna.calculator.core.measurements.Touchstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MeasurementImportTest {
    @Test fun cancellingAnInFlightReplacementPreservesTheVerifiedWorkspace() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "cancel-s1p-").toFile()
        val storage = MeasurementStorage(directory)
        val before = MeasurementTrace("verified.s1p", Touchstone.read("# MHz S RI R 50\n145 0.1 0".byteInputStream()))
        storage.writeWorkspace(listOf(before, null))
        val replacement = File(directory, "replacement.s1p").apply { writeText("# MHz S RI R 50\n145 0.8 0") }
        val opened = CompletableDeferred<Unit>()
        val releaseFile = CountDownLatch(1)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) = null
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun insert(uri: Uri, values: ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                opened.complete(Unit)
                check(releaseFile.await(15, TimeUnit.SECONDS))
                return ParcelFileDescriptor.open(replacement, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        val model = MeasurementViewModel(storage)
        try {
            withTimeout(15_000) {
                model.state.first { !it.busy }
                withContext(Dispatchers.Main) {
                    model.import(0, ContentResolver.wrap(provider), Uri.parse("content://s1p-fixture/replacement.s1p"))
                }
                opened.await()
                assertTrue(model.state.value.importing)
                withContext(Dispatchers.Main) { model.cancelImport() }
                val finished = model.state.first { !it.busy }
                assertNull(finished.error)
                assertEquals(listOf(before, null), finished.traces)
                assertEquals(listOf(before, null), storage.readWorkspace())
            }
        } finally {
            releaseFile.countDown()
            model.viewModelScope.cancel()
            replacement.delete()
            File(directory, "workspace.bin").delete()
            directory.delete()
        }
    }
}
