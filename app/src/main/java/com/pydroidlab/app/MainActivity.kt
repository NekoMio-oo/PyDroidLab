package com.pydroidlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pydroidlab.app.ui.MainViewModel
import com.pydroidlab.app.ui.NavTab
import com.pydroidlab.app.ui.screens.CodeEditorScreen
import com.pydroidlab.app.ui.screens.FileManagerScreen
import com.pydroidlab.app.ui.screens.FilePreviewDialog
import com.pydroidlab.app.ui.screens.PipManagerScreen
import com.pydroidlab.app.ui.screens.SamplesScreen
import com.pydroidlab.app.ui.screens.SettingsScreen
import com.pydroidlab.app.ui.screens.TerminalConsoleSheet
import com.pydroidlab.app.ui.theme.PyDroidLabTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            PyDroidLabTheme(themeMode = settings.themeMode) {
                PyDroidLabMainScaffold(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PyDroidLabMainScaffold(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val fileBrowserState by viewModel.fileBrowserState.collectAsStateWithLifecycle()
    val pipState by viewModel.pipState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val terminalSnapshot by viewModel.terminalSnapshot.collectAsStateWithLifecycle()
    val isTerminalExpanded by viewModel.isTerminalExpanded.collectAsStateWithLifecycle()
    val isReplMode by viewModel.isReplMode.collectAsStateWithLifecycle()
    val replContinuation by viewModel.replContinuation.collectAsStateWithLifecycle()
    val previewFile by viewModel.previewFile.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val showFullScreenTerminal = isTerminalExpanded && settings.terminalFullScreen

    LaunchedEffect(editorState.statusBanner) {
        val msg = editorState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Hide bottom navigation bar when in Full-Screen Terminal mode for an immersive Termux experience
            if (!showFullScreenTerminal) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == NavTab.EDITOR,
                        onClick = { viewModel.selectTab(NavTab.EDITOR) },
                        icon = { Icon(Icons.Default.Code, contentDescription = null) },
                        label = { Text(NavTab.EDITOR.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == NavTab.FILES,
                        onClick = { viewModel.selectTab(NavTab.FILES) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text(NavTab.FILES.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == NavTab.PIP,
                        onClick = { viewModel.selectTab(NavTab.PIP) },
                        icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                        label = { Text(NavTab.PIP.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == NavTab.SAMPLES,
                        onClick = { viewModel.selectTab(NavTab.SAMPLES) },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        label = { Text(NavTab.SAMPLES.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == NavTab.SETTINGS,
                        onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(NavTab.SETTINGS.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (showFullScreenTerminal) {
            // Mode A (Default): Full-Screen Termux-style Terminal Page
            TerminalConsoleSheet(
                snapshot = terminalSnapshot,
                fontSizeSp = settings.terminalFontSizeSp,
                isFullScreen = true,
                isReplMode = isReplMode,
                replContinuation = replContinuation,
                onToggleFullScreen = viewModel::setTerminalFullScreen,
                onToggleReplMode = viewModel::setReplMode,
                onResetRepl = viewModel::resetReplSession,
                onSubmitInput = viewModel::submitTerminalInput,
                onJumpToLine = viewModel::requestJumpToLine,
                onStopScript = viewModel::stopRunningScript,
                onRerunScript = viewModel::runCurrentScript,
                onClearTerminal = viewModel::clearTerminal,
                onCloseSheet = { viewModel.setTerminalExpanded(false) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            // Mode B: Standard Editor / Tabs + Optional Bottom Split-Screen Terminal Dock
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        NavTab.EDITOR -> {
                            CodeEditorScreen(
                                editorState = editorState,
                                settings = settings,
                                isRunning = terminalSnapshot.isRunning,
                                onSelectTab = viewModel::selectEditorTab,
                                onCloseTab = viewModel::closeEditorTab,
                                onTextChanged = viewModel::onEditorTextChanged,
                                onCursorChanged = viewModel::onEditorCursorChanged,
                                onRunClicked = viewModel::runCurrentScript,
                                onStopClicked = viewModel::stopRunningScript,
                                onSaveClicked = { viewModel.saveCurrentFile() },
                                onSaveAsClicked = viewModel::saveCurrentFileAs,
                                onNewFileClicked = { name -> viewModel.createNewScriptInEditor(name) },
                                onToggleFindReplace = viewModel::toggleFindReplaceBar,
                                onUpdateFindReplace = viewModel::updateFindReplaceState,
                                onToggleTerminal = {
                                    viewModel.setTerminalExpanded(!isTerminalExpanded)
                                },
                                onRefreshRuntimeDiagnostics = viewModel::refreshRuntimeDiagnostics,
                                onConsumeJumpToLine = viewModel::consumeJumpToLine
                            )
                        }

                        NavTab.FILES -> {
                            FileManagerScreen(
                                browserState = fileBrowserState,
                                workspaceRoot = viewModel.fileRepository.workspaceRoot,
                                onOpenFile = viewModel::openFileInEditor,
                                onNavigateDirectory = viewModel::refreshFileBrowser,
                                onNavigateUp = { viewModel.navigateUpDirectory() },
                                onCreateFile = viewModel::createFileInBrowser,
                                onCreateFolder = viewModel::createFolderInBrowser,
                                onRenameItem = viewModel::renameFileItem,
                                onMoveItem = viewModel::moveFileItem,
                                onDeleteItem = viewModel::deleteFileItem
                            )
                        }

                        NavTab.PIP -> {
                            PipManagerScreen(
                                pipState = pipState,
                                userSitePackagesPath = viewModel.fileRepository.userSitePackagesDir.absolutePath,
                                onInstallPackage = viewModel::installOrUpgradePackage,
                                onUninstallPackage = viewModel::uninstallPackage,
                                onRefreshList = viewModel::refreshInstalledPackages
                            )
                        }

                        NavTab.SAMPLES -> {
                            SamplesScreen(
                                samples = viewModel.samples,
                                onOpenSample = viewModel::loadSample
                            )
                        }

                        NavTab.SETTINGS -> {
                            SettingsScreen(
                                settings = settings,
                                onThemeModeChanged = viewModel::setThemeMode,
                                onTerminalFullScreenChanged = viewModel::setTerminalFullScreen,
                                onEditorFontSizeChanged = viewModel::setEditorFontSize,
                                onTerminalFontSizeChanged = viewModel::setTerminalFontSize,
                                onTabWidthChanged = viewModel::setTabWidth,
                                onWordWrapChanged = viewModel::setWordWrap,
                                onShowLineNumbersChanged = viewModel::setShowLineNumbers,
                                onAutoSaveOnRunChanged = viewModel::setAutoSaveOnRun,
                                onCustomPipIndexChanged = viewModel::setCustomPipIndexUrl
                            )
                        }
                    }
                }

                // Bottom Split-Screen Terminal Dock (Only when terminalFullScreen == false)
                AnimatedVisibility(
                    visible = isTerminalExpanded && !settings.terminalFullScreen && selectedTab == NavTab.EDITOR,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    TerminalConsoleSheet(
                        snapshot = terminalSnapshot,
                        fontSizeSp = settings.terminalFontSizeSp,
                        isFullScreen = false,
                        isReplMode = isReplMode,
                        replContinuation = replContinuation,
                        onToggleFullScreen = viewModel::setTerminalFullScreen,
                        onToggleReplMode = viewModel::setReplMode,
                        onResetRepl = viewModel::resetReplSession,
                        onSubmitInput = viewModel::submitTerminalInput,
                        onJumpToLine = viewModel::requestJumpToLine,
                        onStopScript = viewModel::stopRunningScript,
                        onRerunScript = viewModel::runCurrentScript,
                        onClearTerminal = viewModel::clearTerminal,
                        onCloseSheet = { viewModel.setTerminalExpanded(false) },
                        modifier = Modifier.height(275.dp)
                    )
                }
            }
        }
    }

    // Built-in Zoomable Image / Plot / JSON / Markdown Previewer Dialog
    previewFile?.let { file ->
        FilePreviewDialog(
            file = file,
            onDismiss = viewModel::dismissPreviewFile
        )
    }
}
