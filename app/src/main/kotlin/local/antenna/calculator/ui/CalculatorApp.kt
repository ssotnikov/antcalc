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
import kotlinx.coroutines.launch
import local.antenna.calculator.R
import local.antenna.calculator.journal.JournalPage
import local.antenna.calculator.journal.JournalScreen
import local.antenna.calculator.journal.JournalViewModel
import local.antenna.calculator.state.*

// Keep ABOUT as the persisted route identifier for Help.
private enum class Page { CALCULATOR, MATCHING, CABLE, INVERTED_V, SETTINGS, ABOUT, REFERENCES, JOURNAL }

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
    val journalStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = state.draft.settings
    val copiedMessage = stringResource(R.string.copied)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun leaveInput() { focus.clearFocus(); keyboard?.hide() }
    fun goBack() {
        leaveInput()
        when (page) {
            Page.REFERENCES -> page = referencesBackPage
            Page.JOURNAL -> journalPage.parent()?.let { journalPage = it } ?: run { page = Page.CALCULATOR }
            else -> page = Page.CALCULATOR
        }
    }
    fun openReferences() {
        referencesBackPage = page
        page = Page.REFERENCES
    }
    BackHandler(page != Page.CALCULATOR, onBack = ::goBack)
    CalculatorTheme(settings.theme) {
        val title = when (page) {
            Page.SETTINGS -> R.string.settings
            Page.ABOUT -> R.string.about
            Page.REFERENCES -> R.string.references_title
            Page.MATCHING -> R.string.matching_title
            Page.CABLE -> R.string.cable_screen_title
            Page.INVERTED_V -> R.string.inverted_v_title
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
                        Box {
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
                if (page == Page.CALCULATOR || page == Page.MATCHING || page == Page.CABLE || page == Page.INVERTED_V || page == Page.JOURNAL) NavigationBar(Modifier.heightIn(min =
                    80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) +
                        NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding())) {
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
            snackbarHost = { SnackbarHost(snackbar) },
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
                    Page.CABLE -> CableScreen(state, onEvent, currentState) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    Page.INVERTED_V -> InvertedVScreen(state, onEvent, currentState, journal) { message ->
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                    Page.JOURNAL -> if (journal != null) {
                        journalStateHolder.SaveableStateProvider("journal") {
                            JournalScreen(journal, journalPage, { next -> leaveInput(); journalPage = next }, state, currentState) { message ->
                                scope.launch { snackbar.showSnackbar(message) }
                            }
                        }
                    }
                    Page.CALCULATOR -> {
                        if (settings.section == Section.TOOLS) {
                            ToolsScreen(
                                onOpenMatching = { leaveInput(); page = Page.MATCHING },
                                onOpenCable = { leaveInput(); page = Page.CABLE },
                                onOpenInvertedV = { leaveInput(); page = Page.INVERTED_V },
                                onOpenJournal = journal?.let { { leaveInput(); journalPage = JournalPage.PROJECTS; page = Page.JOURNAL } },
                            )
                        } else {
                            CalculationScreen(state, onEvent, currentState, ::leaveInput) {
                                scope.launch { snackbar.showSnackbar(copiedMessage) }
                            }
                        }
                    }
                }
            }
        }
    }
}
