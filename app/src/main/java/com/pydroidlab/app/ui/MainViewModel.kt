package com.pydroidlab.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pydroidlab.app.data.AppSettings
import com.pydroidlab.app.data.CodeSample
import com.pydroidlab.app.data.FileRepository
import com.pydroidlab.app.data.SampleRepository
import com.pydroidlab.app.data.SettingsRepository
import com.pydroidlab.app.data.ThemeMode
import com.pydroidlab.app.data.WorkspaceFileItem
import com.pydroidlab.app.editor.IdeColorTheme
import com.pydroidlab.app.runtime.AndroidHardwareBridge
import com.pydroidlab.app.runtime.AstLinterWarning
import com.pydroidlab.app.runtime.AstOutlineSymbol
import com.pydroidlab.app.runtime.AstSyntaxDiagnostic
import com.pydroidlab.app.runtime.CodeMetrics
import com.pydroidlab.app.runtime.InstalledPackage
import com.pydroidlab.app.runtime.PythonExecutor
import com.pydroidlab.app.runtime.RuntimeContextInfo
import com.pydroidlab.terminal.TerminalEmulator
import com.pydroidlab.terminal.TerminalSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class NavTab(val label: String) {
    EDITOR("编辑器"),
    FILES("文件"),
    PIP("库管理"),
    SAMPLES("示例库"),
    SETTINGS("设置")
}

data class EditorTabItem(
    val id: String,
    val file: File,
    val title: String,
    val codeContent: String,
    val isModified: Boolean = false,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0
)

data class EditorUiState(
    val openTabs: List<EditorTabItem> = emptyList(),
    val activeTabId: String = "",
    val activeFile: File? = null,
    val fileName: String = "main.py",
    val codeContent: String = "",
    val contentVersion: Long = 0L,
    val isModified: Boolean = false,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showFindReplaceBar: Boolean = false,
    val findQuery: String = "",
    val replaceText: String = "",
    val caseSensitiveSearch: Boolean = false,
    val regexSearch: Boolean = false,
    val cliArgs: String = "",
    val syntaxDiagnostics: List<AstSyntaxDiagnostic> = emptyList(),
    val linterWarnings: List<AstLinterWarning> = emptyList(),
    val outlineSymbols: List<AstOutlineSymbol> = emptyList(),
    val codeMetrics: CodeMetrics = CodeMetrics(),
    val runtimeContextInfo: RuntimeContextInfo = RuntimeContextInfo(),
    val jumpToLineRequest: Pair<Int, Long>? = null,
    val statusBanner: String? = null
)

data class FileBrowserUiState(
    val currentDir: File,
    val relativePath: String = "/workspace",
    val items: List<WorkspaceFileItem> = emptyList(),
    val allDirectories: List<File> = emptyList(),
    val isLoading: Boolean = false
)

