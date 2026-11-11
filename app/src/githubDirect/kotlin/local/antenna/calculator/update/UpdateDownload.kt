package local.antenna.calculator.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import local.antenna.calculator.BuildConfig

internal open class InvalidUpdateApk : IOException()

private class IncompleteUpdateApk : InvalidUpdateApk()

internal sealed interface UpdateDownloadError {
    data class Http(val status: Int) : UpdateDownloadError
    data object Timeout : UpdateDownloadError
    data object Connection : UpdateDownloadError
    data object Incomplete : UpdateDownloadError
    data object Integrity : UpdateDownloadError
    data object FileAccess : UpdateDownloadError
}

internal class UpdateDownloadException(val reason: UpdateDownloadError, cause: IOException? = null) :
    IOException(cause)

private val SHA256 = Regex("[a-f0-9]{64}")
private const val MAX_SIZE = 64L * 1024 * 1024

internal fun copyVerifiedApk(
    input: InputStream,
    output: OutputStream,
    release: UpdateRelease,
    progress: (Long) -> Unit,
) {
    if (release.size !in 1..MAX_SIZE || !SHA256.matches(release.sha256)) throw InvalidUpdateApk()
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var bytes = 0L
    while (true) {
        progress(bytes)
        val read = input.read(buffer)
        if (read < 0) break
        bytes += read
        if (bytes > release.size) throw InvalidUpdateApk()
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
    }
    if (bytes != release.size) throw IncompleteUpdateApk()
    if (!MessageDigest.isEqual(digest.digest(), release.sha256.hexBytes())) throw InvalidUpdateApk()
}

private fun String.hexBytes() = ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }

internal suspend fun downloadUpdate(release: UpdateRelease, file: File, progress: (Long) -> Unit) {
    val scope = currentCoroutineContext()
    val deadline = SystemClock.elapsedRealtime() + 15 * 60_000
    var lastProgress = 0L
    try {
        withUpdateResponse(release.apkUrl, "application/octet-stream", readTimeoutMillis = 30_000) { response, status ->
            if (status != 200) throw UpdateDownloadException(UpdateDownloadError.Http(status))
            val length = response.getHeaderField("Content-Length")?.toLongOrNull()
            if (length != null && length != release.size) throw InvalidUpdateApk()
            response.inputStream.use { input ->
                file.outputStream().use { output ->
                    copyVerifiedApk(input, output, release) { bytes ->
                        scope.ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        if (now > deadline) throw SocketTimeoutException()
                        if (bytes < release.size && now - lastProgress >= 100) {
                            progress(bytes)
                            lastProgress = now
                        }
                    }
                }
            }
        }
        progress(release.size)
    } catch (error: UpdateDownloadException) {
        throw error
    } catch (error: IOException) {
        scope.ensureActive()
        val reason = when (error) {
            is SocketTimeoutException -> UpdateDownloadError.Timeout
            is IncompleteUpdateApk, is EOFException -> UpdateDownloadError.Incomplete
            is InvalidUpdateApk -> UpdateDownloadError.Integrity
            is UnknownHostException, is SocketException -> UpdateDownloadError.Connection
            else -> UpdateDownloadError.FileAccess
        }
        throw UpdateDownloadException(reason, error)
    }
}

@Suppress("DEPRECATION")
internal fun verifyUpdateArchive(context: Context, file: File, release: UpdateRelease) {
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else PackageManager.GET_SIGNATURES
    val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, signatures or PackageManager.GET_META_DATA)
        ?: throw InvalidUpdateApk()
    val app = info.applicationInfo ?: throw InvalidUpdateApk()
    val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    val signer = signers?.singleOrNull() ?: throw InvalidUpdateApk()
    val digest = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
    if (info.packageName != BuildConfig.APPLICATION_ID ||
        PackageInfoCompat.getLongVersionCode(info) != release.versionCode ||
        release.versionCode <= BuildConfig.VERSION_CODE ||
        info.versionName != release.versionName || app.minSdkVersion.toLong() != release.minSdk ||
        app.metaData?.getString("local.antenna.calculator.UPDATE_CHANNEL") != "github-direct" ||
        !MessageDigest.isEqual(digest, BuildConfig.UPDATE_SIGNER_SHA256.hexBytes())) {
        throw InvalidUpdateApk()
    }
}
