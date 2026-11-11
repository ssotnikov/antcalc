package local.antenna.calculator.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.IOException
import android.content.SharedPreferences
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import local.antenna.calculator.BuildConfig
import org.json.JSONException

internal sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Offer(val release: UpdateRelease, val manual: Boolean) : UpdateUiState
    data class Message(val result: UpdateCheck) : UpdateUiState
    data class Permission(val release: UpdateRelease) : UpdateUiState
    data class Downloading(val release: UpdateRelease, val bytes: Long) : UpdateUiState
    data class Confirmation(val release: UpdateRelease, val intent: Intent) : UpdateUiState
    data object Installing : UpdateUiState
    data class DownloadFailed(val reason: UpdateDownloadError) : UpdateUiState
    data class InstallFailed(val invalidApk: Boolean) : UpdateUiState
}

internal class UpdateDismissals(private val prefs: SharedPreferences) {
    private val key = "dismissed_version_code"

    fun allows(version: Long) = version > prefs.getLong(key, 0)

    fun dismiss(version: Long) {
        if (allows(version)) prefs.edit().putLong(key, version).commit()
    }
}

internal class DirectUpdateViewModel(
    context: Context,
    private val dismissals: UpdateDismissals = UpdateDismissals(
        context.getSharedPreferences("github_direct_updates", Context.MODE_PRIVATE),
    ),
    private val testCheck: (suspend (Boolean) -> UpdateCheck)? = null,
    private val transport: UpdateTransport = GithubUpdateTransport(),
) : ViewModel() {
    private val app = context.applicationContext
    private val cache = File(app.cacheDir, "github-updates")
    private val checks by lazy {
        UpdateChecks.create(app, CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO))
    }
    private var recovery: Job? = null
    private var installation: Job? = null
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state = mutableState.asStateFlow()
    private var started = false
    private var requestId = 0
    private var dismissing = false

    fun start() {
        if (started) return
        started = true
        if (testCheck != null) {
            if (requestId == 0) check(manual = false)
            return
        }
        recovery = viewModelScope.launch {
            val interrupted = withContext(Dispatchers.IO) {
                val partialApk = cache.list()?.any { it.startsWith("update-") && it.endsWith(".apk") } == true
                UpdateInstallSessions.recover(app, cache)
                partialApk
            }
            if (requestId == 0 && mutableState.value == UpdateUiState.Idle) {
                if (interrupted) mutableState.value = UpdateUiState.DownloadFailed(UpdateDownloadError.Incomplete)
                else check(manual = false)
            }
        }
    }

    fun checkManually() {
        if (canCheck()) check(manual = true)
    }

    private fun canCheck() = !dismissing && installation?.isActive != true &&
        mutableState.value !is UpdateUiState.Permission && mutableState.value != UpdateUiState.Checking

    fun download(offer: UpdateUiState.Offer) {
        if (mutableState.value != offer || !canCheck()) return
        if (app.packageManager.canRequestPackageInstalls()) install(offer.release)
        else mutableState.value = UpdateUiState.Permission(offer.release)
    }

    fun permissionUnavailable() {
        if (mutableState.value is UpdateUiState.Permission) mutableState.value = UpdateUiState.InstallFailed(false)
    }

    fun permissionReturned() {
        val permission = mutableState.value as? UpdateUiState.Permission ?: return
        if (app.packageManager.canRequestPackageInstalls()) install(permission.release)
        else cancelInstall(permission.release)
    }

    fun cancelInstall(release: UpdateRelease) {
        if (dismissing) return
        dismissing = true
        val job = installation
        job?.cancel()
        viewModelScope.launch {
            try {
                job?.join()
                withContext(Dispatchers.IO) { dismissals.dismiss(release.versionCode) }
                mutableState.value = UpdateUiState.Idle
            } finally {
                dismissing = false
            }
        }
    }

    fun takeConfirmation(confirmation: UpdateUiState.Confirmation): Intent? {
        if (mutableState.value != confirmation) return null
        mutableState.value = UpdateUiState.Installing
        return confirmation.intent
    }

    fun confirmationUnavailable() {
        if (mutableState.value != UpdateUiState.Installing) return
        installation?.cancel()
        mutableState.value = UpdateUiState.InstallFailed(false)
    }

    private fun install(release: UpdateRelease) {
        if (installation?.isActive == true) return
        mutableState.value = UpdateUiState.Downloading(release, 0)
        installation = viewModelScope.launch {
            var session: Int? = null
            try {
                recovery?.join()
                withContext(Dispatchers.IO) {
                    val source = GithubReleases(
                        transport, BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT, System::currentTimeMillis,
                    )
                    source.confirm(release, apiAvailable = checks.apiAvailable())
                    val file = try {
                        if (!cache.isDirectory && !cache.mkdirs()) throw IOException("Could not create update cache")
                        File.createTempFile("update-", ".apk", cache)
                    } catch (error: IOException) {
                        throw UpdateDownloadException(UpdateDownloadError.FileAccess, error)
                    }
                    try {
                        downloadUpdate(release, file) { bytes ->
                            mutableState.value = UpdateUiState.Downloading(release, bytes)
                        }
                        verifyUpdateArchive(app, file, release)
                        ensureActive()
                        session = UpdateInstallSessions.create(app, file)
                    } finally {
                        file.delete()
                    }
                }
                val id = checkNotNull(session)
                withTimeout(10 * 60_000L) {
                    val first = UpdateInstallSessions.status.first { it?.session == id } ?: error("Missing install event")
                    if (first.status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val intent = first.confirmation ?: throw IOException("No installer confirmation")
                        mutableState.value = UpdateUiState.Confirmation(release, intent)
                    }
                    val finished = UpdateInstallSessions.status.first {
                        it?.session == id && it.status != PackageInstaller.STATUS_PENDING_USER_ACTION
                    } ?: error("Missing install result")
                    when (finished.status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            val installed = withContext(Dispatchers.IO) { installedVersion() }
                            mutableState.value = if (installed >= release.versionCode) UpdateUiState.Idle
                                else UpdateUiState.InstallFailed(false)
                        }
                        PackageInstaller.STATUS_FAILURE_ABORTED -> {
                            withContext(Dispatchers.IO) { dismissals.dismiss(release.versionCode) }
                            mutableState.value = UpdateUiState.Idle
                        }
                        else -> mutableState.value = UpdateUiState.InstallFailed(false)
                    }
                }
            } catch (error: UpdateDownloadException) {
                mutableState.value = UpdateUiState.DownloadFailed(error.reason)
            } catch (_: InvalidUpdateApk) {
                mutableState.value = UpdateUiState.InstallFailed(true)
            } catch (_: TimeoutCancellationException) {
                mutableState.value = UpdateUiState.InstallFailed(false)
            } catch (_: IOException) {
                mutableState.value = UpdateUiState.InstallFailed(false)
            } catch (_: JSONException) {
                mutableState.value = UpdateUiState.InstallFailed(false)
            } catch (_: SecurityException) {
                mutableState.value = UpdateUiState.InstallFailed(false)
            } catch (_: IllegalStateException) {
                mutableState.value = UpdateUiState.InstallFailed(false)
            } finally {
                session?.let { id ->
                    withContext(NonCancellable + Dispatchers.IO) { UpdateInstallSessions.abandon(app, id) }
                }
            }
        }
    }

    private fun check(manual: Boolean) {
        val id = ++requestId
        if (manual) mutableState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val result = testCheck?.invoke(manual) ?: checks.check(manual)
            val canOffer = result is UpdateCheck.Available &&
                (manual || withContext(Dispatchers.IO) { dismissals.allows(result.release.versionCode) })
            if (id != requestId) return@launch
            mutableState.value = when {
                canOffer -> UpdateUiState.Offer(result.release, manual)
                manual -> UpdateUiState.Message(result)
                else -> UpdateUiState.Idle
            }
        }
    }

    fun dismiss(offer: UpdateUiState.Offer) {
        if (mutableState.value != offer || dismissing) return
        dismissing = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { dismissals.dismiss(offer.release.versionCode) }
                if (mutableState.value == offer) mutableState.value = UpdateUiState.Idle
            } finally {
                dismissing = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersion() =
        PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))

    fun messageShown(message: UpdateUiState) {
        if (mutableState.value == message) mutableState.value = UpdateUiState.Idle
    }
}