data class PipManagerUiState(
    val installedPackages: List<InstalledPackage> = emptyList(),
    val isBusy: Boolean = false,
    val currentActionLabel: String = "",
    val pipLogs: String = "",
    val lastResultBanner: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settingsRepository = SettingsRepository(application)
    val fileRepository = FileRepository(application)
    val terminalEmulator = TerminalEmulator()

    private val _previewFile = MutableStateFlow<File?>(null)
    val previewFile: StateFlow<File?> = _previewFile.asStateFlow()

    val hardwareBridge = AndroidHardwareBridge(
        context = application.applicationContext,
        onShowImageOnUi = { imgFile ->
            _previewFile.value = imgFile
        }
    )

    val pythonExecutor = PythonExecutor(
        terminal = terminalEmulator,
        workspaceDir = fileRepository.workspaceRoot,
        userSitePackagesDir = fileRepository.userSitePackagesDir,
        pipCacheDir = fileRepository.pipCacheDir,
        hardwareBridge = hardwareBridge
    )

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val terminalSnapshot: StateFlow<TerminalSnapshot> = terminalEmulator.snapshot

    private val _selectedTab = MutableStateFlow(NavTab.EDITOR)
    val selectedTab: StateFlow<NavTab> = _selectedTab.asStateFlow()

    private val _isTerminalExpanded = MutableStateFlow(false)
    val isTerminalExpanded: StateFlow<Boolean> = _isTerminalExpanded.asStateFlow()

    private val _isReplMode = MutableStateFlow(false)
    val isReplMode: StateFlow<Boolean> = _isReplMode.asStateFlow()

    private val _replContinuation = MutableStateFlow(false)
    val replContinuation: StateFlow<Boolean> = _replContinuation.asStateFlow()

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _fileBrowserState = MutableStateFlow(
        FileBrowserUiState(currentDir = fileRepository.workspaceRoot)
    )
    val fileBrowserState: StateFlow<FileBrowserUiState> = _fileBrowserState.asStateFlow()

    private val _pipState = MutableStateFlow(PipManagerUiState())
    val pipState: StateFlow<PipManagerUiState> = _pipState.asStateFlow()

    val samples: List<CodeSample> = SampleRepository.samples

    private var astAnalysisJob: Job? = null

    init {
        viewModelScope.launch {
            val defaultFile = fileRepository.ensureInitialWorkspace()
            openFileInEditor(defaultFile)
            refreshFileBrowser(fileRepository.workspaceRoot)
            refreshInstalledPackages()
            refreshRuntimeDiagnostics(invalidateCache = false)
        }
    }

    fun selectTab(tab: NavTab) {
        _selectedTab.value = tab
        if (tab == NavTab.FILES) {
            refreshFileBrowser(_fileBrowserState.value.currentDir)
        } else if (tab == NavTab.PIP && _pipState.value.installedPackages.isEmpty()) {
            refreshInstalledPackages()
        }
    }

    fun setTerminalExpanded(expanded: Boolean) {
        _isTerminalExpanded.value = expanded
    }

    fun setReplMode(enabled: Boolean) {
        _isReplMode.value = enabled
        if (enabled && terminalSnapshot.value.lines.isEmpty()) {
            terminalEmulator.writeSystemMessage(
                "Python 3 Interactive REPL 模式 (直接输入表达式回车即求值，如 2 ** 10 或 import this)",
                isError = false
            )
        }
    }

    fun resetReplSession() {
        _replContinuation.value = false
        pythonExecutor.resetReplSession(viewModelScope)
    }

    fun dismissPreviewFile() {
        _previewFile.value = null
    }

    // --- Multi-Tab & Editor Actions ---

    fun selectEditorTab(tabId: String) {
        val current = _editorState.value
        if (tabId == current.activeTabId) return
        val target = current.openTabs.find { it.id == tabId } ?: return
        _editorState.value = current.copy(
            activeTabId = target.id,
            activeFile = target.file,
            fileName = target.title,
            codeContent = target.codeContent,
            contentVersion = current.contentVersion + 1L,
            isModified = target.isModified,
            cursorLine = target.cursorLine,
            cursorColumn = target.cursorColumn
        )
        scheduleAstAnalysis(target.codeContent, target.title)
    }

    fun closeEditorTab(tabId: String) {
        val current = _editorState.value
        if (current.openTabs.size <= 1) return
        val idx = current.openTabs.indexOfFirst { it.id == tabId }
        if (idx == -1) return
        val remaining = current.openTabs.toMutableList().apply { removeAt(idx) }
        val nextActive = if (current.activeTabId == tabId) {
            remaining[idx.coerceAtMost(remaining.size - 1)]
        } else {
            remaining.find { it.id == current.activeTabId } ?: remaining.first()
        }
        _editorState.value = current.copy(
            openTabs = remaining,
            activeTabId = nextActive.id,
            activeFile = nextActive.file,
            fileName = nextActive.title,
            codeContent = nextActive.codeContent,
            contentVersion = current.contentVersion + 1L,
            isModified = nextActive.isModified,
            cursorLine = nextActive.cursorLine,
            cursorColumn = nextActive.cursorColumn
        )
        scheduleAstAnalysis(nextActive.codeContent, nextActive.title)
    }

    fun onEditorTextChanged(
        newText: String,
        cursorLine: Int,
        cursorColumn: Int,
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        val current = _editorState.value
        val changed = newText != current.codeContent
        val newModified = if (changed) true else current.isModified
        val updatedTabs = current.openTabs.map { tab ->
            if (tab.id == current.activeTabId) {
                tab.copy(
                    codeContent = newText,
                    isModified = newModified,
                    cursorLine = cursorLine,
                    cursorColumn = cursorColumn
                )
            } else {
                tab
            }
        }
        _editorState.value = current.copy(
            openTabs = updatedTabs,
            codeContent = newText,
            isModified = newModified,
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            canUndo = canUndo,
            canRedo = canRedo
        )
        if (changed) {
            scheduleAstAnalysis(newText, current.fileName)
        }
    }

    fun onEditorCursorChanged(
        cursorLine: Int,
        cursorColumn: Int,
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        val current = _editorState.value
        val updatedTabs = current.openTabs.map { tab ->
            if (tab.id == current.activeTabId) {
                tab.copy(cursorLine = cursorLine, cursorColumn = cursorColumn)
            } else {
                tab
            }
        }
        _editorState.value = current.copy(
            openTabs = updatedTabs,
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            canUndo = canUndo,
            canRedo = canRedo
        )
    }

    private fun scheduleAstAnalysis(code: String, fileName: String) {
        astAnalysisJob?.cancel()
        astAnalysisJob = viewModelScope.launch {
            delay(300L)
            val analysis = pythonExecutor.analyzeCodeAst(code, fileName)
            _editorState.value = _editorState.value.copy(
                syntaxDiagnostics = analysis.diagnostics,
                linterWarnings = analysis.warnings,
                outlineSymbols = analysis.outline,
                codeMetrics = analysis.metrics
            )
        }
    }

    fun refreshRuntimeDiagnostics(invalidateCache: Boolean = false) {
        viewModelScope.launch {
            val info = pythonExecutor.getRuntimeDiagnostics(invalidateCache = invalidateCache)
            _editorState.value = _editorState.value.copy(
                runtimeContextInfo = info,
                statusBanner = if (invalidateCache) "已清理并重载 Python 模块缓存" else _editorState.value.statusBanner
            )
        }
    }

    fun requestJumpToLine(oneBasedLine: Int) {
        val targetZeroBased = (oneBasedLine - 1).coerceAtLeast(0)
        if (settings.value.terminalFullScreen) {
            _isTerminalExpanded.value = false
        }
        _selectedTab.value = NavTab.EDITOR
        _editorState.value = _editorState.value.copy(
            jumpToLineRequest = targetZeroBased to System.currentTimeMillis()
        )
    }

    fun consumeJumpToLine() {
        _editorState.value = _editorState.value.copy(jumpToLineRequest = null)
    }

    fun toggleFindReplaceBar() {
        val current = _editorState.value
        _editorState.value = current.copy(showFindReplaceBar = !current.showFindReplaceBar)
    }

    fun updateFindReplaceState(
        findQuery: String = _editorState.value.findQuery,
        replaceText: String = _editorState.value.replaceText,
        caseSensitive: Boolean = _editorState.value.caseSensitiveSearch,
        regex: Boolean = _editorState.value.regexSearch
    ) {
        _editorState.value = _editorState.value.copy(
            findQuery = findQuery,
            replaceText = replaceText,
            caseSensitiveSearch = caseSensitive,
            regexSearch = regex
        )
    }

    fun clearStatusBanner() {
        _editorState.value = _editorState.value.copy(statusBanner = null)
    }

    fun openFileInEditor(file: File) {
        val ext = file.extension.lowercase()
        // If it's an image or SQLite DB file, pop up the built-in Visualizer Dialog!
        if (ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "db", "sqlite", "sqlite3")) {
            _previewFile.value = file
            return
        }

        viewModelScope.launch {
            fileRepository.readFile(file)
                .onSuccess { content ->
                    val tabId = file.canonicalPath
                    val current = _editorState.value
                    val existingIdx = current.openTabs.indexOfFirst { it.id == tabId }
                    val newTab = EditorTabItem(
                        id = tabId,
                        file = file,
                        title = file.name,
                        codeContent = content,
                        isModified = false,
                        cursorLine = 0,
                        cursorColumn = 0
                    )
                    val updatedTabs = if (existingIdx >= 0) {
                        current.openTabs.toMutableList().apply { set(existingIdx, newTab) }
                    } else {
                        current.openTabs + newTab
                    }
                    _editorState.value = current.copy(
                        openTabs = updatedTabs,
                        activeTabId = tabId,
                        activeFile = file,
                        fileName = file.name,
                        codeContent = content,
                        contentVersion = current.contentVersion + 1L,
                        isModified = false,
                        cursorLine = 0,
                        cursorColumn = 0,
                        statusBanner = null
                    )
                    _selectedTab.value = NavTab.EDITOR
                    scheduleAstAnalysis(content, file.name)
                }
                .onFailure { e ->
                    _editorState.value = _editorState.value.copy(
                        statusBanner = "打开失败: ${e.localizedMessage}"
                    )
                }
        }
    }

    fun createNewScriptInEditor(
        fileName: String,
        initialCode: String = "# -*- coding: utf-8 -*-\n\nprint(\"Hello from PyDroidLab!\")\n"
    ) {
        viewModelScope.launch {
            val parent = _fileBrowserState.value.currentDir
            fileRepository.createNewFile(parent, fileName, initialCode)
                .onSuccess { created ->
                    refreshFileBrowser(parent)
                    openFileInEditor(created)
                }
                .onFailure { e ->
                    _editorState.value = _editorState.value.copy(
                        statusBanner = "新建失败: ${e.localizedMessage}"
                    )
                }
        }
    }

    fun saveCurrentFile(onSaved: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            val state = _editorState.value
            val targetFile = state.activeFile ?: File(fileRepository.workspaceRoot, state.fileName)
            fileRepository.writeFile(targetFile, state.codeContent)
                .onSuccess {
                    val updatedTabs = state.openTabs.map { tab ->
                        if (tab.id == state.activeTabId) tab.copy(isModified = false) else tab
                    }
                    _editorState.value = state.copy(
                        openTabs = updatedTabs,
                        activeFile = targetFile,
                        fileName = targetFile.name,
                        isModified = false,
                        statusBanner = "已保存 ${targetFile.name}"
                    )
                    refreshFileBrowser(_fileBrowserState.value.currentDir)
                    onSaved?.invoke(targetFile)
                }
                .onFailure { e ->
                    _editorState.value = _editorState.value.copy(
                        statusBanner = "保存失败: ${e.localizedMessage}"
                    )
                }
        }
    }

    fun saveCurrentFileAs(newFileName: String) {
        viewModelScope.launch {
            val clean = newFileName.trim().let { if (it.endsWith(".py")) it else "$it.py" }
            val parent = _editorState.value.activeFile?.parentFile ?: fileRepository.workspaceRoot
            val target = File(parent, clean)
            fileRepository.writeFile(target, _editorState.value.codeContent)
                .onSuccess {
                    openFileInEditor(target)
                    refreshFileBrowser(_fileBrowserState.value.currentDir)
                }
                .onFailure { e ->
                    _editorState.value = _editorState.value.copy(
                        statusBanner = "另存为失败: ${e.localizedMessage}"
                    )
                }
        }
    }

    fun formatCurrentCode(onFormattedResult: (String) -> Unit) {
        val current = _editorState.value
        viewModelScope.launch {
            val formatted = pythonExecutor.formatPythonCode(current.codeContent, settings.value.tabWidth)
            if (formatted != current.codeContent) {
                onFormattedResult(formatted)
                _editorState.value = _editorState.value.copy(
                    statusBanner = "✨ 代码已按 PEP 8 规范格式化"
                )
            } else {
                _editorState.value = _editorState.value.copy(
                    statusBanner = "代码格式已符合规范"
                )
            }
        }
    }

    // --- Run, Interactive Input & REPL Actions ---

    fun runCurrentScript(customCliArgs: String? = null) {
        val currentEditor = _editorState.value
        val argsToUse = customCliArgs ?: currentEditor.cliArgs
        if (customCliArgs != null) {
            _editorState.value = currentEditor.copy(cliArgs = customCliArgs)
        }
        val codeToRun = currentEditor.codeContent
        val scriptFile = currentEditor.activeFile ?: File(fileRepository.workspaceRoot, currentEditor.fileName)

        if (settings.value.autoSaveOnRun) {
            viewModelScope.launch {
                fileRepository.writeFile(scriptFile, codeToRun)
                val updatedTabs = currentEditor.openTabs.map { tab ->
                    if (tab.id == currentEditor.activeTabId) tab.copy(isModified = false) else tab
                }
                _editorState.value = _editorState.value.copy(
                    openTabs = updatedTabs,
                    activeFile = scriptFile,
                    isModified = false
                )
            }
        }

        _isReplMode.value = false
        _isTerminalExpanded.value = true
        pythonExecutor.runScript(
            scope = viewModelScope,
            code = codeToRun,
            scriptFile = scriptFile,
            cliArgs = argsToUse
        )
    }

    fun stopRunningScript() {
        pythonExecutor.stopRunningScript()
    }

    fun submitTerminalInput(inputLine: String) {
        if (_isReplMode.value && !terminalSnapshot.value.isRunning) {
            pythonExecutor.evalReplLine(
                scope = viewModelScope,
                line = inputLine,
                isContinuation = _replContinuation.value,
                onResult = { moreNeeded ->
                    _replContinuation.value = moreNeeded
                }
            )
        } else {
            terminalEmulator.submitUserInput(inputLine)
        }
    }

    fun clearTerminal() {
        terminalEmulator.clearScreen()
    }

    // --- Sample Library Actions ---

    fun loadSample(sample: CodeSample, runImmediately: Boolean) {
        viewModelScope.launch {
            val examplesDir = File(fileRepository.workspaceRoot, "examples").apply { mkdirs() }
            val sampleFile = File(examplesDir, sample.fileName)
            fileRepository.writeFile(sampleFile, sample.code)
            val tabId = sampleFile.canonicalPath
            val current = _editorState.value
            val newTab = EditorTabItem(
                id = tabId,
                file = sampleFile,
                title = sampleFile.name,
                codeContent = sample.code,
                isModified = false
            )
            val existingIdx = current.openTabs.indexOfFirst { it.id == tabId }
            val updatedTabs = if (existingIdx >= 0) {
                current.openTabs.toMutableList().apply { set(existingIdx, newTab) }
            } else {
                current.openTabs + newTab
            }

            _editorState.value = current.copy(
                openTabs = updatedTabs,
                activeTabId = tabId,
                activeFile = sampleFile,
                fileName = sampleFile.name,
                codeContent = sample.code,
                contentVersion = current.contentVersion + 1L,
                isModified = false,
                cursorLine = 0,
                cursorColumn = 0,
                statusBanner = null
            )
            _selectedTab.value = NavTab.EDITOR
            scheduleAstAnalysis(sample.code, sampleFile.name)

            if (runImmediately) {
                _isReplMode.value = false
                _isTerminalExpanded.value = true
                pythonExecutor.runScript(
                    scope = viewModelScope,
                    code = sample.code,
                    scriptFile = sampleFile
                )
            }
        }
    }

    // --- File Manager Actions ---

    fun refreshFileBrowser(directory: File = _fileBrowserState.value.currentDir) {
        viewModelScope.launch {
            _fileBrowserState.value = _fileBrowserState.value.copy(isLoading = true)
            val items = fileRepository.listDirectory(directory)
            val allDirs = fileRepository.listAllDirectories()
            _fileBrowserState.value = FileBrowserUiState(
                currentDir = directory,
                relativePath = fileRepository.relativePath(directory),
                items = items,
                allDirectories = allDirs,
                isLoading = false
            )
        }
    }

    fun navigateUpDirectory(): Boolean {
        val current = _fileBrowserState.value.currentDir
        val root = fileRepository.workspaceRoot
        if (current.canonicalPath == root.canonicalPath) return false
        val parent = current.parentFile ?: root
        refreshFileBrowser(parent)
        return true
    }

    fun createFileInBrowser(name: String) {
        viewModelScope.launch {
            val dir = _fileBrowserState.value.currentDir
            fileRepository.createNewFile(
                dir,
                name,
                "# -*- coding: utf-8 -*-\n\n"
            ).onSuccess {
                refreshFileBrowser(dir)
            }.onFailure { e ->
                _editorState.value = _editorState.value.copy(statusBanner = e.localizedMessage)
            }
        }
    }

    fun createFolderInBrowser(name: String) {
        viewModelScope.launch {
            val dir = _fileBrowserState.value.currentDir
            fileRepository.createNewFolder(dir, name).onSuccess {
                refreshFileBrowser(dir)
            }.onFailure { e ->
                _editorState.value = _editorState.value.copy(statusBanner = e.localizedMessage)
            }
        }
    }

    fun renameFileItem(item: WorkspaceFileItem, newName: String) {
        viewModelScope.launch {
            fileRepository.renameItem(item.file, newName).onSuccess { renamed ->
                if (_editorState.value.activeFile?.canonicalPath == item.file.canonicalPath) {
                    _editorState.value = _editorState.value.copy(
                        activeFile = renamed,
                        fileName = renamed.name
                    )
                }
                refreshFileBrowser(_fileBrowserState.value.currentDir)
            }.onFailure { e ->
                _editorState.value = _editorState.value.copy(statusBanner = e.localizedMessage)
            }
        }
    }

    fun moveFileItem(item: WorkspaceFileItem, targetDir: File) {
        viewModelScope.launch {
            fileRepository.moveItem(item.file, targetDir).onSuccess { moved ->
                if (_editorState.value.activeFile?.canonicalPath == item.file.canonicalPath) {
                    _editorState.value = _editorState.value.copy(
                        activeFile = moved,
                        fileName = moved.name
                    )
                }
                refreshFileBrowser(_fileBrowserState.value.currentDir)
            }.onFailure { e ->
                _editorState.value = _editorState.value.copy(statusBanner = e.localizedMessage)
            }
        }
    }

    fun deleteFileItem(item: WorkspaceFileItem) {
        viewModelScope.launch {
            fileRepository.deleteItem(item.file).onSuccess {
                refreshFileBrowser(_fileBrowserState.value.currentDir)
            }.onFailure { e ->
                _editorState.value = _editorState.value.copy(statusBanner = e.localizedMessage)
            }
        }
    }

    // --- Pip Package Manager Actions ---

    fun refreshInstalledPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            val pkgs = pythonExecutor.listInstalledPackages()
            _pipState.value = _pipState.value.copy(installedPackages = pkgs)
        }
    }

    fun installOrUpgradePackage(packageSpec: String, upgrade: Boolean = false) {
        if (_pipState.value.isBusy) return
        val cleanSpec = packageSpec.trim()
        if (cleanSpec.isEmpty()) return

        viewModelScope.launch {
            _pipState.value = _pipState.value.copy(
                isBusy = true,
                currentActionLabel = if (upgrade) "正在升级 $cleanSpec ..." else "正在安装 $cleanSpec ...",
                pipLogs = "",
                lastResultBanner = null
            )
            val result = withContext(Dispatchers.IO) {
                pythonExecutor.installOrUpgradePackage(
                    packageSpec = cleanSpec,
                    upgrade = upgrade,
                    customIndexUrl = settings.value.customPipIndexUrl,
                    onLog = { chunk ->
                        _pipState.value = _pipState.value.copy(
                            pipLogs = _pipState.value.pipLogs + chunk
                        )
                    }
                )
            }
            val updatedList = withContext(Dispatchers.IO) {
                pythonExecutor.listInstalledPackages()
            }
            _pipState.value = _pipState.value.copy(
                installedPackages = updatedList,
                isBusy = false,
                currentActionLabel = "",
                lastResultBanner = result.message
            )
        }
    }

    fun uninstallPackage(packageName: String) {
        if (_pipState.value.isBusy) return
        viewModelScope.launch {
            _pipState.value = _pipState.value.copy(
                isBusy = true,
                currentActionLabel = "正在卸载 $packageName ...",
                pipLogs = "",
                lastResultBanner = null
            )
            val result = withContext(Dispatchers.IO) {
                pythonExecutor.uninstallPackage(
                    packageName = packageName,
                    onLog = { chunk ->
                        _pipState.value = _pipState.value.copy(
                            pipLogs = _pipState.value.pipLogs + chunk
                        )
                    }
                )
            }
            val updatedList = withContext(Dispatchers.IO) {
                pythonExecutor.listInstalledPackages()
            }
            _pipState.value = _pipState.value.copy(
                installedPackages = updatedList,
                isBusy = false,
                currentActionLabel = "",
                lastResultBanner = result.message
            )
        }
    }

    // --- Settings Actions ---

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setIdeColorTheme(theme: IdeColorTheme) {
        viewModelScope.launch {
            settingsRepository.setIdeColorThemeId(theme.id)
            settingsRepository.setThemeMode(if (theme.isDark) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    fun setTerminalFullScreen(fullScreen: Boolean) {
        viewModelScope.launch { settingsRepository.setTerminalFullScreen(fullScreen) }
    }

    fun setTwoRowSymbolBar(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTwoRowSymbolBar(enabled) }
    }

    fun setEditorFontSize(sizeSp: Float) {
        viewModelScope.launch { settingsRepository.setEditorFontSize(sizeSp) }
    }

    fun setTerminalFontSize(sizeSp: Float) {
        viewModelScope.launch { settingsRepository.setTerminalFontSize(sizeSp) }
    }

    fun setTabWidth(width: Int) {
        viewModelScope.launch { settingsRepository.setTabWidth(width) }
    }

    fun setWordWrap(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWordWrap(enabled) }
    }

    fun setShowLineNumbers(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowLineNumbers(enabled) }
    }

    fun setAutoSaveOnRun(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSaveOnRun(enabled) }
    }

    fun setCustomPipIndexUrl(url: String) {
        viewModelScope.launch { settingsRepository.setCustomPipIndexUrl(url) }
    }
}
