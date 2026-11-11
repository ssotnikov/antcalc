package local.antenna.calculator.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import local.antenna.calculator.BuildConfig
import local.antenna.calculator.R
import local.antenna.calculator.ui.GITHUB_RELEASES_URL
import local.antenna.calculator.ui.UpdateSnackbarVisuals
import local.antenna.calculator.ui.openSourceWithFeedback

internal val LocalDirectUpdateViewModel = staticCompositionLocalOf<DirectUpdateViewModel?> { null }

@Composable
internal fun rememberUpdateMenuActions(
    allowOffer: Boolean, allowAutomaticOffer: Boolean, snackbar: SnackbarHostState,
): List<UpdateMenuAction> {
    val activity = LocalActivity.current as? ComponentActivity ?: return emptyList()
    val suppliedModel = LocalDirectUpdateViewModel.current
    val model = suppliedModel ?: remember(activity) {
        val factory = viewModelFactory { initializer { DirectUpdateViewModel(activity.applicationContext) } }
        ViewModelProvider(activity, factory)[DirectUpdateViewModel::class.java]
    }
    val lifecycle by activity.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycle == Lifecycle.State.RESUMED
    val state by model.state.collectAsState()
    LaunchedEffect(model, resumed) { if (resumed) model.start() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.permissionReturned()
    }

    val notice = when (val current = state) {
        UpdateUiState.Checking -> stringResource(R.string.update_checking)
        is UpdateUiState.Message -> when (current.result) {
            UpdateCheck.Current -> stringResource(R.string.update_current)
            is UpdateCheck.RateLimited -> stringResource(R.string.update_rate_limited)
            else -> stringResource(R.string.update_unavailable)
        }
        is UpdateUiState.InstallFailed -> stringResource(
            if (current.invalidApk) R.string.update_invalid_apk else R.string.update_install_failed,
        )
        else -> null
    }
    LaunchedEffect(state, notice) {
        val current = state
        if (notice != null) {
            val duration = if (current == UpdateUiState.Checking) SnackbarDuration.Indefinite else SnackbarDuration.Short
            snackbar.showSnackbar(UpdateSnackbarVisuals(notice, duration))
            if (current is UpdateUiState.Message || current is UpdateUiState.InstallFailed) model.messageShown(current)
        }
    }

    val active = resumed && allowOffer
    val context = LocalContext.current
    when (val current = state) {
        is UpdateUiState.Offer -> if (active && (current.manual || allowAutomaticOffer)) {
            UpdateOfferDialog(current.release, onDismiss = { model.dismiss(current) }, onDownload = { model.download(current) })
        }
        is UpdateUiState.Permission -> if (active) {
            AlertDialog(
                onDismissRequest = { model.cancelInstall(current.release) },
                title = { Text(stringResource(R.string.update_permission_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.update_permission_message))
                        TextButton(onClick = { openSourceWithFeedback(context, current.release.pageUrl) }) {
                            Text(stringResource(R.string.update_open_release))
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.fromParts("package", activity.packageName, null),
                        )
                        try {
                            permissions.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            model.permissionUnavailable()
                        } catch (_: SecurityException) {
                            model.permissionUnavailable()
                        }
                    }) { Text(stringResource(R.string.update_open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { model.cancelInstall(current.release) }) { Text(stringResource(R.string.update_cancel)) }
                },
            )
        }
        is UpdateUiState.Downloading -> if (active) {
            AlertDialog(
                onDismissRequest = { model.cancelInstall(current.release) },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {
                    val progress = (current.bytes.toFloat() / current.release.size).coerceIn(0f, 1f)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f),
                            drawStopIndicator = {},
                        )
                        Text(stringResource(R.string.update_download_percent, (progress * 100).toInt()))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { model.cancelInstall(current.release) }) { Text(stringResource(R.string.update_cancel)) }
                },
            )
        }
        is UpdateUiState.DownloadFailed -> if (active) {
            val dismiss = { model.messageShown(current) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.update_download_failed)) },
                text = { Text(downloadErrorMessage(current.reason)) },
                confirmButton = {
                    TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) }
                },
            )
        }
        is UpdateUiState.Confirmation -> LaunchedEffect(current, active) {
            if (active) model.takeConfirmation(current)?.let { intent ->
                try {
                    activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    model.confirmationUnavailable()
                } catch (_: SecurityException) {
                    model.confirmationUnavailable()
                }
            }
        }
        else -> Unit
    }
    val enabled = state == UpdateUiState.Idle || state is UpdateUiState.Offer || state is UpdateUiState.Message
    return listOf(
        UpdateMenuAction("check-for-updates", stringResource(R.string.update_check), enabled, model::checkManually),
        UpdateMenuAction("open-github-releases", stringResource(R.string.update_open_releases)) {
            openSourceWithFeedback(context, GITHUB_RELEASES_URL)
        },
    )
}

@Composable
private fun downloadErrorMessage(reason: UpdateDownloadError): String = when (reason) {
    is UpdateDownloadError.Http -> stringResource(R.string.update_error_http, reason.status)
    UpdateDownloadError.Timeout -> stringResource(R.string.update_error_timeout)
    UpdateDownloadError.Connection -> stringResource(R.string.update_error_connection)
    UpdateDownloadError.Incomplete -> stringResource(R.string.update_error_incomplete)
    UpdateDownloadError.Integrity -> stringResource(R.string.update_invalid_apk)
    UpdateDownloadError.FileAccess -> stringResource(R.string.update_error_file)
}

@Composable
private fun UpdateOfferDialog(release: UpdateRelease, onDismiss: () -> Unit, onDownload: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val notes = release.notes?.text(language) ?: stringResource(R.string.update_notes_unavailable)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("update-offer"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.update_available),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.update_versions, BuildConfig.VERSION_NAME, release.versionName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.update_whats_new),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(notes, modifier = Modifier.testTag("update-notes"), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onDismiss, Modifier.weight(1f).testTag("update-cancel")) {
                        Text(stringResource(R.string.update_skip))
                    }
                    Button(onDownload, Modifier.weight(1f).testTag("update-download")) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
        }
    }
}
