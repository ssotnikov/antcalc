package local.antenna.calculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch
import local.antenna.calculator.R
import local.antenna.calculator.update.rememberUpdateMenuActions
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalPage
import local.antenna.calculator.journal.JournalScreen
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.journal.invertedVSnapshot
import local.antenna.calculator.journal.groundPlaneSnapshot
import local.antenna.calculator.state.*

// Keep ABOUT as the persisted route identifier for Help.
private enum class Page { CALCULATOR, MATCHING, SMITH_CHART, CABLE, INVERTED_V, GROUND_PLANE, TRANSFER_TYPE, TRANSFER_PICKER, TRANSFER_NEW, SETTINGS, ABOUT, REFERENCES, JOURNAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalculatorApp(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    journal: JournalViewModel? = null,
    currentState: () -> CalculatorUiState,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101D26)))
        return
    }
    LocalizedContent(state.draft.settings.language) {
        LocalizedCalculatorApp(state, onEvent, journal, currentState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalizedCalculatorApp(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    journal: JournalViewModel?,
    currentState: () -> CalculatorUiState,
) {
    var page by rememberSaveable { mutableStateOf(Page.CALCULATOR) }
    var referencesBackPage by rememberSaveable { mutableStateOf(Page.CALCULATOR) }
    var menu by remember { mutableStateOf(false) }
    var journalPage by rememberSaveable { mutableStateOf(JournalPage.PROJECTS) }
    val screenStates = rememberSaveableStateHolder()
    var pending by rememberSaveable(stateSaver = PendingProjectCalculation.saver) { mutableStateOf<PendingProjectCalculation?>(null) }
    var transferType by rememberSaveable { mutableStateOf(AntennaType.SYMMETRIC_DIPOLE) }
    var linkedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = state.draft.settings
    val journalStatus = journal?.state?.collectAsState()?.value
    val copiedMessage = stringResource(R.string.copied)
    val transferSavedMessage = stringResource(R.string.project_transfer_saved)
    val openProjectLabel = stringResource(R.string.project_transfer_open)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun leaveInput() { focus.clearFocus(); keyboard?.hide() }
    fun finishTransfer() {
        page = when {
            pending?.isInvertedV == true -> Page.INVERTED_V
            pending?.isGroundPlane == true -> Page.GROUND_PLANE
            else -> Page.CALCULATOR
        }
        pending?.let {
            screenStates.removeState("transfer-picker-${it.recordId}")
            screenStates.removeState("transfer-new-${it.recordId}")
        }
        pending = null
    }
    fun goBack() {
        if ((page == Page.TRANSFER_PICKER || page == Page.TRANSFER_NEW) && journalStatus?.busy == true) return
        leaveInput()
        when (page) {
            Page.REFERENCES -> page = referencesBackPage
            Page.TRANSFER_NEW -> page = Page.TRANSFER_PICKER
            Page.TRANSFER_PICKER -> if (pending?.needsAntennaChoice == true) page = Page.TRANSFER_TYPE else finishTransfer()
            Page.TRANSFER_TYPE -> finishTransfer()
            Page.JOURNAL -> journalPage.parent()?.let { journalPage = it } ?: run { page = Page.CALCULATOR }
            else -> page = Page.CALCULATOR
        }
    }
    fun startTransfer(calculation: PendingProjectCalculation, type: AntennaType) {
        snackbar.currentSnackbarData?.dismiss()
        leaveInput()
        transferType = type
        pending = calculation
        page = if (calculation.needsAntennaChoice) Page.TRANSFER_TYPE else Page.TRANSFER_PICKER
    }
    fun openTransfer() {
        val current = currentState()
        val section = current.draft.settings.section
        val type = current.trimming.result?.input?.antennaType
        if (section == Section.TRIMMING && type == null) return
        val antennaType = if (section == Section.TRIMMING) checkNotNull(type) else AntennaType.SYMMETRIC_DIPOLE
        val calculation = PendingProjectCalculation.capture(current, section, antennaType) ?: return
        startTransfer(calculation, antennaType)
    }
    fun openInvertedVTransfer() {
        val current = currentState()
        if (!current.editingEnabled) return
        val snapshot = current.draft.invertedV.invertedVSnapshot() ?: return
        startTransfer(PendingProjectCalculation(snapshot), AntennaType.SYMMETRIC_DIPOLE)
    }
    fun openGroundPlaneTransfer() {
        val current = currentState()
        if (!current.editingEnabled) return
        val snapshot = current.draft.groundPlane.groundPlaneSnapshot() ?: return
        startTransfer(PendingProjectCalculation(snapshot), AntennaType.MONOPOLE)
    }
    fun transferSaved(calculation: PendingProjectCalculation, projectId: UUID) {
        if (pending?.recordId != calculation.recordId) return
        finishTransfer()
        scope.launch {
            val result = snackbar.showSnackbar(
                transferSavedMessage,
                actionLabel = openProjectLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                leaveInput()
                screenStates.removeState("linked-journal")
                linkedProjectId = projectId.toString()
                journalPage = JournalPage.HISTORY
                page = Page.JOURNAL
            }
        }
    }
    fun openReferences() {
        referencesBackPage = page
        page = Page.REFERENCES
    }
    val transferring = page == Page.TRANSFER_TYPE || page == Page.TRANSFER_PICKER || page == Page.TRANSFER_NEW
    LaunchedEffect(transferring, pending) {
        if (transferring && pending == null) finishTransfer()
    }
    val savedTransfer = pending?.let { draft ->
        journalStatus?.projects?.firstOrNull { project -> project.records.any { it.id == draft.recordId } }
    }
    LaunchedEffect(savedTransfer?.id, pending?.recordId) {
        val calculation = pending
        if (calculation != null && savedTransfer != null) transferSaved(calculation, savedTransfer.id)
    }
    BackHandler(page != Page.CALCULATOR, onBack = ::goBack)
    CalculatorTheme(settings.theme) {
        val offerAllowed = !menu && !transferring && WindowInsets.ime.getBottom(LocalDensity.current) == 0
        val updateActions = rememberUpdateMenuActions(offerAllowed, page == Page.CALCULATOR, snackbar)
        val title = when (page) {
            Page.SETTINGS -> R.string.settings
            Page.ABOUT -> R.string.about
            Page.REFERENCES -> R.string.references_title
            Page.MATCHING -> R.string.matching_title
            Page.SMITH_CHART -> R.string.smith_title
            Page.CABLE -> R.string.cable_screen_title
            Page.INVERTED_V -> R.string.inverted_v_title
            Page.GROUND_PLANE -> R.string.ground_plane_title
            Page.TRANSFER_TYPE -> R.string.project_transfer_choose_type
            Page.TRANSFER_PICKER -> R.string.project_transfer_title
            Page.TRANSFER_NEW -> R.string.journal_new_project
            Page.JOURNAL -> journalPage.title
            Page.CALCULATOR -> when (settings.section) {
                Section.SIZING -> R.string.sizes
                Section.TRIMMING -> R.string.trimming
                Section.TOOLS -> R.string.tools
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
                    expandedHeight = 64.dp * LocalDensity.current.fontScale,
                    navigationIcon = {
                        if (page != Page.CALCULATOR) IconButton(onClick = ::goBack,
                            modifier = Modifier.testTag("back")) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                    },
                    actions = {
                        if (!transferring) Box {
                            IconButton(onClick = {
                                leaveInput()
                                snackbar.currentSnackbarData?.dismiss()
                                menu = true
                            }, modifier = Modifier.testTag("menu")) { Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.menu)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; page = Page.SETTINGS }, modifier = Modifier.testTag("open-settings"))
                                DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menu = false; page = Page.ABOUT }, modifier = Modifier.testTag("open-about"))
                                DropdownMenuItem(text = { Text(stringResource(R.string.references_title)) }, onClick = {
                                    menu = false
                                    if (page != Page.REFERENCES) openReferences()
                                }, modifier = Modifier.testTag("open-references"))
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.contact_author)) },
                                    onClick = { menu = false; openAuthorContact(context) },
                                    modifier = Modifier.testTag("contact-author"),
                                )
                                for (action in updateActions) {
                                    DropdownMenuItem(
                                        text = { Text(action.title) },
                                        enabled = action.enabled,
                                        onClick = { menu = false; action.onClick() },
                                        modifier = Modifier.testTag(action.tag),
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.report_bug)) },
                                    onClick = { menu = false; openSourceWithFeedback(context, BUG_REPORT_URL) },
                                    modifier = Modifier.testTag("menu-report-bug"),
                                )
                            }
                        }
                    })
            },
            bottomBar = {
                if (page == Page.CALCULATOR || page == Page.MATCHING || page == Page.SMITH_CHART || page == Page.CABLE || page == Page.INVERTED_V || page == Page.GROUND_PLANE || page == Page.JOURNAL) NavigationBar(Modifier.heightIn(min =
                    80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) +
                        NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()).testTag("bottom-navigation")) {
                    for (section in Section.entries) {
                        val label = when (section) { Section.SIZING -> R.string.sizes; Section.TRIMMING -> R.string.trimming; Section.TOOLS -> R.string.tools }
                        NavigationBarItem(selected = settings.section == section, onClick = {
                            leaveInput(); page = Page.CALCULATOR; onEvent(CalculatorEvent.Settings(settings.copy(section = section)))
                        }, icon = {
                            val icon = when (section) {
                                Section.SIZING -> R.drawable.ic_wavelength
                                Section.TRIMMING -> R.drawable.ic_delta
                                Section.TOOLS -> R.drawable.ic_menu
                            }
                            Icon(
                                painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).rotate(if (section == Section.TOOLS) 90f else 0f),
                            )
                        },
                            label = { Text(stringResource(label)) }, modifier = Modifier.testTag("nav-${section.name}"))
                    }
                }
            },
            snackbarHost = { AppSnackbarHost(snackbar) },
        ) { padding ->
            val gestures = if (page == Page.CALCULATOR && settings.section != Section.TOOLS) Modifier.calculatorSwipes(settings.section) { section ->
                leaveInput()
                onEvent(CalculatorEvent.Settings(settings.copy(section = section)))
            } else Modifier
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).then(gestures)
                .testTag("page-content"), contentAlignment = Alignment.TopCenter) {
                when (page) {
                    Page.SETTINGS -> SettingsScreen(state, onEvent) { page = Page.ABOUT }
                    Page.ABOUT -> HelpScreen()
                    Page.REFERENCES -> ReferencesScreen(settings.language)
                    Page.MATCHING -> MatchingScreen(state, onEvent, currentState) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    Page.SMITH_CHART -> screenStates.SaveableStateProvider("smith-chart") {
                        SmithChartScreen { message -> scope.launch { snackbar.showSnackbar(message) } }
                    }
                    Page.CABLE -> CableScreen(state, onEvent, currentState) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    Page.INVERTED_V -> screenStates.SaveableStateProvider("inverted-v") {
                        val chooseProject = if (journal != null) ::openInvertedVTransfer else null
                        InvertedVScreen(state, onEvent, currentState, chooseProject) { message ->
                            scope.launch { snackbar.showSnackbar(message) }
                        }
                    }
                    Page.GROUND_PLANE -> screenStates.SaveableStateProvider("ground-plane") {
                        val chooseProject = if (journal != null) ::openGroundPlaneTransfer else null
                        GroundPlaneScreen(state, onEvent, currentState, chooseProject) {
                            scope.launch { snackbar.showSnackbar(copiedMessage) }
                        }
                    }
                    Page.TRANSFER_TYPE -> pending?.let { calculation ->
                        TransferAntennaType(calculation, transferType, { type ->
                            if (transferType != type) screenStates.removeState("transfer-picker-${calculation.recordId}")
                            transferType = type
                        }) { page = Page.TRANSFER_PICKER }
                    }
                    Page.TRANSFER_PICKER -> if (journal != null) pending?.let { calculation ->
                        screenStates.SaveableStateProvider("transfer-picker-${calculation.recordId}") {
                            val recordTitle = stringResource(calculation.title)
                            ProjectPicker(journal, transferType,
                                onSelect = { project ->
                                    leaveInput()
                                    journal.saveTransferredCalculation(project.id, calculation.record(transferType, recordTitle))
                                },
                                targetHertz = calculation.target?.hertz,
                                onCreate = { leaveInput(); page = Page.TRANSFER_NEW },
                                header = { TransferSummary(calculation, transferType) },
                            )
                        }
                    }
                    Page.TRANSFER_NEW -> if (journal != null) pending?.let { calculation ->
                        screenStates.SaveableStateProvider("transfer-new-${calculation.recordId}") {
                            val recordTitle = stringResource(calculation.title)
                            TransferNewProject(calculation, transferType, journal) { name ->
                                leaveInput()
                                val project = calculation.newProject(transferType, name)
                                journal.saveTransferredCalculation(project.id, calculation.record(transferType, recordTitle), project)
                            }
                        }
                    }
                    Page.JOURNAL -> if (journal != null) {
                        screenStates.SaveableStateProvider(if (linkedProjectId == null) "journal" else "linked-journal") {
                            JournalScreen(journal, journalPage, { next -> leaveInput(); journalPage = next },
                                state, currentState, initialProjectId = linkedProjectId) { message ->
                                scope.launch { snackbar.showSnackbar(message) }
                            }
                        }
                    }
                    Page.CALCULATOR -> {
                        if (settings.section == Section.TOOLS) {
                            screenStates.SaveableStateProvider("tools") {
                                ToolsScreen(
                                    onOpenSmithChart = { leaveInput(); page = Page.SMITH_CHART },
                                    onOpenMatching = { leaveInput(); page = Page.MATCHING },
                                    onOpenCable = { leaveInput(); page = Page.CABLE },
                                    onOpenGroundPlane = { leaveInput(); page = Page.GROUND_PLANE },
                                    onOpenInvertedV = { leaveInput(); page = Page.INVERTED_V },
                                    onOpenJournal = journal?.let { { leaveInput(); linkedProjectId = null; journalPage = JournalPage.PROJECTS; page = Page.JOURNAL } },
                                )
                            }
                        } else {
                            screenStates.SaveableStateProvider("calculator-results") {
                                CalculationScreen(state, onEvent, currentState, ::leaveInput,
                                    if (journal != null) ::openTransfer else null) {
                                    scope.launch { snackbar.showSnackbar(copiedMessage) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
