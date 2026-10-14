package com.pydroidlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch

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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val showFullScreenTerminal = isTerminalExpanded && settings.terminalFullScreen
    val isImmersiveEditor = selectedTab == NavTab.EDITOR && !showFullScreenTerminal

    BackHandler(enabled = selectedTab != NavTab.EDITOR) {
        viewModel.selectTab(NavTab.EDITOR)
    }

    LaunchedEffect(editorState.statusBanner) {
        val msg = editorState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isImmersiveEditor,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RectangleShape,
                modifier = Modifier
                    .width(270.dp)
                    .fillMaxHeight()
            ) {
                // 1. Minimalist TinaIDE Drawer Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PyDroidLab",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Python ${editorState.runtimeContextInfo.pythonVersion}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(thickness = 0.5.dp)

                // 2. Clean Pure-Text Navigation Entries
                val drawerEntries = listOf(
                    NavTab.FILES to "项目文件",
                    NavTab.PIP to "扩展包管理",
                    NavTab.SAMPLES to "示例教程",
                    NavTab.SETTINGS to "环境设置"
                )
                drawerEntries.forEach { (tab, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                viewModel.selectTab(tab)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp)

                // 3. Minimalist Workspace File Tree
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileBrowserState.relativePath,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (fileBrowserState.currentDir.canonicalPath != viewModel.fileRepository.workspaceRoot.canonicalPath) {
                        IconButton(
                            onClick = { viewModel.navigateUpDirectory() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上级", modifier = Modifier.size(15.dp))
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.createNewScriptInEditor("untitled_${System.currentTimeMillis() % 1000}.py")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建", modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(fileBrowserState.items, key = { it.path }) { item ->
                        val isCurrentFile = editorState.activeFile?.canonicalPath == item.file.canonicalPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrentFile) MaterialTheme.colorScheme.surfaceVariant
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable {
                                    if (item.isDirectory) {
                                        viewModel.refreshFileBrowser(item.file)
                                    } else {
                                        scope.launch { drawerState.close() }
                                        viewModel.openFileInEditor(item.file)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrentFile) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrentFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!showFullScreenTerminal && selectedTab != NavTab.EDITOR) {
                    Column {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (selectedTab) {
                                        NavTab.FILES -> "项目"
                                        NavTab.PIP -> "市场"
                                        NavTab.SAMPLES -> "教程"
                                        NavTab.SETTINGS -> "设置"
                                        NavTab.EDITOR -> "编辑器"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.selectTab(NavTab.EDITOR) }) {
                                    Text("返回编辑器", fontSize = 13.sp)
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            },
            bottomBar = {
                if (!showFullScreenTerminal && selectedTab != NavTab.EDITOR) {
                    NavigationBar(tonalElevation = 0.dp) {
                        NavigationBarItem(
                            selected = selectedTab == NavTab.FILES,
                            onClick = { viewModel.selectTab(NavTab.FILES) },
                            icon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text("项目", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.PIP,
                            onClick = { viewModel.selectTab(NavTab.PIP) },
                            icon = { Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text("市场", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.SAMPLES,
                            onClick = { viewModel.selectTab(NavTab.SAMPLES) },
                            icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text("教程", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.SETTINGS,
                            onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text("设置", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { viewModel.selectTab(NavTab.EDITOR) },
                            icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text("编辑器", fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (showFullScreenTerminal) {
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
                    onRerunScript = { viewModel.runCurrentScript() },
                    onClearTerminal = viewModel::clearTerminal,
                    onCloseSheet = { viewModel.setTerminalExpanded(false) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
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
                                    onOpenDrawer = {
                                        viewModel.refreshFileBrowser(fileBrowserState.currentDir)
                                        scope.launch { drawerState.open() }
                                    },
                                    onSelectTab = viewModel::selectEditorTab,
                                    onCloseTab = viewModel::closeEditorTab,
                                    onTextChanged = viewModel::onEditorTextChanged,
                                    onCursorChanged = viewModel::onEditorCursorChanged,
                                    onFormatCode = viewModel::formatCurrentCode,
                                    onRunClicked = { viewModel.runCurrentScript() },
                                    onRunWithArgsClicked = { args -> viewModel.runCurrentScript(args) },
                                    onStopClicked = viewModel::stopRunningScript,
                                    onSaveClicked = { viewModel.saveCurrentFile() },
                                    onSaveAsClicked = viewModel::saveCurrentFileAs,
                                    onNewFileClicked = { name -> viewModel.createNewScriptInEditor(name) },
                                    onToggleFindReplace = viewModel::toggleFindReplaceBar,
                                    onUpdateFindReplace = viewModel::updateFindReplaceState,
                                    onToggleTerminal = {
                                        viewModel.setTerminalExpanded(!isTerminalExpanded)
                                    },
                                    onSelectIdeTheme = viewModel::setIdeColorTheme,
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
                                    onIdeColorThemeChanged = viewModel::setIdeColorTheme,
                                    onTerminalFullScreenChanged = viewModel::setTerminalFullScreen,
                                    onTwoRowSymbolBarChanged = viewModel::setTwoRowSymbolBar,
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
                            onRerunScript = { viewModel.runCurrentScript() },
                            onClearTerminal = viewModel::clearTerminal,
                            onCloseSheet = { viewModel.setTerminalExpanded(false) },
                            modifier = Modifier.height(260.dp)
                        )
                    }
                }
            }
        }
    }

    previewFile?.let { file ->
        FilePreviewDialog(
            file = file,
            sqliteInspector = viewModel.pythonExecutor::inspectSqliteDb,
            onDismiss = viewModel::dismissPreviewFile
        )
    }
}
