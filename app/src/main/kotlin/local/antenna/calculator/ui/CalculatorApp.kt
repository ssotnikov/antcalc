package local.antenna.calculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.Serializable
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
import local.antenna.calculator.journal.fullWaveLoopSnapshot
import local.antenna.calculator.state.*
import local.antenna.calculator.journal.ProjectForm
import local.antenna.calculator.measurements.MeasurementScreen
import local.antenna.calculator.measurements.MeasurementViewModel
import local.antenna.calculator.measurements.SwrBandwidthScreen
import local.antenna.calculator.core.TrapDipole.Band

// Keep ABOUT as the persisted route identifier for Help.
internal enum class Page { CALCULATOR, MATCHING, SMITH_CHART, CABLE, FEEDLINE, INVERTED_V, GROUND_PLANE, FULL_WAVE_LOOP, J_POLE, SLIM_JIM, MOXON, HB9CV, YAGI_CALCULATOR, TRANSFER_TYPE, TRANSFER_PICKER, TRANSFER_NEW, SETTINGS, ABOUT, REFERENCES, LICENSES, JOURNAL, ANTENNA_PARAMETER_TOOLS, ANTENNA_CONSTRUCTION, CONSTRUCTION_YAGI, ADDITIONAL_TOOLS, MEASUREMENTS, SWR_BANDWIDTH, CANTENNA, HALO_CB, TRAP_DIPOLE, YAGI_SCALING, YAGI_SEGMENTATION, USER_GUIDE }

