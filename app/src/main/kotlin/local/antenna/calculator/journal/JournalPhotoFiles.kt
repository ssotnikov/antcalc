package local.antenna.calculator.journal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import local.antenna.calculator.core.journal.PhotoAttachment
import local.antenna.calculator.core.journal.PhotoFormat
import org.json.JSONArray
import org.json.JSONObject

internal const val PHOTO_BATCH_LIMIT = 8
internal enum class PhotoImportProblem { FORMAT, DAMAGED, LIMIT, QUOTA, READ, WRITE }
internal class PhotoImportException(problem: PhotoImportProblem) : IOException() {
    val problem = problem
}

internal class JournalPhotoFiles(directory: File) {
    private val imageDirectory = File(directory, "photos")
    private val staging = File(directory, "photo-staging")

    fun image(photo: PhotoAttachment, thumbnail: Boolean = false): File =
        File(imageDirectory, "${photo.fileId}${if (thumbnail) "-thumb" else ""}.${photo.format.extension}")

    fun cleanup(referenced: List<PhotoAttachment>) {
        val names = referenced.flatMap { listOf(image(it).name, image(it, true).name) }.toHashSet()
        imageDirectory.listFiles()?.forEach { if (it.name !in names) it.delete() }
    }

    suspend fun import(photo: PhotoAttachment, source: File) {
        ensureDirectory(imageDirectory)
        val decoded = decode(source, photo.format)
        try {
            save(decoded, image(photo), photo.format)
            val thumbnail = scale(decoded, 384)
            try {
                save(thumbnail, image(photo, true), photo.format)
            } finally {
                if (thumbnail !== decoded) thumbnail.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    suspend fun stageSource(open: () -> InputStream): File {
        val file = temporary()
        try {
            open().use { source ->
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read > INPUT_LIMIT - total) fail(PhotoImportProblem.LIMIT)
                        output.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    suspend fun format(file: File): PhotoFormat {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() >= 2 && input.readUnsignedShort() == 0xffd8) {
                if (input.length() < 4) fail(PhotoImportProblem.DAMAGED)
                input.seek(input.length() - 2)
                if (input.readUnsignedShort() != 0xffd9) fail(PhotoImportProblem.DAMAGED)
                return PhotoFormat.JPEG
            }
            input.seek(0)
            if (input.length() < 8 || input.readLong() != PNG_SIGNATURE) fail(PhotoImportProblem.FORMAT)
        }
        validatePng(file)
        return PhotoFormat.PNG
    }

    private suspend fun validatePng(file: File) {
        DataInputStream(file.inputStream().buffered()).use { input ->
            input.readLong()
            val buffer = ByteArray(32 * 1024)
            var first = true
            var hasImageData = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (input.available() < 12) fail(PhotoImportProblem.DAMAGED)
                val size = input.readInt()
                val type = ByteArray(4).also(input::readFully)
                val name = type.toString(Charsets.US_ASCII)
                if (size < 0 || size > input.available() - 4) fail(PhotoImportProblem.DAMAGED)
                if (first && (name != "IHDR" || size != 13)) fail(PhotoImportProblem.DAMAGED)
                if (name == "acTL" || name == "fcTL" || name == "fdAT") fail(PhotoImportProblem.FORMAT)
                first = false
                val crc = CRC32()
                crc.update(type)
                var left = size
                while (left > 0) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(left, buffer.size)
                    input.readFully(buffer, 0, count)
                    crc.update(buffer, 0, count)
                    left -= count
                }
                if (crc.value.toInt() != input.readInt()) fail(PhotoImportProblem.DAMAGED)
                if (name == "IDAT") hasImageData = true
                if (name == "IEND") {
                    if (!hasImageData || size != 0 || input.read() != -1) fail(PhotoImportProblem.DAMAGED)
                    return
                }
            }
        }
    }

    fun withinQuota(): Boolean =
        imageDirectory.listFiles().orEmpty().sumOf { it.length() } <= 100L * 1024 * 1024

    suspend fun prepareExport(photos: List<PhotoAttachment>, archive: Boolean): File {
        val output = temporary()
        try {
            if (!archive) {
                output.outputStream().buffered().use { copyImage(photos.single(), it) }
            } else {
                ZipOutputStream(output.outputStream().buffered()).use { zip ->
                    val entries = JSONArray()
                    for (photo in photos) {
                        val path = "photos/${photo.id}.${photo.format.extension}"
                        zip.putNextEntry(ZipEntry(path))
                        copyImage(photo, zip)
                        zip.closeEntry()
                        entries.put(JSONObject().put("path", path).put("addedAt", photo.addedAt.toString())
                            .put("comment", photo.comment))
                    }
                    zip.putNextEntry(ZipEntry("photos.json"))
                    zip.write(JSONObject().put("schemaVersion", 1).put("photos", entries).toString().encodeToByteArray())
                    zip.closeEntry()
                }
            }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    fun cleanStaging() {
        staging.listFiles()?.forEach { it.delete() }
    }

    private suspend fun copyImage(photo: PhotoAttachment, output: OutputStream) {
        val file = image(photo)
        if (format(file) != photo.format) fail(PhotoImportProblem.DAMAGED)
        file.inputStream().buffered().use { source ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = source.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
    }

    private fun temporary(): File {
        ensureDirectory(staging)
        return File.createTempFile("photo-", ".pending", staging)
    }

    private fun decode(file: File, format: PhotoFormat): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0 || options.outMimeType != format.mimeType) fail(PhotoImportProblem.DAMAGED)
        if (width > 2048 || height > 2048) fail(PhotoImportProblem.LIMIT)
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: fail(PhotoImportProblem.DAMAGED)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun scale(bitmap: Bitmap, limit: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= limit) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * limit / side).coerceAtLeast(1),
            (bitmap.height * limit / side).coerceAtLeast(1), true)
    }

    private fun save(bitmap: Bitmap, file: File, format: PhotoFormat) {
        val encoding = if (format == PhotoFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        FileOutputStream(file).use { stream ->
            if (!bitmap.compress(encoding, 90, stream)) fail(PhotoImportProblem.WRITE)
            stream.fd.sync()
        }
    }

    private fun ensureDirectory(file: File) {
        if (!file.isDirectory && !file.mkdirs()) throw IOException()
    }

    private fun fail(problem: PhotoImportProblem): Nothing = throw PhotoImportException(problem)

    private companion object {
        const val INPUT_LIMIT = 20 * 1024 * 1024
        const val PNG_SIGNATURE = -8552249625308161526L
    }
}
