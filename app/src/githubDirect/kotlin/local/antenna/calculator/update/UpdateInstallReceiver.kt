package local.antenna.calculator.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import local.antenna.calculator.BuildConfig

private const val INSTALL_STATUS = "local.antenna.calculator.UPDATE_INSTALL_STATUS"

internal data class InstallEvent(val session: Int, val status: Int, val confirmation: Intent?)

internal object UpdateInstallSessions {
    private val events = MutableStateFlow<InstallEvent?>(null)
    val status = events.asStateFlow()
    private val activeSession = AtomicInteger(-1)

    fun create(context: Context, apk: File): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            activeSession.set(id)
            val result = PendingIntent.getBroadcast(
                context, id, statusIntent(context, id), PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag(),
            )
            installer.openSession(id).use { session ->
                session.openWrite("antcalc.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(result.intentSender)
            }
            return id
        } catch (error: Exception) {
            abandon(context, id)
            throw error
        }
    }

    fun abandon(context: Context, id: Int) {
        val installer = context.packageManager.packageInstaller
        runCatching { if (installer.getSessionInfo(id) != null) installer.abandonSession(id) }
        PendingIntent.getBroadcast(context, id, statusIntent(context, id), PendingIntent.FLAG_NO_CREATE or mutableFlag())
            ?.cancel()
        activeSession.compareAndSet(id, -1)
        events.compareAndSet(events.value?.takeIf { it.session == id }, null)
    }

    private fun statusIntent(context: Context, id: Int) =
        Intent(context, UpdateInstallReceiver::class.java).setAction(INSTALL_STATUS)
            .setData(Uri.parse("antcalc-update://session/$id"))

    private fun mutableFlag() = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    fun recover(context: Context, cache: File) {
        cache.listFiles()?.forEach(File::delete)
        context.packageManager.packageInstaller.mySessions.filter { it.appPackageName == BuildConfig.APPLICATION_ID }
            .forEach { abandon(context, it.sessionId) }
    }

    fun receive(intent: Intent) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (id < 0 || id != activeSession.get() || intent.action != INSTALL_STATUS ||
            intent.dataString != "antcalc-update://session/$id" ||
            !intent.hasExtra(PackageInstaller.EXTRA_STATUS)) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirmation = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else legacyConfirmation(intent)
        } else null
        events.value = InstallEvent(id, status, confirmation)
    }

    @Suppress("DEPRECATION")
    private fun legacyConfirmation(intent: Intent): Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdateInstallSessions.receive(intent)
    }
}
