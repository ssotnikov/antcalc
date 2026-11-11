package local.antenna.calculator.update

import android.content.pm.PackageInstaller
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class UpdateInstallerTest {
    @Test fun recoveryDeletesThePartialApkAndAbandonsTheInterruptedSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = context.packageManager.packageInstaller
        val cache = Files.createTempDirectory(context.cacheDir.toPath(), "update-recovery-").toFile()
        val partial = File(cache, "update-interrupted.apk").apply { writeText("partial download") }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        try {
            val id = installer.createSession(params)
            try {
                UpdateInstallSessions.recover(context, cache)
                assertFalse(partial.exists())
                assertNull(installer.getSessionInfo(id))
            } finally {
                UpdateInstallSessions.abandon(context, id)
            }
        } finally {
            partial.delete()
            cache.delete()
        }
    }

    @Test fun androidRequiresConfirmationAndCancellingReleasesTheSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        assumeTrue("Enable installation from this source on the test device before running this test",
            context.packageManager.canRequestPackageInstalls())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val apk = File(context.applicationInfo.sourceDir)
            val id = UpdateInstallSessions.create(context, apk)
            try {
                val first = withTimeout(15_000) { UpdateInstallSessions.status.first { it?.session == id } }!!
                assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, first.status)
                assertNotNull(first.confirmation)
                val confirmation = checkNotNull(first.confirmation)
                val installerPackage = checkNotNull(confirmation.component?.packageName ?: confirmation.`package`)
                scenario.onActivity { it.startActivity(confirmation) }
                val deadline = SystemClock.elapsedRealtime() + 10_000
                fun installerVisible(): Boolean {
                    val windows = ParcelFileDescriptor.AutoCloseInputStream(
                        automation.executeShellCommand("dumpsys window"),
                    ).bufferedReader().use { it.readText() }
                    return windows.lineSequence().any { "mCurrentFocus=" in it && "$installerPackage/" in it }
                }
                while (!installerVisible() && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(100)
                }
                assertTrue("The installer window must receive focus", installerVisible())
                val cancelled = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Cancel")
                    ?.any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
                if (!cancelled) {
                    ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4"))
                        .use { it.readBytes() }
                }
                val final = withTimeout(15_000) {
                    UpdateInstallSessions.status.first { it?.session == id && it.status != PackageInstaller.STATUS_PENDING_USER_ACTION }
                }!!
                assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, final.status)
            } finally {
                UpdateInstallSessions.abandon(context, id)
            }
            assertNull(context.packageManager.packageInstaller.getSessionInfo(id))
        }
    }
}
