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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Pressing Back when on a non-editor tab returns to the immersive code editor
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
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                // 1. TinaIDE-style Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "PyDroidLab IDE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Python 3.10 • 工作区文件树与导航",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Quick Navigation Entries (项目页 / 市场 / 教程 / 设置 / 终端)
                val navItems = listOf(
                    Triple(NavTab.FILES, "项目工作区 (Files)", Icons.Default.Folder),
                    Triple(NavTab.PIP, "pip 扩展包市场 (Market)", Icons.Default.Extension),
                    Triple(NavTab.SAMPLES, "Python 实战教程 (Tutorials)", Icons.Default.MenuBook),
                    Triple(NavTab.SETTINGS, "IDE 环境设置 (Settings)", Icons.Default.Settings)
                )
                navItems.forEach { (tab, title, icon) ->
                    NavigationDrawerItem(
                        label = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                        selected = selectedTab == tab,
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp)) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.selectTab(tab)
                        },
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .height(42.dp)
                    )
                }

                NavigationDrawerItem(
                    label = { Text("全屏交互终端 (Termux Console)", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    selected = showFullScreenTerminal,
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(19.dp)) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.setTerminalExpanded(true)
                    },
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .height(42.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 3. Embedded Live Project File Tree inside Drawer (TinaIDE style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileBrowserState.relativePath,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (fileBrowserState.currentDir.canonicalPath != viewModel.fileRepository.workspaceRoot.canonicalPath) {
                        IconButton(
                            onClick = { viewModel.navigateUpDirectory() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "上级目录", modifier = Modifier.size(15.dp))
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.createNewScriptInEditor("script_${System.currentTimeMillis() % 1000}.py")
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "快速新建脚本", modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    items(fileBrowserState.items, key = { it.path }) { item ->
                        val isCurrentFile = editorState.activeFile?.canonicalPath == item.file.canonicalPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isCurrentFile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
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
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrentFile) FontWeight.Bold else FontWeight.Normal,
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
                // When on TinaIDE Home tabs (项目 / 市场 / 教程 / 设置), show a sleek top bar with "返回编辑器" button
                if (!showFullScreenTerminal && selectedTab != NavTab.EDITOR) {
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (selectedTab) {
                                    NavTab.FILES -> "项目工作区"
                                    NavTab.PIP -> "pip 扩展包市场"
                                    NavTab.SAMPLES -> "Python 实战教程"
                                    NavTab.SETTINGS -> "IDE 环境设置"
                                    NavTab.EDITOR -> "编辑器"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.selectTab(NavTab.EDITOR) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("返回代码编辑器 (${editorState.fileName})", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // TinaIDE Architecture:
                // - In Code Editor or Full-Screen Terminal: NO bottom navigation bar! 100% immersive!
                // - In Home tabs (项目 / 市场 / 教程 / 设置): 4-tab TinaIDE bottom bar + 编辑器 entry
                if (!showFullScreenTerminal && selectedTab != NavTab.EDITOR) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = false,
                            onClick = { viewModel.selectTab(NavTab.EDITOR) },
                            icon = { Icon(Icons.Default.Code, contentDescription = null) },
                            label = { Text("编辑器") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.FILES,
                            onClick = { viewModel.selectTab(NavTab.FILES) },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text("项目") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.PIP,
                            onClick = { viewModel.selectTab(NavTab.PIP) },
                            icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                            label = { Text("市场") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.SAMPLES,
                            onClick = { viewModel.selectTab(NavTab.SAMPLES) },
                            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                            label = { Text("教程") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == NavTab.SETTINGS,
                            onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("设置") }
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
                    onRerunScript = viewModel::runCurrentScript,
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
    }

    // Built-in Zoomable Image / Plot / JSON / Markdown Previewer Dialog
    previewFile?.let { file ->
        FilePreviewDialog(
            file = file,
            onDismiss = viewModel::dismissPreviewFile
        )
    }
}
