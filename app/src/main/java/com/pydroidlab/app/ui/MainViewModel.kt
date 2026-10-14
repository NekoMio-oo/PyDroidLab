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
import com.pydroidlab.app.runtime.InstalledPackage
import com.pydroidlab.app.runtime.PythonExecutor
import com.pydroidlab.terminal.TerminalEmulator
import com.pydroidlab.terminal.TerminalSnapshot
import kotlinx.coroutines.Dispatchers
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

data class EditorUiState(
    val activeFile: File? = null,
    val fileName: String = "main.py",
    val codeContent: String = "",
    val contentVersion: Long = 0L, // Incremented only on external file load / sample load
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

/**
 * Survives screen rotation (portrait <-> landscape) so editor buffer, cursor position,
 * running Python script, and interactive terminal session are never lost.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settingsRepository = SettingsRepository(application)
    val fileRepository = FileRepository(application)
    val terminalEmulator = TerminalEmulator()
    val pythonExecutor = PythonExecutor(
        terminal = terminalEmulator,
        workspaceDir = fileRepository.workspaceRoot,
        userSitePackagesDir = fileRepository.userSitePackagesDir,
        pipCacheDir = fileRepository.pipCacheDir
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

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _fileBrowserState = MutableStateFlow(
        FileBrowserUiState(currentDir = fileRepository.workspaceRoot)
    )
    val fileBrowserState: StateFlow<FileBrowserUiState> = _fileBrowserState.asStateFlow()

    private val _pipState = MutableStateFlow(PipManagerUiState())
    val pipState: StateFlow<PipManagerUiState> = _pipState.asStateFlow()

    val samples: List<CodeSample> = SampleRepository.samples

    init {
        viewModelScope.launch {
            val defaultFile = fileRepository.ensureInitialWorkspace()
            openFileInEditor(defaultFile)
            refreshFileBrowser(fileRepository.workspaceRoot)
            refreshInstalledPackages()
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

    // --- Editor Actions ---

    fun onEditorTextChanged(
        newText: String,
        cursorLine: Int,
        cursorColumn: Int,
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        val current = _editorState.value
        val changed = newText != current.codeContent
        _editorState.value = current.copy(
            codeContent = newText,
            isModified = if (changed) true else current.isModified,
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            canUndo = canUndo,
            canRedo = canRedo
        )
    }

    fun onEditorCursorChanged(
        cursorLine: Int,
        cursorColumn: Int,
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        _editorState.value = _editorState.value.copy(
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            canUndo = canUndo,
            canRedo = canRedo
        )
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
        viewModelScope.launch {
            fileRepository.readFile(file)
                .onSuccess { content ->
                    _editorState.value = _editorState.value.copy(
                        activeFile = file,
                        fileName = file.name,
                        codeContent = content,
                        contentVersion = _editorState.value.contentVersion + 1L,
                        isModified = false,
                        cursorLine = 0,
                        cursorColumn = 0,
                        statusBanner = "已打开 ${file.name}"
                    )
                    _selectedTab.value = NavTab.EDITOR
                }
                .onFailure { e ->
                    _editorState.value = _editorState.value.copy(
                        statusBanner = "打开失败: ${e.localizedMessage}"
                    )
                }
        }
    }

    fun createNewScriptInEditor(fileName: String, initialCode: String = "# -*- coding: utf-8 -*-\n\nprint(\"Hello from PyDroidLab!\")\n") {
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
                    _editorState.value = _editorState.value.copy(
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
                    _editorState.value = _editorState.value.copy(
                        activeFile = target,
                        fileName = target.name,
                        isModified = false,
                        statusBanner = "已另存为 ${target.name}"
                    )
                    refreshFileBrowser(_fileBrowserState.value.currentDir)
                }
                .onFailure { e ->
                    _editorState.value = _editorState.value.copy(
                        statusBanner = "另存为失败: ${e.localizedMessage}"
                    )
                }
        }
    }

    // --- Run & Interactive Terminal Actions ---

    fun runCurrentScript() {
        val currentEditor = _editorState.value
        val codeToRun = currentEditor.codeContent
        val scriptFile = currentEditor.activeFile ?: File(fileRepository.workspaceRoot, currentEditor.fileName)

        if (settings.value.autoSaveOnRun) {
            viewModelScope.launch {
                fileRepository.writeFile(scriptFile, codeToRun)
                _editorState.value = _editorState.value.copy(
                    activeFile = scriptFile,
                    isModified = false
                )
            }
        }

        _isTerminalExpanded.value = true
        pythonExecutor.runScript(
            scope = viewModelScope,
            code = codeToRun,
            scriptFile = scriptFile
        )
    }

    fun stopRunningScript() {
        pythonExecutor.stopRunningScript()
    }

    fun submitTerminalInput(inputLine: String) {
        terminalEmulator.submitUserInput(inputLine)
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
            _editorState.value = _editorState.value.copy(
                activeFile = sampleFile,
                fileName = sampleFile.name,
                codeContent = sample.code,
                contentVersion = _editorState.value.contentVersion + 1L,
                isModified = false,
                cursorLine = 0,
                cursorColumn = 0,
                statusBanner = "已加载示例：${sample.title}"
            )
            _selectedTab.value = NavTab.EDITOR
            if (runImmediately) {
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
