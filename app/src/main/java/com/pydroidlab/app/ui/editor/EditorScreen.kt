package com.pydroidlab.app.ui.editor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.editor.PythonLanguage.EditorDiagnostics
import com.pydroidlab.app.ui.settings.ThemeMode
import java.io.File
import kotlin.math.abs

/**
 * ① 编辑器界面
 *
 * 多标签页（点击切换 / 拖动关闭）+ 符号工具栏 + 运行按钮。
 * 底层用 [CodeEditorView]（sora-editor 封装）；标签状态保存在
 * [AppViewModel.editor]，因此横竖屏旋转不会丢内容，切标签也会恢复光标。
 */
@Composable
fun EditorScreen(vm: AppViewModel, embedded: Boolean = false) {
    // Phase 1：embedded = true 时由「项目工作区」承载 —— 自有工具栏换成一排
    // 编辑器强绑定操作（另存为/查找/格式化），运行/保存/调试移到工作区 pill
    // 工具栏；符号栏前部追加撤销 / 重做 / TAB。默认 false 保持旧行为。
    // 读取 revision 让所有基于 EditorState 的变更都能触发重组
    vm.editorRevision

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val editorState = vm.editor
    val active = editorState.active
    val editorSettings = vm.settings.editor
    // 收集断点，断点变化时 gutter 才会重画
    val breakpoints by vm.services.debugger.breakpoints.collectAsState()

    val dark = when (vm.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val soraScheme: EditorColorScheme = remember(editorSettings.colorScheme, dark) {
        EditorThemes.resolve(editorSettings.colorScheme, dark)
    }

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var cursorLine by remember { mutableStateOf(1) }
    var cursorCol by remember { mutableStateOf(0) }
    var showSymbolBar by remember { mutableStateOf(true) }
    var showFind by remember { mutableStateOf(false) }
    var showSaveAs by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    var showRunConfig by remember { mutableStateOf(false) }
    var wordHelp by remember { mutableStateOf<String?>(null) }
    var wordText by remember { mutableStateOf("") }

    val activePath = active?.file?.absolutePath

    LaunchedEffect(active?.id) {
        active?.let {
            cursorLine = it.cursorLine + 1
            cursorCol = it.cursorColumn
            // 打开/切换标签时刷新一次 import 补全缓存（不在每次按键时做）
            vm.ensureCompletionFor(it.content.toString())
        }
    }

    // 外接键盘快捷键下发的编辑器命令（Ctrl+F 查找 / Ctrl+H 替换 / Ctrl+Alt+L 格式化）
    val pendingCmd = vm.pendingEditorCommand
    LaunchedEffect(pendingCmd) {
        if (pendingCmd != null) {
            when (pendingCmd) {
                AppViewModel.CMD_FIND, AppViewModel.CMD_REPLACE -> showFind = true
                AppViewModel.CMD_FORMAT -> vm.formatActiveTab()
                else -> Unit
            }
            vm.consumeEditorCommand()
        }
    }

    // 工作区 pill 工具栏的「编辑运行配置…」（Phase 1）
    LaunchedEffect(vm.pendingRunConfigEdit) {
        if (vm.pendingRunConfigEdit) {
            showRunConfig = true
            vm.consumeRunConfigEdit()
        }
    }

    // 分析自愈循环：sora 的分析线程遇异常会静默死亡且无法复活，唯一可靠的
    // 办法是换一个全新的语言实例（新分析器 + 新分析线程）。每 2.5 秒体检
    // 一次：失败 → 自动重建（上限 3 次）；重建后恢复 → Snackbar 报喜；
    // 语言一直没挂上 → 提示去诊断面板。
    LaunchedEffect(active?.id) {
        var pendingStartupHint = true
        // delay 是可取消挂起点：LaunchedEffect 离开组合时协程被取消，循环随之结束
        while (true) {
            kotlinx.coroutines.delay(2500)
            val d = EditorDiagnostics
            if (!d.languageSeen) {
                if (pendingStartupHint) {
                    pendingStartupHint = false
                    vm.postNotice("编辑器分析未启动：请到 设置 → 诊断 → 编辑器诊断 查看详情")
                    try {
                        editorRef?.editorLanguage?.analyzeManager?.rerun()
                    } catch (_: Throwable) {
                    }
                }
                continue
            }
            if (!d.lastAnalyzeOk && d.rebuildCount < 3) {
                d.rebuildCount++
                editorRef?.let { e ->
                    try {
                        e.setEditorLanguage(
                            PythonLanguage(
                                identifierProvider = { vm.completionNames() },
                                memberProvider = { module -> vm.moduleMembers(module) },
                                breakpointProvider = {
                                    val path = active?.file?.absolutePath
                                    if (path == null) emptySet()
                                    else breakpoints.filter { it.first == path }.map { it.second - 1 }.toSet()
                                }
                            )
                        )
                        vm.postNotice("编辑器分析线程异常，已自动重建（第 ${d.rebuildCount} 次）")
                    } catch (_: Throwable) {
                    }
                }
            } else if (d.lastAnalyzeOk && d.rebuildCount > 0) {
                vm.postNotice("编辑器分析已恢复正常")
                // 只报一次喜：把计数清零避免重复提示
                d.rebuildCount = 0
                d.markRecovered()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!embedded) {
        EditorActionBar(
            running = vm.isRunning,
            hasTab = active != null,
            showSymbolBar = showSymbolBar,
            onToggleSymbolBar = { showSymbolBar = !showSymbolBar },
            onNew = { showTemplates = true },
            onRun = { vm.runActiveScript() },
            onSave = { vm.saveActive() },
            onSaveAs = { showSaveAs = true },
            onFind = { showFind = true },
            onFormat = { vm.formatActiveTab() },
            onDebug = { vm.startDebug() },
            onRunConfig = { showRunConfig = true },
            onRecent = { vm.refreshRecent(); showRecent = true },
            onUndo = { editorRef?.undo() },
            onRedo = { editorRef?.redo() },
            onShare = {
                val text = active?.content?.toString().orEmpty()
                if (text.isBlank()) {
                    vm.postNotice("没有可分享的内容")
                } else {
                    val name = active?.title ?: "untitled.py"
                    shareText(context, name, text)
                }
            },
            onCopyAll = {
                val text = active?.content?.toString().orEmpty()
                if (text.isBlank()) {
                    vm.postNotice("没有可复制的内容")
                } else {
                    clipboard.setText(AnnotatedString(text))
                    vm.postNotice("整篇代码已复制")
                }
            },
            onToggleBreakpoint = {
                val tab = active
                val file = tab?.file
                if (tab == null || file == null) {
                    vm.postNotice("请先保存文件再打断点")
                } else {
                    if (cursorLine in vm.breakpointsFor(file)) {
                        tab.breakpoints.remove(cursorLine)
                    } else {
                        tab.breakpoints.add(cursorLine)
                    }
                    vm.toggleBreakpoint(file, cursorLine)
                    vm.touchEditor()
                }
            }
        )
        } else {
            EmbeddedActionBar(
                onSaveAs = { showSaveAs = true },
                onFind = { showFind = true },
                onFormat = { vm.formatActiveTab() }
            )
        }

        if (editorState.tabs.isEmpty()) {
            // 空状态整块居中。
            // 这里必须同时做两件事，少一件排版就会歪：
            //   1) 给 Column 一个确定的宽度（fillMaxWidth + 水平内边距）——
            //      否则它是 wrap-content，长文案的固有宽度一旦超过屏宽，
            //      父容器的 Center 对齐就失效，文字会**溢出到左侧**贴边；
            //   2) 文本自身 textAlign = Center —— 换行后每一行才会居中。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "还没有打开的文件",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "从「文件」页打开项目里的脚本，或直接新建一个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { showTemplates = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建脚本")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.refreshRecent(); showRecent = true }) {
                        Text("打开最近文件（${vm.recent.size}）")
                    }
                }
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = editorState.activeIndex.coerceAtLeast(0),
                edgePadding = 4.dp
            ) {
                editorState.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == editorState.activeIndex,
                        onClick = {
                            editorState.select(index)
                            vm.touchEditor()
                        },
                        // 标签左右拖动即可关闭（仅多于一个标签时启用）
                        modifier = Modifier.swipeToClose(
                            enabled = editorState.tabs.size > 1,
                            onClose = { vm.closeTab(index) }
                        ),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tab.displayTitle,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(onClick = { vm.closeTab(index) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "关闭标签",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (active != null) {
                    key(active.id) {
                        CodeEditorView(
                            tab = active,
                            settings = editorSettings,
                            colorScheme = soraScheme,
                            identifierProvider = { vm.completionNames() },
                            onDirtyChanged = { vm.touchEditor() },
                            onCursorChanged = { line, col ->
                                cursorLine = line + 1
                                cursorCol = col
                            },
                            onBreakpointToggle = { line ->
                                // 行号栏点击：line 是 0-based，调试器用 1-based
                                active.file?.let { f ->
                                    vm.toggleBreakpoint(f, line + 1)
                                    vm.touchEditor()
                                }
                            },
                            onLongPressWord = { word ->
                                wordText = word
                                wordHelp = "查询中…"
                            },
                            memberProvider = { module -> vm.moduleMembers(module) },
                            breakpointProvider = {
                                val path = active.file?.absolutePath
                                if (path == null) {
                                    emptySet()
                                } else {
                                    breakpoints.filter { it.first == path }
                                        .map { it.second - 1 }
                                        .toSet()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            editorRef = { editorRef = it }
                        )
                    }
                }
            }

            if (showSymbolBar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (embedded) {
                        // 工作区精简符号栏前段：撤销 / 重做 / TAB（TinaIDE 风格）
                        IconButton(onClick = { editorRef?.undo() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Undo, contentDescription = "撤销", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { editorRef?.redo() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Redo, contentDescription = "重做", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { editorRef?.commitText("\t") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Text("TAB", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    SymbolBar(
                        symbols = vm.settings.symbolBar,
                        modifier = Modifier.weight(1f),
                        onInsert = { sym ->
                            // Bug 7.4：走 commitText 而不是 insertText ——
                            // commitText 才经过 sora 的符号配对管道（配对策略 +
                            // skip-over 拦截器），让符号栏与键盘输入行为完全一致，
                            // 也不需要在符号栏里再写一份插入逻辑。
                            editorRef?.commitText(sym)
                        }
                    )
                }
            }
        }

        HorizontalDivider()
        StatusBar(
            path = active?.file?.absolutePath ?: "未保存的新标签页",
            line = cursorLine,
            column = cursorCol,
            dirty = active?.isDirty == true,
            breakpoints = activePath?.let { path -> breakpoints.count { it.first == path } } ?: 0,
            // Phase 3 接 Jedi 后由 VM 提供；Phase 1 恒为灰点占位
            smartReady = false
        )
    }

    if (showFind) {
        FindReplaceDialog(
            onDismiss = { showFind = false },
            onFind = { q, regex, caseSensitive, wholeWord ->
                val s = editorRef?.searcher
                if (s != null) {
                    s.search(q, searchOptions(regex, caseSensitive, wholeWord))
                    s.gotoNext()
                }
                s != null
            },
            onReplace = { q, repl, regex, caseSensitive, wholeWord, all ->
                val s = editorRef?.searcher
                if (s != null) {
                    if (all) {
                        s.search(q, searchOptions(regex, caseSensitive, wholeWord))
                        s.replaceAll(repl)
                    } else {
                        s.replaceCurrentMatch(repl)
                    }
                }
                s != null
            }
        )
    }

    if (showSaveAs && active != null) {
        val baseDir: File = vm.currentDir ?: vm.currentProject?.dir ?: File("/")
        SaveAsDialog(
            initial = active.title,
            hint = "保存到：${baseDir.absolutePath}",
            onDismiss = { showSaveAs = false },
            onConfirm = { name ->
                vm.saveAs(active, name, baseDir)
                showSaveAs = false
            }
        )
    }

    if (showTemplates) {
        TemplatePicker(
            onDismiss = { showTemplates = false },
            onPick = { template ->
                vm.newTab(template)
                showTemplates = false
            }
        )
    }

    if (showRunConfig) {
        val file = active?.file
        if (file == null) {
            LaunchedEffect(Unit) {
                vm.postNotice("请先保存文件再配置运行参数")
                showRunConfig = false
            }
        } else {
            RunConfigDialog(
                initial = vm.runOptionsFor(file),
                onDismiss = { showRunConfig = false },
                onSave = { opts ->
                    vm.saveRunOptions(opts)
                    showRunConfig = false
                },
                onSaveAndRun = { opts ->
                    vm.saveRunOptions(opts)
                    showRunConfig = false
                    vm.runActiveScript()
                }
            )
        }
    }

    if (showRecent) {
        RecentFilesDialog(
            paths = vm.recent,
            onDismiss = { showRecent = false },
            onOpen = { path ->
                showRecent = false
                vm.openRecent(path)
            },
            onClear = { vm.clearRecent() }
        )
    }

    val help = wordHelp
    if (help != null) {
        LaunchedEffect(wordText) {
            val doc = vm.services.repl.docstring(wordText)
            wordHelp = "$wordText\n\n$doc"
        }
        AlertDialog(
            onDismissRequest = { wordHelp = null },
            title = { Text("帮助：$wordText") },
            text = {
                Text(
                    text = help,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = { TextButton(onClick = { wordHelp = null }) { Text("关闭") } }
        )
    }
}

/** 把 UI 上的三个开关翻译成 sora 的查询选项。 */
private fun searchOptions(
    useRegex: Boolean,
    caseSensitive: Boolean,
    wholeWord: Boolean
): EditorSearcher.SearchOptions {
    val type = when {
        useRegex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
        else -> EditorSearcher.SearchOptions.TYPE_NORMAL
    }
    return EditorSearcher.SearchOptions(type, !caseSensitive)
}

/** 标签左右滑动超过阈值即关闭。 */
private fun Modifier.swipeToClose(enabled: Boolean, onClose: () -> Unit): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        var total = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                total = 0f
                fired = false
            },
            onHorizontalDrag = { _, dragAmount ->
                if (fired) return@detectHorizontalDragGestures
                total += dragAmount
                if (abs(total) > SWIPE_CLOSE_THRESHOLD_PX) {
                    fired = true
                    onClose()
                }
            }
        )
    }
}

private const val SWIPE_CLOSE_THRESHOLD_PX = 140f

@Composable
private fun EditorActionBar(
    running: Boolean,
    hasTab: Boolean,
    showSymbolBar: Boolean,
    onToggleSymbolBar: () -> Unit,
    onNew: () -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onFind: () -> Unit,
    onFormat: () -> Unit,
    onDebug: () -> Unit,
    onRunConfig: () -> Unit,
    onRecent: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShare: () -> Unit,
    onCopyAll: () -> Unit,
    onToggleBreakpoint: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
            TextButton(onClick = onRun, enabled = hasTab && !running) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (running) "运行中" else "运行")
            }
            TextButton(onClick = onSave, enabled = hasTab) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
            TextButton(onClick = onSaveAs, enabled = hasTab) { Text("另存为") }
            TextButton(onClick = onRunConfig, enabled = hasTab) { Text("运行配置") }
            TextButton(onClick = onFind, enabled = hasTab) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("查找")
            }
            TextButton(onClick = onUndo, enabled = hasTab) { Text("撤销") }
            TextButton(onClick = onRedo, enabled = hasTab) { Text("重做") }
            TextButton(onClick = onFormat, enabled = hasTab) { Text("格式化") }
            TextButton(onClick = onDebug, enabled = hasTab) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("调试")
            }
            TextButton(onClick = onToggleBreakpoint, enabled = hasTab) { Text("断点") }
            TextButton(onClick = onShare, enabled = hasTab) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("分享")
            }
            TextButton(onClick = onCopyAll, enabled = hasTab) { Text("复制") }
            TextButton(onClick = onRecent) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("最近")
            }
            TextButton(onClick = onToggleSymbolBar) {
                Text(if (showSymbolBar) "隐藏符号栏" else "显示符号栏")
            }
        }
    }
}

