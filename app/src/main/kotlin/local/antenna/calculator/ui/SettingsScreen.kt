package local.antenna.calculator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import local.antenna.calculator.BuildConfig
import local.antenna.calculator.R
import local.antenna.calculator.state.*

@Composable
internal fun SettingsScreen(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit, onAbout: () -> Unit) {
    val settings = state.draft.settings
    val context = LocalContext.current
    val website = stringResource(R.string.author_website_url)
    val bannerImage = when (settings.language) {
        AppLanguage.RUSSIAN -> R.drawable.lizaalert_banner_ru
        AppLanguage.ENGLISH -> R.drawable.lizaalert_banner_en
    }
    BoxWithConstraints(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
        val viewportHeight = maxHeight
        val bannerLift = with(LocalDensity.current) { 20.toDp() }
        val bottomGap = (viewportHeight * 0.025f).coerceIn(12.dp, 24.dp) + bannerLift
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("settings-scroll")
                .heightIn(min = viewportHeight)
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottomGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChoicePicker(stringResource(R.string.language), AppLanguage.entries, settings.language, {
                    stringResource(if (it == AppLanguage.RUSSIAN) R.string.language_russian else R.string.language_english)
                }, { onEvent(CalculatorEvent.Settings(settings.copy(language = it))) }, "language")
                ChoicePicker(stringResource(R.string.theme), AppTheme.entries, settings.theme, {
                    stringResource(when (it) { AppTheme.SYSTEM -> R.string.theme_system; AppTheme.LIGHT -> R.string.theme_light; AppTheme.DARK -> R.string.theme_dark })
                }, { onEvent(CalculatorEvent.Settings(settings.copy(theme = it))) }, "theme")
                StorageNotice(state, onEvent)
                OutlinedButton(onClick = onAbout) { Text(stringResource(R.string.about)) }
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge)
                HorizontalDivider()
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.license_mit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openSourceWithFeedback(context, website) }, modifier = Modifier.testTag("source-author")) {
                        Text(stringResource(R.string.author_website_label))
                    }
                    TextButton(onClick = { openSourceWithFeedback(context, BUG_REPORT_URL) }, modifier = Modifier.testTag("report-bug")) {
                        Text(stringResource(R.string.report_bug))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(bannerImage),
                contentDescription = stringResource(R.string.lizaalert_banner_description),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(1280f / 427f)
                    .clip(MaterialTheme.shapes.small)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        // A horizontal drag across the image must cancel the link press.
                        detectHorizontalDragGestures { _, _ -> }
                    }
                    .clickable(role = Role.Button) { openSourceWithFeedback(context, LIZAALERT_URL) }
                    .testTag("lizaalert-banner"),
            )
        }
    }
}

@Composable
internal fun StorageNotice(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit) {
    val issue = state.storageIssue ?: return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(when (issue) {
                StorageIssue.WRITE_FAILED -> R.string.settings_write_failed
                StorageIssue.READ_FAILED -> R.string.settings_read_failed
                StorageIssue.CORRUPT -> R.string.settings_corrupt
                StorageIssue.UNSUPPORTED_SCHEMA -> R.string.settings_unsupported
            }))
            TextButton(onClick = { onEvent(CalculatorEvent.RetryStorage) }) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = { onEvent(CalculatorEvent.ResetStorage) }) { Text(stringResource(R.string.reset_settings)) }
        }
    }
}
