package local.antenna.calculator.update

import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateDownloadTest {
    private val bytes = "apk contents".encodeToByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val update = UpdateRelease("1.7.1", 61, 26, bytes.size.toLong(), hash)

    @Test fun downloadChecksLengthHashAndInterruptedStreams() {
        val out = ByteArrayOutputStream()
        copyVerifiedApk(bytes.inputStream(), out, update) {}
        assertArrayEquals(bytes, out.toByteArray())
        assertThrows(InvalidUpdateApk::class.java) { copy(bytes.dropLast(1).toByteArray(), update) }
        assertThrows(InvalidUpdateApk::class.java) { copy(bytes + 0.toByte(), update) }
        assertThrows(InvalidUpdateApk::class.java) { copy(bytes, update.copy(sha256 = "0".repeat(64))) }
        assertThrows(IOException::class.java) {
            copyVerifiedApk(object : InputStream() {
                override fun read(): Int = throw IOException("Disconnected")
            }, ByteArrayOutputStream(), update) {}
        }
    }

    @Test fun assetRedirectsStayOnGitHubOverHttps() {
        val from = URI(update.apkUrl)
        val expected = "https://release-assets.githubusercontent.com/path?token=value"
        assertEquals(expected, updateRedirect(from, expected).toString())
        for (url in listOf("http://release-assets.githubusercontent.com/path", "https://example.org/app.apk",
            "https://user@release-assets.githubusercontent.com/path", "https://release-assets.githubusercontent.com:444/path")) {
            assertThrows(IOException::class.java) { updateRedirect(from, url) }
        }
    }

    @Test fun archiveRejectsUnparseableAndUnapprovedPackages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invalid = File.createTempFile("update-test-", ".apk", context.cacheDir)
        try {
            invalid.writeBytes(bytes)
            assertThrows(InvalidUpdateApk::class.java) { verifyUpdateArchive(context, invalid, update) }
            val debug = File(context.applicationInfo.sourceDir)
            assertThrows(InvalidUpdateApk::class.java) { verifyUpdateArchive(context, debug, update) }
        } finally {
            invalid.delete()
        }
    }

    private fun copy(input: ByteArray, release: UpdateRelease) =
        copyVerifiedApk(input.inputStream(), ByteArrayOutputStream(), release) {}
}