@Composable
private fun StatusBar(
    path: String,
    line: Int,
    column: Int,
    dirty: Boolean,
    breakpoints: Int,
    smartReady: Boolean = false
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ● Python 智能就绪（Phase 3 Jedi 就绪时绿点，现在灰点占位）
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (smartReady) Color(0xFF34A853) else MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (smartReady) "Python 智能就绪" else "智能未就绪",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "UTF-8",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f)
            )
            Text(
                text = buildString {
                    append("Ln $line, Col $column")
                    if (dirty) append(" · 未保存")
                    if (breakpoints > 0) append(" · 断点 $breakpoints")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 工作区（embedded）模式下的精简操作行：只保留编辑器强绑定动作。 */
@Composable
private fun EmbeddedActionBar(onSaveAs: () -> Unit, onFind: () -> Unit, onFormat: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 0.dp)
    ) {
        TextButton(onClick = onSaveAs) { Text("另存为") }
        TextButton(onClick = onFind) { Text("查找/替换") }
        TextButton(onClick = onFormat) { Text("格式化") }
    }
}

@Composable
private fun FindReplaceDialog(
    onDismiss: () -> Unit,
    onFind: (String, Boolean, Boolean, Boolean) -> Boolean,
    onReplace: (String, String, Boolean, Boolean, Boolean, Boolean) -> Boolean
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查找 / 替换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("查找") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CheckRow("区分大小写", caseSensitive) { caseSensitive = it }
                CheckRow("全词匹配", wholeWord) {
                    wholeWord = it
                    if (it) useRegex = false
                }
                CheckRow("正则表达式", useRegex) {
                    useRegex = it
                    if (it) wholeWord = false
                }
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (query.isEmpty()) {
                    message = "请输入查找内容"
                } else {
                    onFind(query, useRegex, caseSensitive, wholeWord)
                    message = "已定位匹配项"
                }
            }) { Text("查找下一个") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (query.isNotEmpty()) {
                        onReplace(query, replacement, useRegex, caseSensitive, wholeWord, false)
                    }
                }) { Text("替换") }
                TextButton(onClick = {
                    if (query.isNotEmpty()) {
                        onReplace(query, replacement, useRegex, caseSensitive, wholeWord, true)
                    }
                }) { Text("全部替换") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SaveAsDialog(
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("另存为") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(hint, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TemplatePicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建脚本") },
        text = {
            Column {
                FileTemplates.ALL.forEach { (name, template) ->
                    Text(
                        text = name,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(template) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RecentFilesDialog(
    paths: List<String>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最近打开（${paths.size}）") },
        text = {
            if (paths.isEmpty()) {
                Text("还没有打开过文件", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(paths) { path ->
                        val exists = File(path).exists()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = exists) { onOpen(path) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = File(path).name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!exists) {
                                Text(
                                    text = "已删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (paths.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("清空")
                    }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/** 通过系统分享面板把一段文本发出去。 */
private fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}
