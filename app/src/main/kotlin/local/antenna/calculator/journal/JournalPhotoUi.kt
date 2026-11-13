package local.antenna.calculator.journal

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.PhotoAttachment
import local.antenna.calculator.core.journal.PhotoFormat
import local.antenna.calculator.ui.AppAlertDialog

internal class JournalPhotoActions(
    val add: () -> Unit,
    val exportAll: () -> Unit,
    val open: (PhotoAttachment) -> Unit,
    val file: (PhotoAttachment, Boolean) -> File,
)

@Composable
internal fun rememberJournalPhotoActions(
    model: JournalViewModel,
    project: AntennaProject?,
    page: JournalPage,
    open: (UUID) -> Unit,
    onMessage: (String) -> Unit,
    state: JournalUiState,
): JournalPhotoActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    var importProject by rememberSaveable { mutableStateOf<String?>(null) }
    var exportConfirmation by rememberSaveable { mutableStateOf<String?>(null) }
    fun accept(uris: List<Uri>) {
        val id = importProject?.let(UUID::fromString)
        importProject = null
        if (id != null) model.importPhotos(id, context.contentResolver, uris)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PHOTO_BATCH_LIMIT), ::accept)
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::accept)
    val archive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        finishPhotoExport(model, context, it, onMessage)
    }

    LaunchedEffect(state.photoImport) {
        val result = state.photoImport ?: return@LaunchedEffect
        val message = buildList {
            add(resources.getString(R.string.photo_added, result.added))
            if (result.cancelled) add(resources.getString(R.string.photo_cancelled))
            result.problems.forEach { (problem, count) -> add(resources.getString(problem.label(), count)) }
        }.joinToString("\n")
        model.clearPhotoImport()
        onMessage(message)
        if (result.photoId != null && result.projectId == project?.id && page == JournalPage.HISTORY) open(result.photoId)
    }

    state.photoProgress?.let { (done, total) ->
        AppAlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.photo_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.photo_import_progress, done, total))
                }
            },
            confirmButton = { TextButton(onClick = model::cancelPhotoImport) { Text(stringResource(R.string.journal_cancel)) } },
        )
    }

    exportConfirmation?.let { id ->
        AppAlertDialog(
            onDismissRequest = { exportConfirmation = null },
            title = { Text(stringResource(R.string.photo_export_all)) },
            text = { Text(stringResource(R.string.photo_archive_help)) },
            confirmButton = {
                TextButton(onClick = {
                    exportConfirmation = null
                    model.preparePhotoExport(UUID.fromString(id), null) {
                        launchPhotoPicker(model) { archive.launch("antcalc-photos.zip") }
                    }
                }) { Text(stringResource(R.string.photo_choose_location)) }
            },
            dismissButton = { TextButton(onClick = { exportConfirmation = null }) { Text(stringResource(R.string.journal_cancel)) } },
        )
    }
    return JournalPhotoActions(
        add = {
            project?.let {
                importProject = it.id.toString()
                try {
                    if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else documents.launch(arrayOf("image/jpeg", "image/png"))
                } catch (_: ActivityNotFoundException) {
                    importProject = null
                    model.filePickerUnavailable()
                } catch (_: SecurityException) {
                    importProject = null
                    model.filePickerUnavailable()
                }
            }
        },
        exportAll = { exportConfirmation = project?.id?.toString() },
        open = { open(it.id) },
        file = model::photoFile,
    )
}