private data class GuideVisit(val page: Page, val menuDepth: Int, val previousSection: Section?) : Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalculatorApp(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    journal: JournalViewModel? = null,
    measurements: MeasurementViewModel? = null,
    currentState: () -> CalculatorUiState,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101D26)))
        return
    }
    val scrollToTop = remember { PageScrollToTop() }
    LocalizedContent(state.draft.settings.language) {
        CompositionLocalProvider(LocalPageScrollToTop provides scrollToTop) {
            LocalizedCalculatorApp(state, onEvent, journal, measurements, scrollToTop, currentState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalizedCalculatorApp(
    state: CalculatorUiState,
    onEvent: (CalculatorEvent) -> Unit,
    journal: JournalViewModel?,
    measurements: MeasurementViewModel?,
    scrollToTop: PageScrollToTop,
    currentState: () -> CalculatorUiState,
) {
    var page by rememberSaveable { mutableStateOf(Page.CALCULATOR) }
    var segmentationParent by rememberSaveable { mutableStateOf(Page.ADDITIONAL_TOOLS) }
    var segmentationInput by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var menuParents by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var guideVisits by rememberSaveable { mutableStateOf<List<GuideVisit>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var cableParent by rememberSaveable { mutableStateOf(Page.CALCULATOR) }
    var measurementsParent by rememberSaveable { mutableStateOf(Page.ADDITIONAL_TOOLS) }
    var savedCantennaProject by rememberSaveable { mutableStateOf<String?>(null) }
    var yagiLinkOrigin by rememberSaveable { mutableStateOf<Page?>(null) }
    var bandwidthFromMeasurements by rememberSaveable { mutableStateOf(false) }
    var bandwidthParent by rememberSaveable { mutableStateOf(Page.ADDITIONAL_TOOLS) }
    var bandwidthEntrySlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var journalPage by rememberSaveable { mutableStateOf(JournalPage.PROJECTS) }
    val screenStates = rememberSaveableStateHolder()
    var pending by rememberSaveable(stateSaver = PendingProjectCalculation.saver) { mutableStateOf<PendingProjectCalculation?>(null) }
    var scalingRestore by rememberSaveable(stateSaver = PendingProjectCalculation.saver) { mutableStateOf<PendingProjectCalculation?>(null) }
    var trapRestore by rememberSaveable(stateSaver = PendingProjectCalculation.saver) { mutableStateOf<PendingProjectCalculation?>(null) }
    var trapBand by rememberSaveable { mutableStateOf(Band.M20) }
    var haloRestore by rememberSaveable(stateSaver = PendingProjectCalculation.saver) { mutableStateOf<PendingProjectCalculation?>(null) }
    var sweepTransferId by rememberSaveable { mutableStateOf<UUID?>(null) }
    var transferType by rememberSaveable { mutableStateOf(AntennaType.SYMMETRIC_DIPOLE) }
    var linkedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = state.draft.settings
    val returnToGuide = guideVisits.lastOrNull()?.takeIf { visit ->
        visit.page == page && visit.menuDepth == menuParents.size && when (page) {
            Page.JOURNAL -> journalPage == JournalPage.PROJECTS
            Page.HALO_CB -> haloRestore == null
            Page.TRAP_DIPOLE -> trapRestore == null
            Page.YAGI_SCALING -> scalingRestore == null
            else -> true
        }
    }
    val journalStatus = journal?.state?.collectAsState()?.value
    val measurementState = measurements?.state?.collectAsState()?.value
    val sweepTraces = measurementState?.takeIf { !it.busy && it.showTraces }?.traces?.filterNotNull().orEmpty()
    val copiedMessage = stringResource(R.string.copied)
    val transferSavedMessage = stringResource(R.string.project_transfer_saved)
    val openProjectLabel = stringResource(R.string.project_transfer_open)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun leaveInput() { focus.clearFocus(); keyboard?.hide() }
    fun finishTransfer() {
        page = if (sweepTransferId != null) Page.MEASUREMENTS else pending?.returnPage ?: Page.CALCULATOR
        listOfNotNull(pending?.recordId, sweepTransferId).forEach { transferId ->
            screenStates.removeState("transfer-picker-$transferId")
            screenStates.removeState("transfer-new-$transferId")
        }
        pending = null
        sweepTransferId = null
    }
    fun openRelatedYagiPage(destination: Page) {
        leaveInput()
        if (destination == Page.YAGI_CALCULATOR) {
            context.selectYagiCalculatorProfile(YagiProfile.R3)
            if (yagiLinkOrigin == null) screenStates.removeState("yagi-calculator")
        }
        yagiLinkOrigin = if (yagiLinkOrigin == destination) null else page
        page = destination
    }
    fun openSegmentation(input: ArrayList<String>?, parent: Page = page) {
        leaveInput()
        segmentationParent = parent
        segmentationInput = input
        screenStates.removeState("yagi-segmentation")
        page = Page.YAGI_SEGMENTATION
    }
    fun goBack() {
        if ((page == Page.TRANSFER_PICKER || page == Page.TRANSFER_NEW) && journalStatus?.busy == true) return
        leaveInput()
        if (returnToGuide != null) {
            returnToGuide.previousSection?.let { onEvent(CalculatorEvent.Settings(settings.copy(section = it))) }
            guideVisits = guideVisits.dropLast(1)
            page = Page.USER_GUIDE
            return
        }
        when (page) {
            Page.SETTINGS, Page.ABOUT, Page.REFERENCES, Page.LICENSES, Page.USER_GUIDE -> {
                page = menuParents.lastOrNull()?.let(Page::valueOf) ?: Page.CALCULATOR
                menuParents = ArrayList(menuParents.dropLast(1))
            }
            Page.ANTENNA_PARAMETER_TOOLS, Page.ANTENNA_CONSTRUCTION, Page.ADDITIONAL_TOOLS -> page = Page.CALCULATOR
            Page.CONSTRUCTION_YAGI, Page.YAGI_CALCULATOR -> {
                page = yagiLinkOrigin ?: if (page == Page.CONSTRUCTION_YAGI) Page.ANTENNA_CONSTRUCTION else Page.ANTENNA_PARAMETER_TOOLS
                yagiLinkOrigin = null
            }
            Page.INVERTED_V, Page.GROUND_PLANE, Page.FULL_WAVE_LOOP, Page.J_POLE, Page.SLIM_JIM, Page.MOXON, Page.HB9CV, Page.CANTENNA -> page = Page.ANTENNA_PARAMETER_TOOLS
            Page.YAGI_SEGMENTATION -> page = segmentationParent
            Page.YAGI_SCALING -> page = if (scalingRestore != null) Page.JOURNAL else Page.CALCULATOR
            Page.TRAP_DIPOLE -> page = if (trapRestore != null) Page.JOURNAL else Page.ANTENNA_PARAMETER_TOOLS
            Page.HALO_CB -> page = if (haloRestore != null) Page.JOURNAL else Page.ANTENNA_PARAMETER_TOOLS
            Page.CABLE -> page = cableParent
            Page.MEASUREMENTS -> page = measurementsParent
            Page.SWR_BANDWIDTH -> page = if (bandwidthFromMeasurements) Page.MEASUREMENTS else bandwidthParent
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
    fun openFullWaveLoopTransfer() {
        val current = currentState()
        if (!current.editingEnabled) return
        val snapshot = current.draft.fullWaveLoop.fullWaveLoopSnapshot() ?: return
        startTransfer(PendingProjectCalculation(snapshot), AntennaType.FULL_WAVE_LOOP)
    }
    fun showSavedProject(projectId: UUID) {
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
    fun openMenuPage(destination: Page) {
        menu = false
        if (destination == page) return
        menuParents = ArrayList(menuParents + page.name)
        page = destination
    }
    fun openGuideDestination(destination: GuideDestination) {
        leaveInput()
        guideVisits = guideVisits + GuideVisit(destination.page, menuParents.size,
            settings.section.takeIf { destination.section != null })
        destination.section?.let { onEvent(CalculatorEvent.Settings(settings.copy(section = it))) }
        destination.shape?.let { onEvent(CalculatorEvent.FullWaveLoop(currentState().draft.fullWaveLoop.chooseShape(it))) }
        when (destination.page) {
            Page.YAGI_CALCULATOR, Page.CONSTRUCTION_YAGI -> {
                yagiLinkOrigin = null
                if (destination == GuideDestination.R3UDK_CALCULATOR) {
                    context.selectYagiCalculatorProfile(YagiProfile.R3)
                    screenStates.removeState("yagi-calculator")
                }
            }
            Page.JOURNAL -> {
                linkedProjectId = null
                journalPage = JournalPage.PROJECTS
            }
            Page.HALO_CB -> haloRestore = null
            Page.YAGI_SEGMENTATION -> openSegmentation(null, Page.ADDITIONAL_TOOLS)
            Page.YAGI_SCALING -> scalingRestore = null
            Page.TRAP_DIPOLE -> trapRestore = null
            Page.CABLE -> cableParent = Page.CALCULATOR
            Page.MEASUREMENTS -> {
                measurementsParent = Page.ADDITIONAL_TOOLS
                measurements?.showWorkspace()
            }
            Page.SWR_BANDWIDTH -> {
                bandwidthFromMeasurements = false
                bandwidthParent = Page.ADDITIONAL_TOOLS
                bandwidthEntrySlot = null
                measurements?.showWorkspace()
            }
            else -> Unit
        }
        page = destination.page
    }
    val transferring = page == Page.TRANSFER_TYPE || page == Page.TRANSFER_PICKER || page == Page.TRANSFER_NEW
    LaunchedEffect(transferring, pending) {
        if (transferring && pending == null && sweepTransferId == null) finishTransfer()
    }
    val savedTransfer = pending?.let { draft ->
        journalStatus?.projects?.firstOrNull { project -> project.records.any { it.id == draft.recordId } }
    }
    LaunchedEffect(savedTransfer?.id, pending?.recordId) {
        val calculation = pending
        if (calculation != null && savedTransfer != null) showSavedProject(savedTransfer.id)
    }
    val savedSweepTransfer = journalStatus?.savedSweepTransfer
    LaunchedEffect(sweepTransferId, savedSweepTransfer) {
        if (savedSweepTransfer != null && savedSweepTransfer.first == sweepTransferId) {
            showSavedProject(savedSweepTransfer.second)
        }
    }
    LaunchedEffect(sweepTransferId, measurementState?.busy, sweepTraces.isEmpty()) {
        if (sweepTransferId != null && measurementState?.busy == false && sweepTraces.isEmpty()) finishTransfer()
    }
    LaunchedEffect(page, menuParents) {
        if (page != Page.MEASUREMENTS && Page.MEASUREMENTS.name !in menuParents) measurements?.cancelImport()
    }
    BackHandler(page != Page.CALCULATOR || returnToGuide != null, onBack = ::goBack)
    CalculatorTheme(settings.theme) {
        val offerAllowed = !menu && !transferring && WindowInsets.ime.getBottom(LocalDensity.current) == 0
        val updateActions = rememberUpdateMenuActions(offerAllowed, page == Page.CALCULATOR, snackbar)
        val guideUpdateAction = updateActions.firstOrNull { it.tag == "check-for-updates" }
        val title = when (page) {
            Page.ANTENNA_CONSTRUCTION -> R.string.construction_title
            Page.CONSTRUCTION_YAGI -> R.string.construction_yagi_title
            Page.ANTENNA_PARAMETER_TOOLS -> R.string.antenna_parameter_tools
            Page.ADDITIONAL_TOOLS -> R.string.additional_tools
            Page.MEASUREMENTS -> R.string.sweep_title
            Page.SWR_BANDWIDTH -> R.string.bandwidth_title
            Page.SETTINGS -> R.string.settings
            Page.ABOUT -> R.string.about
            Page.USER_GUIDE -> R.string.user_guide
            Page.REFERENCES -> R.string.references_title
            Page.LICENSES -> R.string.licenses_title
            Page.MATCHING -> R.string.matching_title
            Page.SMITH_CHART -> R.string.smith_title
            Page.CABLE -> R.string.cable_screen_title
            Page.FEEDLINE -> R.string.feedline_title
            Page.INVERTED_V -> R.string.inverted_v_title
            Page.GROUND_PLANE -> R.string.ground_plane_title
            Page.FULL_WAVE_LOOP -> R.string.loop_title
            Page.YAGI_CALCULATOR -> R.string.yagi_title
            Page.SLIM_JIM -> R.string.slim_jim_title
            Page.MOXON -> R.string.moxon_title
            Page.HB9CV -> R.string.hb9cv_title
            Page.CANTENNA -> R.string.cantenna_title
            Page.HALO_CB -> R.string.halo_title
            Page.YAGI_SEGMENTATION -> R.string.seg_title
            Page.YAGI_SCALING -> R.string.ys_title
            Page.TRAP_DIPOLE -> R.string.trap_title
            Page.J_POLE -> R.string.jpole_title
            Page.TRANSFER_TYPE -> R.string.project_transfer_choose_type
            Page.TRANSFER_PICKER -> if (sweepTransferId != null) R.string.sweep_save else R.string.project_transfer_title
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
                TopAppBar(title = {
                    val action = scrollToTop.action
                    val sweep = remember(page, title) { Animatable(1f) }
                    val titleScope = rememberCoroutineScope()
                    val accent = MaterialTheme.colorScheme.primary
                    val titleClick = if (action != null) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = stringResource(R.string.scroll_to_top),
                            role = Role.Button,
                            onClick = {
                                action()
                                titleScope.launch {
                                    sweep.snapTo(0f)
                                    sweep.animateTo(1f, tween(410, easing = LinearEasing))
                                }
                            },
                        )
                    } else Modifier
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(titleClick)
                            .testTag("screen-title").semantics { heading() },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            stringResource(title),
                            modifier = Modifier.drawBehind {
                                val progress = sweep.value
                                if (progress >= 1f) return@drawBehind
                                val inset = 8.dp.toPx()
                                val band = (size.width * .36f).coerceAtLeast(24.dp.toPx())
                                val center = -inset - band + (size.width + 2 * (inset + band)) * progress
                                val opacity = minOf(1f, progress / .12f, (1f - progress) / .27f)
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(accent.copy(alpha = 0f), accent.copy(alpha = .27f * opacity), accent.copy(alpha = 0f)),
                                        startX = center - band,
                                        endX = center + band,
                                    ),
                                    topLeft = Offset(-inset, -4.dp.toPx()),
                                    size = Size(size.width + 2 * inset, size.height + 8.dp.toPx()),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                )
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                    expandedHeight = 64.dp * LocalDensity.current.fontScale,
                    navigationIcon = {
                        if (page != Page.CALCULATOR || returnToGuide != null) IconButton(onClick = ::goBack,
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
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { openMenuPage(Page.SETTINGS) }, modifier = Modifier.testTag("open-settings"))
                                DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { openMenuPage(Page.ABOUT) }, modifier = Modifier.testTag("open-about"))
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.user_guide)) },
                                    onClick = { openMenuPage(Page.USER_GUIDE) },
                                    modifier = Modifier.testTag("open-user-guide"),
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.references_title)) }, onClick = {
                                    openMenuPage(Page.REFERENCES)
                                }, modifier = Modifier.testTag("open-references"))
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.licenses_title)) },
                                    onClick = { openMenuPage(Page.LICENSES) },
                                    modifier = Modifier.testTag("open-licenses"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.contact_author)) },
                                    onClick = { menu = false; openAuthorContact(context) },
                                    modifier = Modifier.testTag("contact-author"),
                                )
                                if (state.draft.settings.language == AppLanguage.RUSSIAN) {
                                    authorSupportPaymentUrl?.let { url ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.support_author)) },
                                            onClick = { menu = false; openSourceWithFeedback(context, url) },
                                            modifier = Modifier.testTag("menu-support-author"),
                                        )
                                    }
                                }
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
                if (page == Page.CALCULATOR || page == Page.MATCHING || page == Page.SMITH_CHART || page == Page.CABLE || page == Page.FEEDLINE || page == Page.INVERTED_V || page == Page.GROUND_PLANE || page == Page.FULL_WAVE_LOOP || page == Page.J_POLE || page == Page.SLIM_JIM || page == Page.MOXON || page == Page.HB9CV || page == Page.YAGI_CALCULATOR || page == Page.JOURNAL || page == Page.ANTENNA_PARAMETER_TOOLS || page == Page.ANTENNA_CONSTRUCTION || page == Page.CONSTRUCTION_YAGI || page == Page.ADDITIONAL_TOOLS || page == Page.MEASUREMENTS || page == Page.SWR_BANDWIDTH || page == Page.CANTENNA || page == Page.HALO_CB || page == Page.TRAP_DIPOLE || page == Page.YAGI_SCALING || page == Page.YAGI_SEGMENTATION) NavigationBar(Modifier.heightIn(min =
                    80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) +
                        NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()).testTag("bottom-navigation")) {
                    for (section in Section.entries) {
                        val label = when (section) { Section.SIZING -> R.string.sizes; Section.TRIMMING -> R.string.trimming; Section.TOOLS -> R.string.tools }
                        NavigationBarItem(selected = settings.section == section, onClick = {
                            if (guideVisits.isNotEmpty()) {
                                guideVisits = emptyList()
                                menuParents = arrayListOf()
                            }
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
                    Page.ANTENNA_PARAMETER_TOOLS -> screenStates.SaveableStateProvider("antenna-parameter-tools") {
                        AntennaParameterToolsScreen(
                            onOpenInvertedV = { leaveInput(); page = Page.INVERTED_V },
                            onOpenGroundPlane = { leaveInput(); page = Page.GROUND_PLANE },
                            onOpenFullWaveLoop = { leaveInput(); page = Page.FULL_WAVE_LOOP },
                            onOpenSlimJim = { leaveInput(); page = Page.SLIM_JIM },
                            onOpenMoxon = { leaveInput(); page = Page.MOXON },
                            onOpenHb9cv = { leaveInput(); page = Page.HB9CV },
                            onOpenCantenna = { leaveInput(); page = Page.CANTENNA },
                            onOpenTrapDipole = {
                                leaveInput()
                                trapRestore = null
                                page = Page.TRAP_DIPOLE
                            },
                            onOpenHalo = {
                                leaveInput()
                                haloRestore = null
                                page = Page.HALO_CB
                            },
                            onOpenJPole = { leaveInput(); page = Page.J_POLE },
                            onOpenYagi = {
                                leaveInput()
                                yagiLinkOrigin = null
                                page = Page.YAGI_CALCULATOR
                            },
                        )
                    }
                    Page.ANTENNA_CONSTRUCTION -> screenStates.SaveableStateProvider("antenna-construction") {
                        AntennaConstructionScreen {
                            yagiLinkOrigin = null
                            page = Page.CONSTRUCTION_YAGI
                        }
                    }
                    Page.CONSTRUCTION_YAGI -> screenStates.SaveableStateProvider("construction-yagi") {
                        YagiConstructionScreen(
                            onOpenCalculator = { openRelatedYagiPage(Page.YAGI_CALCULATOR) },
                            onAddToProject = if (journal == null) null else {
                                { startTransfer(context.r3udkGuideProjectCalculation(), AntennaType.YAGI) }
                            },
                            onOpenMeasurements = measurements?.let { model ->
                                {
                                    measurementsParent = Page.CONSTRUCTION_YAGI
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                        )
                    }
                    Page.ADDITIONAL_TOOLS -> screenStates.SaveableStateProvider("additional-tools") {
                        AdditionalToolsScreen(
                            onOpenSegmentation = { openSegmentation(null) },
                            onOpenMeasurements = measurements?.let { model ->
                                {
                                    measurementsParent = Page.ADDITIONAL_TOOLS
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onOpenBandwidth = measurements?.let { model ->
                                {
                                    bandwidthFromMeasurements = false
                                    bandwidthParent = Page.ADDITIONAL_TOOLS
                                    bandwidthEntrySlot = null
                                    model.showWorkspace()
                                    page = Page.SWR_BANDWIDTH
                                }
                            },
                        )
                    }
                    Page.SWR_BANDWIDTH -> if (measurements != null) {
                        val initialBand = trapBand.takeIf {
                            if (bandwidthFromMeasurements) measurementsParent == Page.TRAP_DIPOLE
                            else bandwidthParent == Page.TRAP_DIPOLE
                        }
                        val screenKey = initialBand?.let { "swr-bandwidth-trap-${it.meters}" } ?: "swr-bandwidth"
                        screenStates.SaveableStateProvider(screenKey) {
                            SwrBandwidthScreen(
                                measurements,
                                onOpenMeasurements = {
                                    leaveInput()
                                    if (!bandwidthFromMeasurements) measurementsParent = Page.SWR_BANDWIDTH
                                    page = Page.MEASUREMENTS
                                },
                                entrySlot = bandwidthEntrySlot,
                                initialBand = initialBand,
                                onEntryApplied = { bandwidthEntrySlot = null },
                            ) { message -> scope.launch { snackbar.showSnackbar(message) } }
                        }
                    }
                    Page.MEASUREMENTS -> if (measurements != null) screenStates.SaveableStateProvider("measurements") {
                        MeasurementScreen(
                            measurements,
                            showBeforeAfter = measurementsParent == Page.CANTENNA ||
                                measurementsParent == Page.JOURNAL && savedCantennaProject != null,
                            beforeAfterProject = savedCantennaProject.takeIf { measurementsParent == Page.JOURNAL },
                            onOpenBandwidth = { slot ->
                                leaveInput()
                                bandwidthFromMeasurements = measurementsParent != Page.SWR_BANDWIDTH
                                bandwidthEntrySlot = slot
                                page = Page.SWR_BANDWIDTH
                            },
                            onSaveToProject = if (journal == null) null else {
                                {
                                    snackbar.currentSnackbarData?.dismiss()
                                    leaveInput()
                                    sweepTransferId = UUID.randomUUID()
                                    page = Page.TRANSFER_PICKER
                                }
                            },
                        ) { message -> scope.launch { snackbar.showSnackbar(message) } }
                    }
                    Page.SETTINGS -> screenStates.SaveableStateProvider("settings-${menuParents.size}") {
                        SettingsScreen(state, onEvent, onAbout = { openMenuPage(Page.ABOUT) },
                            onOpenNecLicense = { openMenuPage(Page.LICENSES) })
                    }
                    Page.ABOUT -> screenStates.SaveableStateProvider("help-${menuParents.size}") { HelpScreen() }
                    Page.USER_GUIDE -> screenStates.SaveableStateProvider("user-guide-${menuParents.size}") {
                        val dark = when (settings.theme) {
                            AppTheme.SYSTEM -> isSystemInDarkTheme()
                            AppTheme.LIGHT -> false
                            AppTheme.DARK -> true
                        }
                        UserGuideScreen(settings.language, dark, guideUpdateAction != null) { destination ->
                            if (destination == GuideDestination.CHECK_UPDATES) {
                                guideUpdateAction?.takeIf { it.enabled }?.onClick?.invoke()
                            } else openGuideDestination(destination)
                        }
                    }
                    Page.REFERENCES -> screenStates.SaveableStateProvider("references-${menuParents.size}") { ReferencesScreen(settings.language) }
                    Page.LICENSES -> screenStates.SaveableStateProvider("licenses-${menuParents.size}") { LicensesScreen() }
                    Page.MATCHING -> screenStates.SaveableStateProvider("matching") {
                        MatchingScreen(state, onEvent, currentState) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    }
                    Page.SMITH_CHART -> screenStates.SaveableStateProvider("smith-chart") {
                        SmithChartScreen { message -> scope.launch { snackbar.showSnackbar(message) } }
                    }
                    Page.CABLE -> screenStates.SaveableStateProvider("cable") {
                        CableScreen(state, onEvent, currentState) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    }
                    Page.FEEDLINE -> screenStates.SaveableStateProvider("feedline") {
                        FeedlineScreen(state, currentState, onEvent) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    }
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
                    Page.YAGI_CALCULATOR -> screenStates.SaveableStateProvider("yagi-calculator") {
                        YagiCalculatorScreen(
                            onSegmentation = { openSegmentation(it) },
                            onGuide = { openRelatedYagiPage(Page.CONSTRUCTION_YAGI) },
                            onMeasurements = measurements?.let { model ->
                                {
                                    leaveInput()
                                    measurementsParent = Page.YAGI_CALCULATOR
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onSave = if (journal == null) null else { snapshot, note ->
                                startTransfer(PendingProjectCalculation(snapshot, note = note), AntennaType.YAGI)
                            },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.YAGI_SEGMENTATION -> screenStates.SaveableStateProvider("yagi-segmentation") {
                        YagiSegmentationScreen(segmentationInput)
                    }
                    Page.YAGI_SCALING -> screenStates.SaveableStateProvider(if (scalingRestore == null) "yagi-scaling" else "yagi-scaling-record") {
                        YagiScalingScreen(scalingRestore?.snapshot, if (journal == null) null else { snapshot ->
                            startTransfer(PendingProjectCalculation(snapshot), AntennaType.YAGI)
                        }, onSegmentation = { openSegmentation(it) }) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    }
                    Page.TRAP_DIPOLE -> screenStates.SaveableStateProvider(if (trapRestore == null) "trap-dipole" else "trap-dipole-record") {
                        TrapDipoleScreen(
                            initial = trapRestore?.snapshot,
                            onMeasurements = measurements?.let { model ->
                                { band ->
                                    leaveInput()
                                    trapBand = band
                                    measurementsParent = Page.TRAP_DIPOLE
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onBandwidth = measurements?.let { model ->
                                { band ->
                                    leaveInput()
                                    trapBand = band
                                    bandwidthFromMeasurements = false
                                    bandwidthParent = Page.TRAP_DIPOLE
                                    bandwidthEntrySlot = null
                                    model.showWorkspace()
                                    page = Page.SWR_BANDWIDTH
                                }
                            },
                            onSave = if (journal == null) null else { snapshot ->
                                startTransfer(PendingProjectCalculation(snapshot), AntennaType.TRAP_DIPOLE)
                            },
                            onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                        )
                    }
                    Page.HALO_CB -> screenStates.SaveableStateProvider(if (haloRestore == null) "halo-cb" else "halo-cb-record") {
                        HaloCbScreen(
                            initial = haloRestore?.snapshot,
                            onMeasurements = measurements?.let { model ->
                                {
                                    leaveInput()
                                    measurementsParent = Page.HALO_CB
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onBandwidth = measurements?.let { model ->
                                {
                                    leaveInput()
                                    bandwidthFromMeasurements = false
                                    bandwidthParent = Page.HALO_CB
                                    bandwidthEntrySlot = null
                                    model.showWorkspace()
                                    page = Page.SWR_BANDWIDTH
                                }
                            },
                            onSave = if (journal == null) null else { snapshot ->
                                startTransfer(PendingProjectCalculation(snapshot), AntennaType.HALO_CB)
                            },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.CANTENNA -> screenStates.SaveableStateProvider("cantenna") {
                        CantennaScreen(
                            onMeasurements = measurements?.let { model ->
                                {
                                    leaveInput()
                                    measurementsParent = Page.CANTENNA
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onSave = if (journal == null) null else { snapshot, note ->
                                startTransfer(PendingProjectCalculation(snapshot, note = note), AntennaType.CANTENNA)
                            },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.HB9CV -> screenStates.SaveableStateProvider("hb9cv") {
                        Hb9cvScreen(
                            onMeasurements = measurements?.let { model ->
                                {
                                    leaveInput()
                                    measurementsParent = Page.HB9CV
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onSave = if (journal == null) null else { snapshot, note ->
                                startTransfer(PendingProjectCalculation(snapshot, note = note), AntennaType.HB9CV)
                            },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.MOXON -> screenStates.SaveableStateProvider("moxon") {
                        MoxonScreen(
                            onSave = if (journal == null) null else { snapshot, note ->
                                startTransfer(PendingProjectCalculation(snapshot, note = note), AntennaType.MOXON)
                            },
                            onMeasurements = measurements?.let { model ->
                                {
                                    leaveInput()
                                    measurementsParent = Page.MOXON
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.SLIM_JIM -> screenStates.SaveableStateProvider("slim-jim") {
                        SlimJimScreen(
                            onSave = if (journal == null) null else { snapshot ->
                                startTransfer(PendingProjectCalculation(snapshot), AntennaType.SLIM_JIM)
                            },
                            onMeasurements = measurements?.let { model ->
                                {
                                    leaveInput()
                                    measurementsParent = Page.SLIM_JIM
                                    model.showWorkspace()
                                    page = Page.MEASUREMENTS
                                }
                            },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.J_POLE -> screenStates.SaveableStateProvider("j-pole") {
                        JPoleScreen(
                            onSave = if (journal == null) null else { snapshot ->
                                startTransfer(PendingProjectCalculation(snapshot), AntennaType.J_POLE)
                            },
                            onOpenCable = { leaveInput(); cableParent = Page.J_POLE; page = Page.CABLE },
                            onCopied = { scope.launch { snackbar.showSnackbar(copiedMessage) } },
                        )
                    }
                    Page.FULL_WAVE_LOOP -> screenStates.SaveableStateProvider("full-wave-loop") {
                        val chooseProject = if (journal != null) ::openFullWaveLoopTransfer else null
                        FullWaveLoopScreen(state, currentState, onEvent, chooseProject) {
                            scope.launch { snackbar.showSnackbar(copiedMessage) }
                        }
                    }
                    Page.TRANSFER_TYPE -> pending?.let { calculation ->
                        TransferAntennaType(calculation, transferType, { type ->
                            if (transferType != type) screenStates.removeState("transfer-picker-${calculation.recordId}")
                            transferType = type
                        }) { page = Page.TRANSFER_PICKER }
                    }
                    Page.TRANSFER_PICKER -> if (journal != null) {
                        val sweepId = sweepTransferId
                        if (sweepId != null) {
                            screenStates.SaveableStateProvider("transfer-picker-$sweepId") {
                                ProjectPicker(journal, antennaType = null,
                                    onSelect = { project ->
                                        leaveInput()
                                        if (sweepTraces.isNotEmpty()) journal.saveSweeps(sweepId, project.id, sweepTraces)
                                    },
                                    targetHertz = null,
                                    onCreate = { leaveInput(); page = Page.TRANSFER_NEW },
                                    header = {
                                        Text(stringResource(R.string.sweep_saved_traces),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.secondary)
                                        sweepTraces.forEach { trace -> Text(trace.name, style = MaterialTheme.typography.bodyMedium) }
                                    },
                                )
                            }
                        } else pending?.let { calculation ->
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
                    }
                    Page.TRANSFER_NEW -> if (journal != null) {
                        val sweepId = sweepTransferId
                        if (sweepId != null) {
                            screenStates.SaveableStateProvider("transfer-new-$sweepId") {
                                ProjectForm(
                                    initial = null,
                                    enabled = journalStatus?.let { it.readable && !it.busy } == true && sweepTraces.isNotEmpty(),
                                    problem = journalStatus?.problem,
                                ) { project ->
                                    leaveInput()
                                    val newProject = project.copy(id = sweepId)
                                    journal.saveSweeps(sweepId, newProject.id, sweepTraces, newProject)
                                }
                            }
                        } else pending?.let { calculation ->
                            screenStates.SaveableStateProvider("transfer-new-${calculation.recordId}") {
                                val recordTitle = stringResource(calculation.title)
                                TransferNewProject(calculation, transferType, journal) { name ->
                                    leaveInput()
                                    val project = calculation.newProject(transferType, name)
                                    journal.saveTransferredCalculation(project.id, calculation.record(transferType, recordTitle), project)
                                }
                            }
                        }
                    }
                    Page.JOURNAL -> if (journal != null) {
                        screenStates.SaveableStateProvider(if (linkedProjectId == null) "journal" else "linked-journal") {
                            JournalScreen(journal, journalPage, { next -> leaveInput(); journalPage = next },
                                state, currentState, initialProjectId = linkedProjectId,
                                onOpenHalo = { snapshot ->
                                    leaveInput()
                                    screenStates.removeState("halo-cb-record")
                                    haloRestore = PendingProjectCalculation(snapshot)
                                    page = Page.HALO_CB
                                },
                                onOpenYagiScaling = { snapshot ->
                                    leaveInput()
                                    screenStates.removeState("yagi-scaling-record")
                                    scalingRestore = PendingProjectCalculation(snapshot)
                                    page = Page.YAGI_SCALING
                                },
                                onOpenTrapDipole = { snapshot ->
                                    leaveInput()
                                    screenStates.removeState("trap-dipole-record")
                                    trapRestore = PendingProjectCalculation(snapshot)
                                    page = Page.TRAP_DIPOLE
                                },
                                onOpenSweeps = measurements?.let { model ->
                                    { sweep, project ->
                                        savedCantennaProject = project.title.takeIf { project.antennaType == AntennaType.CANTENNA }
                                        measurementsParent = Page.JOURNAL
                                        page = Page.MEASUREMENTS
                                        model.openAttachments(sweep.traces)
                                    }
                                },
                            ) { message ->
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
                                    onOpenCable = { leaveInput(); cableParent = Page.CALCULATOR; page = Page.CABLE },
                                    onOpenFeedline = { leaveInput(); page = Page.FEEDLINE },
                                    onOpenYagiScaling = { leaveInput(); scalingRestore = null; page = Page.YAGI_SCALING },
                                    onOpenAdditional = { leaveInput(); page = Page.ADDITIONAL_TOOLS },
                                    onOpenConstruction = { leaveInput(); page = Page.ANTENNA_CONSTRUCTION },
                                    onOpenAntennaCalculations = { leaveInput(); page = Page.ANTENNA_PARAMETER_TOOLS },
                                    onOpenJournal = journal?.let { { leaveInput(); linkedProjectId = null; journalPage = JournalPage.PROJECTS; page = Page.JOURNAL } },
                                )
                            }
                        } else {
                            screenStates.SaveableStateProvider("calculator-results-${settings.section.name}") {
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
