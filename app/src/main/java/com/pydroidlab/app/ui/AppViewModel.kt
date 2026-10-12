package com.pydroidlab.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.pydroidlab.app.PyDroidLabApp
import com.pydroidlab.app.core.Chart
import com.pydroidlab.app.core.EventBus
import com.pydroidlab.app.core.ServiceLocator
import com.pydroidlab.app.project.Project
import com.pydroidlab.app.python.HostBridge
import com.pydroidlab.app.python.PipMirror
import com.pydroidlab.app.python.RunConfig
import com.pydroidlab.app.python.RunConfigStore
import com.pydroidlab.app.python.RunOptions
import com.pydroidlab.app.ui.editor.EditorState
import com.pydroidlab.app.ui.editor.EditorTab
import com.pydroidlab.app.ui.settings.AppSettings
import com.pydroidlab.app.ui.settings.EditorSettings
import com.pydroidlab.app.ui.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** 控制台里的一行（或一段未换行的文本）。 */
data class ConsoleLine(val text: String, val isError: Boolean)

/**
 * 全局共享状态：所有屏幕都从这一个 ViewModel 读写。
 *
 * 之所以放在 ViewModel 而不是各个 Composable 里，是为了让
 * 「横竖屏旋转不丢编辑内容」天然成立 —— ViewModel 在配置变更时不会重建，
 * 而 [EditorState] 里持有的 sora `Content` 实例也随之一并保留。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val services: ServiceLocator = (app as PyDroidLabApp).services

    private val runConfigStore = RunConfigStore(app)

    // ---------------------------------------------------------------- 导航 --
    /** 屏幕请求跳转的目标路由；UI 消费一次后置空。 */
    var navigateTo by mutableStateOf<String?>(null)
        private set

    fun requestNavigate(route: String) {
        navigateTo = route
    }

    fun consumeNavigate() {
        navigateTo = null
    }

    /**
     * 快捷键 / 外部入口发给「编辑器」页的一次性命令。
     *
     * sora-editor 会把「Ctrl + 字母」全部吃掉（KeyBindingEvent），
     * 所以应用级快捷键只能在 Activity 里拦截，再通过这里转成编辑器动作。
     */
    var pendingEditorCommand by mutableStateOf<String?>(null)
        private set

    fun requestEditorCommand(command: String) {
        pendingEditorCommand = command
        requestNavigate(ROUTE_EDITOR)
    }

    fun consumeEditorCommand() {
        pendingEditorCommand = null
    }

    /** 顶部 Snackbar 提示。 */
    var notice by mutableStateOf<String?>(null)
        private set

    fun postNotice(msg: String) {
        notice = msg
    }

    fun consumeNotice() {
        notice = null
    }

    // -------------------------------------------------------------- 设置 --
    var settings by mutableStateOf(AppSettings())
        private set

    // ------------------------------------------------------------ 编辑器 --
    val editor = EditorState()

    /** 每次标签集合 / 脏标记变化时自增，用于触发 Compose 重组。 */
    var editorRevision by mutableStateOf(0)
        private set

    fun touchEditor() {
        editorRevision++
    }

    /** 当前活动标签绑定的文件（未保存的新标签为 null）。 */
    val activeFile: File? get() = editor.active?.file

    fun openFile(file: File) {
        if (!file.exists()) {
            postNotice("文件不存在：${file.name}")
            return
        }
        val tab = editor.openFile(file) { services.recentFiles.add(it) }
        tab.loadFromFile()
        currentDir = file.parentFile ?: currentDir
        refreshRecent()
        refreshCompletion(tab.content.toString())
        touchEditor()
        requestNavigate(ROUTE_EDITOR)
    }

    fun newTab(template: String = "") {
        editor.newUntitled(template)
        touchEditor()
        requestNavigate(ROUTE_EDITOR)
    }

    /** 处理从其它 App 分享 / 打开进来的 .py 文件。 */
    fun openIncoming(uri: Uri) {
        viewModelScope.launch {
            val target: File? = when (uri.scheme) {
                "file" -> uri.path?.let { File(it) }
                else -> {
                    val dir = sharedDir()
                    services.projectManager.importFromSaf(uri, dir)
                }
            }
            if (target != null && target.exists()) {
                openFile(target)
            } else {
                postNotice("无法读取分享的文件")
            }
        }
    }

    fun openIncomingText(text: String) {
        val stripped = text.trim()
        if (stripped.isEmpty()) return
        val tab = editor.tabs.firstOrNull { it.file == null }
        val target = tab ?: editor.newUntitled()
        setTabText(target, stripped)
        target.isDirty = true
        touchEditor()
        requestNavigate(ROUTE_EDITOR)
    }

    private fun sharedDir(): File =
        File(getApplication<Application>().filesDir, "shared").apply { mkdirs() }

    fun saveActive(then: (() -> Unit)? = null): Boolean {
        val tab = editor.active ?: return false
        if (tab.file == null) {
            postNotice("未命名文件请先使用「另存为」")
            return false
        }
        applyFormatIfNeeded(tab)
        tab.save()
        refreshCompletion(tab.content.toString())
        touchEditor()
        postNotice("已保存 ${tab.file?.name}")
        then?.invoke()
        return true
    }

    fun saveAs(tab: EditorTab, name: String, dir: File): File? {
        val clean = name.trim().ifEmpty { "untitled.py" }
        val withExt = if (clean.endsWith(".py")) clean else "$clean.py"
        val target = File(dir, withExt)
        editor.saveTabAs(tab, target)
        services.recentFiles.add(target.absolutePath)
        refreshRecent()
        touchEditor()
        postNotice("已保存到 ${target.absolutePath}")
        return target
    }

    fun closeTab(index: Int) {
        editor.close(index)
        touchEditor()
    }

    /**
     * 就地改写标签的文本。
     *
     * 关键：**不能**用 `tab.content = Content(newText)` 整体替换对象 ——
     * sora 的 `CodeEditor.setText(Content)` 会复用传入的 Content 实例
     * （reuseContentObject = true），编辑器此后一直绑定在那个对象上。
     * 一旦换成新对象，编辑器与文件内容就会分叉：屏幕上是旧文本，
     * 保存下去的是新文本。因此这里对同一个 Content 做区间替换。
     */
    private fun setTabText(tab: EditorTab, newText: String) {
        val content = tab.content
        val lastLine = (content.lineCount - 1).coerceAtLeast(0)
        val lastColumn = content.getColumnCount(lastLine)
        content.replace(0, 0, lastLine, lastColumn, newText)
    }

    private fun applyFormatIfNeeded(tab: EditorTab) {
        if (!settings.editor.formatOnSave) return
        val src = tab.content.toString()
        val formatted = formatBlocking(src)
        if (formatted != null && formatted != src) {
            setTabText(tab, formatted)
            tab.isDirty = true
        }
    }

    private fun formatBlocking(src: String): String? = try {
        Python.getInstance().getModule("pdl_format").callAttr("format_code", src).toString()
    } catch (t: Throwable) {
        null
    }

    /** 用 black 格式化指定源码；失败时原样返回。 */
    suspend fun formatCode(src: String): String = withContext(Dispatchers.IO) {
        formatBlocking(src) ?: src
    }

    fun formatActiveTab() {
        val tab = editor.active ?: return
        viewModelScope.launch {
            val src = tab.content.toString()
            val out = formatCode(src)
            if (out != src) {
                setTabText(tab, out)
                tab.isDirty = true
                touchEditor()
                postNotice("已用 black 格式化")
            } else {
                postNotice("源码无需格式化（或 black 不可用）")
            }
        }
    }

    // ------------------------------------------------------------ 补全缓存 --
    private val memberCache = ConcurrentHashMap<String, List<String>>()
    private var importNames: List<String> = emptyList()
    private var memberCacheLoadedFor: String? = null

    /** 供补全用的「文件内可直接使用的名字」（from x import a, b）。 */
    fun completionNames(): List<String> = importNames

    /** 供 `模块.` 前缀补全用的成员列表（命中缓存，同步返回）。 */
    fun moduleMembers(module: String): List<String> =
        memberCache[module] ?: memberCache[module.substringBefore('.')] ?: emptyList()

    /**
     * 扫描源码里的 import，把模块成员读进缓存。
     * 只在「打开 / 保存 / 运行」时调用，不在每次按键时调用。
     */
    fun refreshCompletion(source: String) {
        if (source.isBlank()) {
            memberCache.clear()
            importNames = emptyList()
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val mod = Python.getInstance().getModule("pdl_complete")
                    val modules = mod.callAttr("imported_modules", source)
                        .asList().map { it.toString() }
                        .filter { it.isNotBlank() }
                        .take(MAX_COMPLETION_MODULES)
                    val names = mod.callAttr("from_import_names", source)
                        .asList().map { it.toString() }
                    val members = if (modules.isEmpty()) {
                        emptyMap<String, List<String>>()
                    } else {
                        mod.callAttr("module_members_map", modules.toTypedArray())
                            .asMap()
                            .entries
                            .associate { (k, v) ->
                                k.toString() to v.asList().take(MAX_MEMBERS_PER_MODULE)
                                    .map { it.toString() }
                            }
                    }
                    members to names
                } catch (t: Throwable) {
                    emptyMap<String, List<String>>() to emptyList<String>()
                }
            }
            memberCache.clear()
            memberCache.putAll(result.first)
            importNames = result.second
            touchEditor()
        }
    }

    /**
     * 触发一次补全缓存刷新，但同一个内容签名只做一次
     * （避免每次重组都去 import 模块）。
     */
    fun ensureCompletionFor(source: String) {
        val signature = "${source.length}:${source.hashCode()}"
        if (signature == memberCacheLoadedFor) return
        memberCacheLoadedFor = signature
        refreshCompletion(source)
    }

    // ---------------------------------------------------------- 运行配置 --
    /** 读取某个文件的运行配置（argv / cwd / env / stdin 预设）。 */
    fun runOptionsFor(file: File): RunOptions = runConfigStore.load(file.absolutePath)

    fun saveRunOptions(options: RunOptions) {
        runConfigStore.save(options)
        postNotice("运行配置已保存")
    }

    suspend fun loadRunOptionsPreview(options: RunOptions): String = withContext(Dispatchers.IO) {
        buildString {
            append("文件   : ${options.filePath}\n")
            append("argv   : ${options.argvAsList()}\n")
            append("工作目录: ${options.cwd.ifBlank { "（脚本所在目录）" }}\n")
            append("环境变量: ${options.envAsMap()}\n")
            append("stdin  : ${options.stdinPreset.length} 字符")
        }
    }

    // -------------------------------------------------------------- 运行 --
    var isRunning by mutableStateOf(false)
        private set

    fun runActiveScript() {
        val tab = editor.active ?: run {
            postNotice("没有打开的标签页")
            return
        }
        val file = tab.file ?: run {
            postNotice("请先「另存为」给未命名文件指定路径")
            return
        }
        if (services.pythonRunner.running || isRunning) {
            postNotice("已有脚本在运行，请先中断")
            return
        }
        applyFormatIfNeeded(tab)
        tab.save()
        refreshCompletion(tab.content.toString())
        touchEditor()
        startRun(file)
    }

    fun runFile(file: File) {
        if (services.pythonRunner.running || isRunning) {
            postNotice("已有脚本在运行，请先中断")
            return
        }
        startRun(file)
    }

    private fun startRun(file: File) {
        isRunning = true
        requestNavigate(ROUTE_CONSOLE)
        val options = runConfigStore.load(file.absolutePath)
        val config = options.toRunConfig().copy(
            cwd = options.cwd.trim().ifBlank { file.parentFile?.absolutePath }
        )
        services.pythonRunner.run(config) {
            isRunning = false
        }
    }

    fun interrupt() {
        services.pythonRunner.interrupt()
        isRunning = false
    }

    // ------------------------------------------------------------- 控制台 --
    private val lineList = mutableStateListOf<ConsoleLine>()
    val lines: List<ConsoleLine> get() = lineList

    /** 当前尚未以换行结束的尾巴（例如 input() 的提示串）。 */
    var pendingLine by mutableStateOf("")
        private set

    private var pendingIsError = false
    private val consoleLock = Any()

    fun clearConsole() {
        synchronized(consoleLock) {
            lineList.clear()
            pendingLine = ""
            pendingIsError = false
        }
    }

    fun consoleText(): String {
        val sb = StringBuilder()
        synchronized(consoleLock) {
            lineList.forEach { sb.append(it.text).append('\n') }
            sb.append(pendingLine)
        }
        return sb.toString()
    }

    /** 把控制台输出保存成文本文件；返回落盘路径。 */
    fun saveConsoleToFile(): File? {
        val text = consoleText()
        if (text.isBlank()) {
            postNotice("控制台还没有内容")
            return null
        }
        val dir = currentDir ?: currentProject?.dir ?: services.appContext.filesDir
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val target = File(dir, "console_$stamp.txt")
        return try {
            target.writeText(text)
            postNotice("输出已保存到 ${target.name}")
            touchEditor()
            target
        } catch (t: Throwable) {
            postNotice("保存失败：${t.message}")
            null
        }
    }

    /** 把用户在控制台输入的一行交给被阻塞的 `input()`。 */
    fun submitInput(text: String) {
        appendOutput(false, "$text\n")
        HostBridge.submitStdin(text)
    }

    private fun appendOutput(isErr: Boolean, chunk: String) {
        if (chunk.isEmpty()) return
        synchronized(consoleLock) {
            var rest = chunk
            while (rest.isNotEmpty()) {
                if (pendingIsError != isErr && pendingLine.isNotEmpty()) {
                    lineList.add(ConsoleLine(pendingLine, pendingIsError))
                    pendingLine = ""
                }
                pendingIsError = isErr
                val nl = rest.indexOf('\n')
                if (nl < 0) {
                    pendingLine += rest
                    rest = ""
                } else {
                    pendingLine += rest.substring(0, nl)
                    lineList.add(ConsoleLine(pendingLine, pendingIsError))
                    pendingLine = ""
                    rest = rest.substring(nl + 1)
                }
            }
            while (lineList.size > MAX_CONSOLE_LINES) lineList.removeAt(0)
        }
    }

    // -------------------------------------------------------------- 项目 --
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set

    var currentProject by mutableStateOf<Project?>(null)
        private set

    /** 文件浏览器当前所在目录。 */
    var currentDir by mutableStateOf<File?>(null)
        private set

    /** 文件列表用的版本号，任何目录内改动都会自增以触发刷新。 */
    var filesRevision by mutableStateOf(0)
        private set

    fun refreshProjects() {
        projects = services.projectManager.listProjects()
        val last = services.projectManager.lastOpenedProject
        if (currentProject == null) {
            currentProject = projects.firstOrNull { it.name == last }
                ?: projects.firstOrNull()
                ?: services.projectManager.createProject("默认项目").also {
                    projects = services.projectManager.listProjects()
                }
        }
        currentDir = currentProject?.dir
    }

    fun selectProject(project: Project) {
        currentProject = project
        currentDir = project.dir
        services.projectManager.lastOpenedProject = project.name
    }

    fun createProject(name: String) {
        val p = services.projectManager.createProject(name)
        projects = services.projectManager.listProjects()
        selectProject(p)
        postNotice("已创建项目 ${p.name}")
    }

    fun deleteProject(project: Project) {
        services.projectManager.deleteProject(project)
        projects = services.projectManager.listProjects()
        if (currentProject?.name == project.name) {
            currentProject = projects.firstOrNull()
            currentDir = currentProject?.dir
        }
    }

    fun enterDir(dir: File) {
        currentDir = dir
    }

    fun goUp() {
        val dir = currentDir ?: return
        val root = currentProject?.dir ?: return
        if (dir.absolutePath != root.absolutePath) {
            currentDir = dir.parentFile ?: root
        }
    }

    fun newFile(name: String, template: String = "") {
        val dir = currentDir ?: return
        viewModelScope.launch {
            val f = services.projectManager.createFile(dir, name, template)
            postNotice("已新建 ${f.name}")
            touchEditor()
        }
    }

    fun newDir(name: String) {
        val dir = currentDir ?: return
        services.projectManager.createDir(dir, name)
        refreshDir()
    }

    fun renameFile(file: File, newName: String) {
        services.projectManager.rename(file, newName)
        refreshDir()
    }

    fun deleteFiles(files: List<File>) {
        files.forEach { services.projectManager.delete(it) }
        services.recentFiles.list()
            .filter { path -> files.any { path.startsWith(it.absolutePath) } }
            .forEach { services.recentFiles.remove(it) }
        refreshRecent()
        refreshDir()
    }

    fun moveFiles(files: List<File>, destDir: File) {
        files.forEach { services.projectManager.move(it, destDir) }
        refreshDir()
    }

    fun copyFiles(files: List<File>, destDir: File) {
        files.forEach { services.projectManager.copy(it, destDir) }
        refreshDir()
    }

    fun refreshDir() {
        filesRevision++
        touchEditor()
    }

    fun importViaSaf(uri: Uri) {
        val dir = currentDir ?: return
        viewModelScope.launch {
            val f = services.projectManager.importFromSaf(uri, dir)
            postNotice(f?.let { "已导入 ${it.name}" } ?: "导入失败")
            touchEditor()
        }
    }

    fun importFromStream(name: String, stream: InputStream) {
        val dir = currentDir ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val target = File(dir, name)
                stream.use { input -> target.outputStream().use { input.copyTo(it) } }
                withContext(Dispatchers.Main) {
                    postNotice("已导入 ${target.name}")
                    touchEditor()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { postNotice("导入失败：${t.message}") }
            }
        }
    }

    // ---------------------------------------------------------- 最近文件 --
    var recent by mutableStateOf<List<String>>(emptyList())
        private set

    fun refreshRecent() {
        recent = services.recentFiles.list()
    }

    fun clearRecent() {
        services.recentFiles.clear()
        refreshRecent()
        postNotice("已清空最近打开列表")
    }

    fun openRecent(path: String) {
        openFile(File(path))
    }

    // ------------------------------------------------------------ 调试器 --
    /** 当前正在调试的文件是否命中该断点。 */
    fun breakpointsFor(file: File): Set<Int> =
        services.debugger.breakpoints.value
            .filter { it.first == file.absolutePath }
            .map { it.second }
            .toSet()

    /** 供编辑器 gutter 使用：0-based 行号集合。 */
    fun breakpointLinesFor(file: File?): Set<Int> =
        file?.let { breakpointsFor(it).map { line -> line - 1 }.toSet() } ?: emptySet()

    fun toggleBreakpoint(file: File, line: Int) {
        services.debugger.toggleBreakpoint(file.absolutePath, line)
    }

    fun startDebug() {
        val file = editor.active?.file ?: run {
            postNotice("请先保存文件再调试")
            return
        }
        editor.active?.save()
        touchEditor()
        services.debugger.start(file.absolutePath, emptyList(), file.parentFile?.absolutePath)
        requestNavigate(ROUTE_DEBUGGER)
    }

    // -------------------------------------------------------------- 图表 --
    val charts: List<Chart> get() = EventBus.chartList.value

    fun saveChart(chart: Chart, dest: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                File(chart.path).copyTo(dest, overwrite = true)
                true
            } catch (t: Throwable) {
                false
            }
            withContext(Dispatchers.Main) {
                postNotice(if (ok) "已保存到 ${dest.absolutePath}" else "保存失败")
            }
        }
    }

    // ------------------------------------------------------------ 教程区 --
    /** 从 APK 中读取教程 / 片段的原始文本。 */
    suspend fun readResource(pkg: String, name: String): String = withContext(Dispatchers.IO) {
        try {
            Python.getInstance().getModule("pdl_resources")
                .callAttr("read_text", pkg, name).toString()
        } catch (t: Throwable) {
            ""
        }
    }

    suspend fun listResources(pkg: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Python.getInstance().getModule("pdl_resources")
                .callAttr("list_files", pkg).asList().map { it.toString() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** 把教程里的示例代码放进当前项目并打开。 */
    fun materializeExample(title: String, code: String, runAfter: Boolean = false) {
        val dir = currentDir ?: return
        viewModelScope.launch {
            val safe = title.replace(Regex("[^A-Za-z0-9_\\u4e00-\\u9fa5\\-]"), "_")
            var target = File(dir, "$safe.py")
            var n = 1
            while (target.exists()) {
                target = File(dir, "${safe}_${n++}.py")
            }
            services.projectManager.createFile(dir, target.name, code)
            postNotice("示例已写入 ${target.name}")
            openFile(target)
            if (runAfter) runFile(target)
        }
    }

    // -------------------------------------------------------------- 设置 --
    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch { services.settings.setThemeMode(mode) }
    }

    fun updateMirror(id: String) {
        viewModelScope.launch {
            services.settings.setPipMirror(id)
            services.packageManager.mirror =
                PipMirror.values().firstOrNull { it.id == id } ?: PipMirror.PYPI
        }
    }

    fun updateEditorSettings(e: EditorSettings) {
        viewModelScope.launch { services.settings.setEditor(e) }
    }

    fun updateSymbolBar(symbols: List<String>) {
        viewModelScope.launch { services.settings.setSymbolBar(symbols) }
    }

    fun updateShortcuts(map: Map<String, String>) {
        viewModelScope.launch { services.settings.setShortcuts(map) }
    }

    suspend fun exportSettings(): String = withContext(Dispatchers.IO) {
        services.settings.exportJson()
    }

    suspend fun importSettings(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            services.settings.importJson(json)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 备份项目为 zip；返回产物路径（供分享）。 */
    fun backupProject(project: Project, destZip: File, onReady: (File) -> Unit = {}) {
        viewModelScope.launch {
            try {
                services.projectManager.exportProjectZip(project, destZip)
                postNotice("已备份到 ${destZip.absolutePath}")
                onReady(destZip)
            } catch (t: Throwable) {
                postNotice("备份失败：${t.message}")
            }
        }
    }

    // ---------------------------------------------------------------- 初始化 --
    init {
        refreshProjects()
        refreshRecent()
        viewModelScope.launch {
            services.settings.settings.collect { s ->
                settings = s
                services.packageManager.mirror =
                    PipMirror.values().firstOrNull { it.id == s.pipMirror } ?: PipMirror.PYPI
            }
        }
        viewModelScope.launch { EventBus.stdout.collect { appendOutput(false, it) } }
        viewModelScope.launch { EventBus.stderr.collect { appendOutput(true, it) } }
    }

    companion object {
        const val ROUTE_EDITOR = "editor"
        const val ROUTE_CONSOLE = "console"
        const val ROUTE_TERMINAL = "terminal"
        const val ROUTE_FILES = "files"
        const val ROUTE_PACKAGES = "packages"
        const val ROUTE_CHARTS = "charts"
        const val ROUTE_DEBUGGER = "debugger"
        const val ROUTE_REPL = "repl"
        const val ROUTE_LEARN = "learn"
        const val ROUTE_SETTINGS = "settings"

        /** 编辑器一次性命令字面量。 */
        const val CMD_FIND = "find"
        const val CMD_REPLACE = "replace"
        const val CMD_FORMAT = "format"
        const val CMD_UNDO = "undo"
        const val CMD_REDO = "redo"

        private const val MAX_CONSOLE_LINES = 4000
        private const val MAX_COMPLETION_MODULES = 40
        private const val MAX_MEMBERS_PER_MODULE = 800
    }
}