internal fun LazyListScope.journalPhotos(
    project: AntennaProject,
    presentation: JournalPresentation,
    actions: JournalPhotoActions,
    enabled: Boolean,
) {
    item(key = "photos-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("journal-photos")) {
            JournalHeading(stringResource(R.string.photo_section), MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.photo_count, project.photos.size, PhotoAttachment.MAX_PER_PROJECT),
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.add, enabled = enabled && project.photos.size < PhotoAttachment.MAX_PER_PROJECT,
                    modifier = Modifier.testTag("journal-photo-add")) { Text(stringResource(R.string.photo_add)) }
                OutlinedButton(onClick = actions.exportAll, enabled = enabled && project.photos.isNotEmpty(),
                    modifier = Modifier.testTag("journal-photo-export-all")) { Text(stringResource(R.string.photo_export_all)) }
            }
            Text(stringResource(R.string.photo_formats), style = MaterialTheme.typography.bodySmall)
            if (project.photos.isEmpty()) Text(stringResource(R.string.photo_empty))
        }
    }
    items(project.photos.asReversed(), key = { "photo-${it.id}" }) { photo ->
        OutlinedCard(onClick = { actions.open(photo) }, modifier = Modifier.fillMaxWidth().testTag("journal-photo-card")) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                val stacked = maxWidth < 240.dp || LocalDensity.current.fontScale >= 1.6f
                val details: @Composable () -> Unit = {
                    Text(presentation.date(photo.addedAt), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary)
                    Text(photo.comment.ifBlank { stringResource(R.string.photo_no_comment) },
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoredPhoto(actions.file(photo, true), null, Modifier.fillMaxWidth().height(84.dp))
                        details()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StoredPhoto(actions.file(photo, true), null, Modifier.width(76.dp).height(66.dp))
                        Column(Modifier.weight(1f)) { details() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun JournalPhotoDetail(
    project: AntennaProject, photo: PhotoAttachment, model: JournalViewModel, presentation: JournalPresentation,
    enabled: Boolean, onBack: () -> Unit, onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var editing by rememberSaveable(photo.id) { mutableStateOf(false) }
    val draft = key(photo.id) { rememberTextFieldState(photo.comment) }
    var discard by rememberSaveable(photo.id) { mutableStateOf(false) }
    var delete by rememberSaveable(photo.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable(photo.id) { mutableStateOf(false) }
    val file = model.photoFile(photo, false)
    val jpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
        finishPhotoExport(model, context, it, onMessage)
    }
    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        finishPhotoExport(model, context, it, onMessage)
    }

    JournalColumn {
        val date = presentation.date(photo.addedAt)
        StoredPhoto(file, stringResource(R.string.photo_description, date),
            Modifier.fillMaxWidth().height(260.dp),
            onOpen = { fullscreen = true },
            fullscreen = fullscreen,
            onClose = { fullscreen = false })
        Text(date, style = MaterialTheme.typography.bodySmall)
        JournalHeading(stringResource(R.string.photo_comment), MaterialTheme.colorScheme.secondary)
        Text(photo.comment.ifBlank { stringResource(R.string.photo_no_comment) }, Modifier.testTag("journal-photo-comment"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                draft.edit { replace(0, length, photo.comment) }
                editing = true
            }, enabled = enabled,
                modifier = Modifier.testTag("journal-photo-edit")) { Text(stringResource(R.string.photo_comment)) }
            Box {
                OutlinedButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.testTag("journal-photo-actions")) {
                    Text(stringResource(R.string.photo_actions))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.photo_export_one)) }, onClick = {
                        menu = false
                        model.preparePhotoExport(project.id, photo.id) {
                            launchPhotoPicker(model) {
                                val launcher = if (photo.format == PhotoFormat.JPEG) jpeg else png
                                launcher.launch("antcalc-photo.${photo.format.extension}")
                            }
                        }
                    }, modifier = Modifier.testTag("journal-photo-export-one"))
                    DropdownMenuItem(text = { Text(stringResource(R.string.photo_delete)) }, onClick = {
                        menu = false; delete = true
                    }, modifier = Modifier.testTag("journal-photo-delete"))
                }
            }
        }
    }

    fun closeEditor() { if (draft.text.toString() != photo.comment) discard = true else editing = false }
    if (editing && !discard) AppAlertDialog(
        onDismissRequest = ::closeEditor,
        title = { Text(stringResource(R.string.photo_edit_comment)) },
        text = {
            OutlinedTextField(state = draft, label = { Text(stringResource(R.string.photo_comment)) },
                labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                inputTransformation = InputTransformation.maxLength(PhotoAttachment.MAX_COMMENT),
                supportingText = { Text(stringResource(R.string.photo_count, draft.text.length, PhotoAttachment.MAX_COMMENT)) },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6),
                modifier = Modifier.fillMaxWidth().testTag("journal-photo-comment-input"))
        },
        confirmButton = {
            TextButton(onClick = { model.updatePhotoComment(project.id, photo.id, draft.text.toString()) { editing = false } },
                enabled = enabled, modifier = Modifier.testTag("journal-photo-comment-save")) {
                Text(stringResource(R.string.journal_save))
            }
        },
        dismissButton = { TextButton(onClick = ::closeEditor) { Text(stringResource(R.string.journal_cancel)) } },
    )
    if (discard) AppAlertDialog(
        onDismissRequest = { discard = false },
        title = { Text(stringResource(R.string.photo_discard)) },
        confirmButton = { TextButton(onClick = { discard = false; editing = false }) { Text(stringResource(R.string.photo_discard_action)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.journal_cancel)) } },
    )
    if (delete) AppAlertDialog(
        onDismissRequest = { delete = false },
        title = { Text(stringResource(R.string.photo_delete_question)) },
        text = { Text(stringResource(R.string.photo_delete_help)) },
        confirmButton = {
            TextButton(onClick = { model.removePhoto(project.id, photo.id) { delete = false; onBack() } },
                enabled = enabled, modifier = Modifier.testTag("journal-photo-confirm-delete")) {
                Text(stringResource(R.string.journal_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(stringResource(R.string.journal_cancel)) } },
    )
}

@Composable
private fun StoredPhoto(
    file: File, description: String?, modifier: Modifier, onOpen: (() -> Unit)? = null,
    fullscreen: Boolean = false, onClose: () -> Unit = {},
) {
    var ready by remember(file) { mutableStateOf(false) }
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
        ready = true
    }
    val image = bitmap
    if (image == null) Box(modifier, contentAlignment = Alignment.Center) {
        if (ready) Text(stringResource(R.string.photo_unavailable), style = MaterialTheme.typography.bodySmall)
    } else {
        val preview = if (onOpen == null) modifier else modifier
            .testTag("journal-photo-preview")
            .clickable(onClickLabel = stringResource(R.string.photo_fullscreen), onClick = onOpen)
        Image(image, description, preview, contentScale = ContentScale.Fit)
        if (fullscreen) FullscreenJournalPhoto(image, description, onClose)
    }
}

private fun launchPhotoPicker(model: JournalViewModel, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        model.cancelPhotoExport()
        model.filePickerUnavailable()
    } catch (_: SecurityException) {
        model.cancelPhotoExport()
        model.filePickerUnavailable()
    }
}

private fun finishPhotoExport(model: JournalViewModel, context: android.content.Context, uri: Uri?, onMessage: (String) -> Unit) {
    if (uri == null) model.cancelPhotoExport()
    else model.writePhotoExport(context.contentResolver, uri) { success, incomplete ->
        onMessage(context.getString(when {
            success -> R.string.photo_exported
            incomplete -> R.string.photo_export_incomplete
            else -> R.string.photo_export_failed
        }))
    }
}

private fun PhotoImportProblem.label(): Int = when (this) {
    PhotoImportProblem.FORMAT -> R.string.photo_problem_format
    PhotoImportProblem.DAMAGED -> R.string.photo_problem_damaged
    PhotoImportProblem.LIMIT -> R.string.photo_problem_limit
    PhotoImportProblem.READ -> R.string.photo_problem_read
    PhotoImportProblem.WRITE -> R.string.photo_problem_write
}
