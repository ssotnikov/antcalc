package local.antenna.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import local.antenna.calculator.R
import local.antenna.calculator.state.*

private enum class Page { CALCULATOR, MATCHING, SETTINGS, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorApp(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit, currentState: () -> CalculatorUiState) {
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101D26)))
        return
    }
    LocalizedContent(state.draft.settings.language) {
        LocalizedCalculatorApp(state, onEvent, currentState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalizedCalculatorApp(state: CalculatorUiState, onEvent: (CalculatorEvent) -> Unit, currentState: () -> CalculatorUiState) {
    var page by rememberSaveable { mutableStateOf(Page.CALCULATOR) }
    var menu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = state.draft.settings
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val appName = stringResource(R.string.app_name)
    val copiedMessage = stringResource(R.string.copied)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun leaveInput() { focus.clearFocus(); keyboard?.hide() }
    BackHandler(page != Page.CALCULATOR) { leaveInput(); page = Page.CALCULATOR }
    CalculatorTheme(settings.theme) {
        val title = when (page) {
            Page.SETTINGS -> R.string.settings
            Page.ABOUT -> R.string.about
            Page.MATCHING -> R.string.matching_title
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
                        if (page != Page.CALCULATOR) IconButton(onClick = { leaveInput(); page = Page.CALCULATOR },
                            modifier = Modifier.testTag("back")) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { leaveInput(); menu = true }, modifier = Modifier.testTag("menu")) { Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.menu)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; page = Page.SETTINGS }, modifier = Modifier.testTag("open-settings"))
                                DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menu = false; page = Page.ABOUT }, modifier = Modifier.testTag("open-about"))
                            }
                        }
                    })
            },
            bottomBar = {
                if (page == Page.CALCULATOR || page == Page.MATCHING) NavigationBar(Modifier.heightIn(min =
                    80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) +
                        NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding())) {
                    for (section in Section.entries) {
                        val label = when (section) { Section.SIZING -> R.string.sizes; Section.TRIMMING -> R.string.trimming; Section.TOOLS -> R.string.tools }
                        NavigationBarItem(selected = settings.section == section, onClick = {
                            leaveInput(); page = Page.CALCULATOR; onEvent(CalculatorEvent.Settings(settings.copy(section = section)))
                        }, icon = { Text(when (section) { Section.SIZING -> "λ"; Section.TRIMMING -> "Δ"; Section.TOOLS -> "Γ" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {}) },
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
                    Page.ABOUT -> AboutScreen()
                    Page.MATCHING -> MatchingScreen(state, onEvent, currentState) { scope.launch { snackbar.showSnackbar(copiedMessage) } }
                    Page.CALCULATOR -> {
                        if (settings.section == Section.TOOLS) {
                            ToolsScreen { leaveInput(); page = Page.MATCHING }
                        } else {
                            val presentation = remember(state, configuration, context) { presentResults(context, state) }
                            CalculatorScreen(state, presentation, onEvent) {
                                leaveInput()
                                // Re-check the live state even if a click arrives before the next rendered frame.
                                val text = presentResults(context, currentState()).copyText ?: return@CalculatorScreen
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText(appName, text))
                                scope.launch { snackbar.showSnackbar(copiedMessage) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorScreen(state: CalculatorUiState, presentation: ResultPresentation, onEvent: (CalculatorEvent) -> Unit, onCopy: () -> Unit) {
    val settings = state.draft.settings
    val scroll = rememberSaveable(settings.section, saver = ScrollState.Saver) { ScrollState(0) }
    BoxWithConstraints(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp).testTag("calculator-scroll"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StorageNotice(state, onEvent)
            val inputs: @Composable ColumnScope.() -> Unit = { CalculatorInputs(state, onEvent) }
            val results: @Composable ColumnScope.() -> Unit = {
                if (presentation.groups.isEmpty()) HelpText(R.string.result_pending)
                presentation.groups.forEach { ResultPanel(it) }
                Button(onClick = onCopy, enabled = presentation.copyText != null,
                    modifier = Modifier.fillMaxWidth().testTag("copy-result")) { Text(stringResource(R.string.copy_result)) }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = inputs)
                    Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(20.dp), content = results)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = inputs)
                Column(verticalArrangement = Arrangement.spacedBy(20.dp), content = results)
            }
        }
    }
}

@Composable
private fun ResultPanel(group: ResultGroup) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            group.rows.forEachIndexed { index, row ->
                if (group.diagrams && index == 0) AntennaDiagram(dipole = false)
                if (group.diagrams && index == 1) AntennaDiagram(dipole = true)
                Column(Modifier.fillMaxWidth().testTag(row.tag).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(row.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.value, style = if (index == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                }
                if (index != group.rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            group.notes.forEach { HelpText(it) }
        }
    }
}

