package com.pydroidlab.app.ui.screens

import android.content.Intent
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pydroidlab.app.data.AppSettings
import com.pydroidlab.app.editor.EditorThemeManager
import com.pydroidlab.app.editor.IdeColorTheme
import com.pydroidlab.app.editor.PythonSoraLanguage
import com.pydroidlab.app.runtime.AstLinterWarning
import com.pydroidlab.app.runtime.AstOutlineSymbol
import com.pydroidlab.app.runtime.AstSyntaxDiagnostic
import com.pydroidlab.app.runtime.CodeMetrics
import com.pydroidlab.app.runtime.RuntimeContextInfo
import com.pydroidlab.app.ui.EditorTabItem
import com.pydroidlab.app.ui.EditorUiState
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

data class CommandPaletteAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val onExecute: () -> Unit
)

data class PythonSnippetTemplate(
    val title: String,
    val category: String,
    val code: String
)

private val BUILTIN_SNIPPET_TEMPLATES = listOf(
    PythonSnippetTemplate(
        title = "if __name__ == '__main__' 主入口",
        category = "基础结构",
        code = "def main():\n    print(\"Hello PyDroidLab!\")\n\n\nif __name__ == \"__main__\":\n    main()\n"
    ),
    PythonSnippetTemplate(
        title = "requests 发送 HTTP GET 请求 (带异常捕获)",
        category = "网络请求",
        code = "import requests\n\ntry:\n    resp = requests.get(\"https://httpbin.org/get\", timeout=8)\n    resp.raise_for_status()\n    print(resp.json())\nexcept requests.RequestException as e:\n    print(f\"请求失败: {e}\")\n"
    ),
    PythonSnippetTemplate(
        title = "读写 UTF-8 文件 (with open)",
        category = "文件 I/O",
        code = "with open(\"data.txt\", \"w\", encoding=\"utf-8\") as f:\n    f.write(\"Hello Python\\n\")\n\nwith open(\"data.txt\", \"r\", encoding=\"utf-8\") as f:\n    content = f.read()\nprint(content)\n"
    ),
    PythonSnippetTemplate(
        title = "面向对象 Class 完整模板 (@property)",
        category = "面向对象",
        code = "class DataItem:\n    def __init__(self, name: str, value: float = 0.0):\n        self.name = name\n        self._value = value\n\n    @property\n    def value(self) -> float:\n        return self._value\n\n    def __repr__(self) -> str:\n        return f\"DataItem(name={self.name!r}, value={self._value})\"\n"
    ),
    PythonSnippetTemplate(
        title = "函数耗时计时装饰器 (@timer)",
        category = "高阶技巧",
        code = "import time\nfrom functools import wraps\n\ndef timer(func):\n    @wraps(func)\n    def wrapper(*args, **kwargs):\n        t0 = time.perf_counter()\n        res = func(*args, **kwargs)\n        print(f\"[{func.__name__}] 耗时: {(time.perf_counter() - t0)*1000:.2f} ms\")\n        return res\n    return wrapper\n"
    ),
    PythonSnippetTemplate(
        title = "调用手机原生震动、通知与剪贴板 (android_app)",
        category = "手机硬件",
        code = "import android_app\n\nandroid_app.toast(\"任务开始执行\")\nandroid_app.vibrate(120)\ninfo = android_app.device_info()\nprint(\"设备信息:\", info)\nandroid_app.notify(\"PyDroidLab\", \"脚本运行完毕\")\n"
    ),
    PythonSnippetTemplate(
        title = "生成 PNG 图像并内嵌预览 (show_image)",
        category = "可视化",
        code = "import android_app\n# 若已生成图片文件，直接调用内置缩放预览器：\nandroid_app.show_image(\"pydroidlab_chart.png\")\n"
    ),
    PythonSnippetTemplate(
        title = "多线程并发执行任务 (ThreadPoolExecutor)",
        category = "并发编程",
        code = "from concurrent.futures import ThreadPoolExecutor\n\ndef worker(n: int) -> int:\n    return n * n\n\nwith ThreadPoolExecutor(max_workers=4) as pool:\n    results = list(pool.map(worker, range(10)))\nprint(\"并发计算结果:\", results)\n"
    ),
    PythonSnippetTemplate(
        title = "SQLite 内存/本地数据库快速操作",
        category = "数据库",
        code = "import sqlite3\n\nwith sqlite3.connect(\"app.db\") as conn:\n    cur = conn.cursor()\n    cur.execute(\"CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, text TEXT)\")\n    cur.execute(\"INSERT INTO notes (text) VALUES (?)\", (\"来自 PyDroidLab 的记录\",))\n    conn.commit()\n    print(cur.execute(\"SELECT * FROM notes\").fetchall())\n"
    ),
    PythonSnippetTemplate(
        title = "命令行参数解析 (argparse / sys.argv)",
        category = "系统工具",
        code = "import sys\n\nprint(\"脚本路径:\", sys.argv[0])\nprint(\"传入参数列表:\", sys.argv[1:])\n"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    editorState: EditorUiState,
    settings: AppSettings,
    isRunning: Boolean,
    onOpenDrawer: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onTextChanged: (String, Int, Int, Boolean, Boolean) -> Unit,
    onCursorChanged: (Int, Int, Boolean, Boolean) -> Unit,
    onFormatCode: ((String) -> Unit) -> Unit,
    onRunClicked: () -> Unit,
    onRunWithArgsClicked: (String) -> Unit,
    onStopClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onSaveAsClicked: (String) -> Unit,
    onNewFileClicked: (String) -> Unit,
    onToggleFindReplace: () -> Unit,
    onUpdateFindReplace: (String, String, Boolean, Boolean) -> Unit,
    onToggleTerminal: () -> Unit,
    onSelectIdeTheme: (IdeColorTheme) -> Unit,
    onRefreshRuntimeDiagnostics: (Boolean) -> Unit,
    onConsumeJumpToLine: () -> Unit
) {
    val context = LocalContext.current
    val ideTheme = remember(settings.ideColorThemeId) {
        IdeColorTheme.fromId(settings.ideColorThemeId)
    }

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var lastLoadedVersion by remember { mutableLongStateOf(-1L) }
    var lastAppliedThemeId by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showOutlineAndContextSheet by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showSnippetsDialog by remember { mutableStateOf(false) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showRunWithArgsDialog by remember { mutableStateOf(false) }

    // Helper to replace entire buffer undoably in Sora Editor
    fun replaceFullBufferUndoable(newFullText: String, targetLine: Int, targetCol: Int) {
        val editor = editorRef ?: return
        runCatching {
            val lastLine = (editor.text.lineCount - 1).coerceAtLeast(0)
            val lastCol = editor.text.getColumnCount(lastLine)
            editor.text.replace(0, 0, lastLine, lastCol, newFullText)
            val safeL = targetLine.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
            val safeC = targetCol.coerceIn(0, editor.text.getColumnCount(safeL))
            editor.setSelection(safeL, safeC)
            editor.ensureSelectionVisible()
        }
    }

    // Line-level editing operations (Acode / Pydroid 3 pro editing toolkit)
    val toggleCurrentLineComment = {
        val editor = editorRef
        if (editor != null) {
            runCatching {
                val ln = editor.cursor.leftLine
                val col = editor.cursor.leftColumn
                val lineStr = editor.text.getLineString(ln)
                val trimmed = lineStr.trimStart()
                val indentLen = lineStr.length - trimmed.length
                val indent = lineStr.substring(0, indentLen)
                val newLineStr = if (trimmed.startsWith("# ")) {
                    indent + trimmed.removePrefix("# ")
                } else if (trimmed.startsWith("#")) {
                    indent + trimmed.removePrefix("#")
                } else {
                    "$indent# $trimmed"
                }
                editor.text.replace(ln, 0, ln, lineStr.length, newLineStr)
                editor.setSelection(ln, (col + (newLineStr.length - lineStr.length)).coerceIn(0, newLineStr.length))
            }
        }
    }

    val duplicateCurrentLineDown = {
        val editor = editorRef
        if (editor != null) {
            runCatching {
                val ln = editor.cursor.leftLine
                val col = editor.cursor.leftColumn
                val lineStr = editor.text.getLineString(ln)
                editor.text.insert(ln, lineStr.length, "\n$lineStr")
                editor.setSelection(ln + 1, col.coerceAtMost(lineStr.length))
            }
        }
    }

    val deleteCurrentLine = {
        val editor = editorRef
        if (editor != null) {
            runCatching {
                val ln = editor.cursor.leftLine
                val total = editor.text.lineCount
                if (total <= 1) {
                    val len = editor.text.getColumnCount(0)
                    editor.text.delete(0, 0, 0, len)
                } else if (ln < total - 1) {
                    editor.text.delete(ln, 0, ln + 1, 0)
                } else {
                    val prevLen = editor.text.getColumnCount(ln - 1)
                    val curLen = editor.text.getColumnCount(ln)
                    editor.text.delete(ln - 1, prevLen, ln, curLen)
                }
            }
        }
    }

    val moveCurrentLineVertically = { moveUp: Boolean ->
        val editor = editorRef
        if (editor != null) {
            runCatching {
                val lines = editor.text.toString().split("\n").toMutableList()
                val ln = editor.cursor.leftLine
                val col = editor.cursor.leftColumn
                val targetLn = if (moveUp) ln - 1 else ln + 1
                if (targetLn in lines.indices) {
                    val tmp = lines[ln]
                    lines[ln] = lines[targetLn]
                    lines[targetLn] = tmp
                    replaceFullBufferUndoable(lines.joinToString("\n"), targetLn, col)
                }
            }
        }
    }

    val unindentCurrentLine = {
        val editor = editorRef
        if (editor != null) {
            runCatching {
                val ln = editor.cursor.leftLine
                val col = editor.cursor.leftColumn
                val lineStr = editor.text.getLineString(ln)
                var spacesToRemove = 0
                while (spacesToRemove < settings.tabWidth &&
                    spacesToRemove < lineStr.length &&
                    lineStr[spacesToRemove] == ' '
                ) {
                    spacesToRemove++
                }
                if (spacesToRemove > 0) {
                    editor.text.delete(ln, 0, ln, spacesToRemove)
                    editor.setSelection(ln, (col - spacesToRemove).coerceAtLeast(0))
                }
            }
        }
    }

    val triggerFormat = {
        onFormatCode { formattedText ->
            val editor = editorRef ?: return@onFormatCode
            replaceFullBufferUndoable(formattedText, editor.cursor.leftLine, editor.cursor.leftColumn)
        }
    }

    val shareCurrentCode = {
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, editorState.fileName)
                putExtra(Intent.EXTRA_TEXT, editorState.codeContent)
            }
            context.startActivity(Intent.createChooser(sendIntent, "分享 ${editorState.fileName}"))
        }
    }

    // Synchronize external file loads or tab switches
    LaunchedEffect(editorState.contentVersion, editorRef) {
        val editor = editorRef ?: return@LaunchedEffect
        if (lastLoadedVersion != editorState.contentVersion) {
            lastLoadedVersion = editorState.contentVersion
            if (editor.text.toString() != editorState.codeContent) {
                editor.setText(editorState.codeContent)
            }
            val safeLine = editorState.cursorLine.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
            val safeCol = editorState.cursorColumn.coerceIn(0, editor.text.getColumnCount(safeLine))
            runCatching { editor.setSelection(safeLine, safeCol) }
        }
    }

    // Handle Jump-To-Line requests
    LaunchedEffect(editorState.jumpToLineRequest, editorRef) {
        val req = editorState.jumpToLineRequest ?: return@LaunchedEffect
        val editor = editorRef ?: return@LaunchedEffect
        val targetLine = req.first.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
        runCatching {
            editor.setSelection(targetLine, 0)
            editor.ensureSelectionVisible()
            editor.requestFocus()
        }
        onConsumeJumpToLine()
    }

    // Push real-time `ast.parse` syntax diagnostics + static linter warnings into Sora Editor's DiagnosticsContainer
    LaunchedEffect(editorState.syntaxDiagnostics, editorState.linterWarnings, editorRef) {
        val editor = editorRef ?: return@LaunchedEffect
        runCatching {
            val container = DiagnosticsContainer()
            for (diag in editorState.syntaxDiagnostics) {
                val lineIdx = (diag.line - 1).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                val lineLen = editor.text.getColumnCount(lineIdx)
                val startCol = (diag.column - 1).coerceIn(0, lineLen)
                val endCol = diag.endColumn.coerceIn((startCol + 1).coerceAtMost(lineLen), lineLen)
                val startIndex = editor.text.getCharIndex(lineIdx, startCol)
                val endIndex = editor.text.getCharIndex(lineIdx, endCol).coerceAtLeast(startIndex + 1)
                container.addDiagnostic(
                    DiagnosticRegion(startIndex, endIndex, DiagnosticRegion.SEVERITY_ERROR)
                )
            }
            for (warn in editorState.linterWarnings) {
                val lineIdx = (warn.line - 1).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                val lineLen = editor.text.getColumnCount(lineIdx)
                if (lineLen > 0) {
                    val startIndex = editor.text.getCharIndex(lineIdx, 0)
                    val endIndex = editor.text.getCharIndex(lineIdx, lineLen).coerceAtLeast(startIndex + 1)
                    container.addDiagnostic(
                        DiagnosticRegion(startIndex, endIndex, DiagnosticRegion.SEVERITY_WARNING)
                    )
                }
            }
            editor.diagnostics = container
        }
    }

    // Apply Find query changes dynamically
    LaunchedEffect(
        editorState.showFindReplaceBar,
        editorState.findQuery,
        editorState.caseSensitiveSearch,
        editorState.regexSearch,
        editorRef
    ) {
        val editor = editorRef ?: return@LaunchedEffect
        if (!editorState.showFindReplaceBar || editorState.findQuery.isEmpty()) {
            if (editor.searcher.hasQuery()) {
                editor.searcher.stopSearch()
            }
        } else {
            runCatching {
                val options = EditorSearcher.SearchOptions(
                    !editorState.caseSensitiveSearch,
                    editorState.regexSearch
                )
                editor.searcher.search(editorState.findQuery, options)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ideTheme.editorBg)
    ) {
        // 1. Flagship Styled Top App Bar (46.dp)
        Surface(
            color = ideTheme.chromeBg,
            contentColor = ideTheme.textPrimary,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactToolbarIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "打开工作区抽屉",
                    tint = ideTheme.textPrimary,
                    onClick = onOpenDrawer
                )

                Spacer(modifier = Modifier.width(4.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showCommandPalette = true }
                ) {
                    Text(
                        text = editorState.fileName + if (editorState.isModified) " ●" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ideTheme.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ideTheme.displayName.substringBefore(" ")} • 点击打开命令面板 ⚡",
                        fontSize = 9.sp,
                        color = ideTheme.accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Undo / Redo / Format (✨) / Theme (🎨) / Command Palette (⚡) / Terminal / Run / More
                CompactToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    enabled = editorState.canUndo,
                    tint = if (editorState.canUndo) ideTheme.textPrimary else ideTheme.textSecondary.copy(alpha = 0.4f),
                    onClick = { editorRef?.undo() }
                )

                CompactToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "重做",
                    enabled = editorState.canRedo,
                    tint = if (editorState.canRedo) ideTheme.textPrimary else ideTheme.textSecondary.copy(alpha = 0.4f),
                    onClick = { editorRef?.redo() }
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.AutoFixHigh,
                    contentDescription = "PEP 8 格式化代码",
                    tint = ideTheme.accentColor,
                    onClick = triggerFormat
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Palette,
                    contentDescription = "切换 IDE 配色主题",
                    tint = ideTheme.textPrimary,
                    onClick = { showThemePickerDialog = true }
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Bolt,
                    contentDescription = "Acode 命令面板",
                    tint = Color(0xFFFACC15),
                    onClick = { showCommandPalette = true }
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Terminal,
                    contentDescription = "打开终端",
                    tint = ideTheme.textPrimary,
                    onClick = onToggleTerminal
                )

                Spacer(modifier = Modifier.width(3.dp))

                if (isRunning) {
                    FilledIconButton(
                        onClick = onStopClicked,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止运行",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onRunClicked,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ideTheme.accentColor
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "运行脚本",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box {
                    CompactToolbarIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "更多功能",
                        tint = ideTheme.textPrimary,
                        onClick = { showOverflowMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("⚡ 命令面板 (Command Palette)") },
                            leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showCommandPalette = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🧩 插入代码片段模板 (Snippets)") },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showSnippetsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("✨ 格式化代码 (PEP 8 Format)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                triggerFormat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⚙️ 带命令行参数运行 (sys.argv)") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showRunWithArgsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🔍 查找与替换 (Find & Replace)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onToggleFindReplace()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📊 代码大纲 / 静态检查 / 环境诊断") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onRefreshRuntimeDiagnostics(false)
                                showOutlineAndContextSheet = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("💾 保存当前文件 (Save)") },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onSaveClicked()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📝 另存为 (Save As...)") },
                            leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showSaveAsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("➕ 新建脚本 (New .py)") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showNewFileDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📤 分享当前源码 (Share)") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                shareCurrentCode()
                            }
                        )
                    }
                }
            }
        }

        // 2. Always-Visible Multi-Tab Strip (Styled with ideTheme.tabStripBg)
        MultiTabStrip(
            tabs = editorState.openTabs,
            activeTabId = editorState.activeTabId,
            ideTheme = ideTheme,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onNewTab = { showNewFileDialog = true }
        )

        // 3. Collapsible Find & Replace Toolbar
        AnimatedVisibility(visible = editorState.showFindReplaceBar) {
            FindReplaceToolbar(
                findQuery = editorState.findQuery,
                replaceText = editorState.replaceText,
                caseSensitive = editorState.caseSensitiveSearch,
                useRegex = editorState.regexSearch,
                onQueryChange = { q ->
                    onUpdateFindReplace(q, editorState.replaceText, editorState.caseSensitiveSearch, editorState.regexSearch)
                },
                onReplaceChange = { r ->
                    onUpdateFindReplace(editorState.findQuery, r, editorState.caseSensitiveSearch, editorState.regexSearch)
                },
                onToggleCase = {
                    onUpdateFindReplace(editorState.findQuery, editorState.replaceText, !editorState.caseSensitiveSearch, editorState.regexSearch)
                },
                onToggleRegex = {
                    onUpdateFindReplace(editorState.findQuery, editorState.replaceText, editorState.caseSensitiveSearch, !editorState.regexSearch)
                },
                onFindPrev = { runCatching { editorRef?.searcher?.gotoPrevious() } },
                onFindNext = { runCatching { editorRef?.searcher?.gotoNext() } },
                onReplaceOne = {
                    runCatching {
                        if (editorRef?.searcher?.hasQuery() == true) {
                            editorRef?.searcher?.replaceThis(editorState.replaceText)
                        }
                    }
                },
                onReplaceAll = {
                    runCatching {
                        if (editorRef?.searcher?.hasQuery() == true) {
                            editorRef?.searcher?.replaceAll(editorState.replaceText)
                        }
                    }
                },
                onClose = onToggleFindReplace
            )
        }

        // 4. Rosemoe Sora CodeEditor View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CodeEditor(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        typefaceText = Typeface.MONOSPACE
                        typefaceLineNumber = Typeface.MONOSPACE
                        setPinLineNumber(true)
                        isHighlightCurrentBlock = true
                        isHighlightCurrentLine = true
                        isBlockLineEnabled = true

                        val pyLang = PythonSoraLanguage(tabWidthProvider = { settings.tabWidth })
                        setEditorLanguage(pyLang)
                        colorScheme = EditorThemeManager.createSoraScheme(ideTheme)
                        lastAppliedThemeId = ideTheme.id

                        setTextSize(settings.editorFontSizeSp)
                        tabWidth = settings.tabWidth
                        isWordwrap = settings.wordWrap
                        isLineNumberEnabled = settings.showLineNumbers

                        setText(editorState.codeContent)
                        lastLoadedVersion = editorState.contentVersion

                        subscribeAlways(ContentChangeEvent::class.java) {
                            val currentText = text.toString()
                            onTextChanged(
                                currentText,
                                cursor.leftLine,
                                cursor.leftColumn,
                                canUndo(),
                                canRedo()
                            )
                        }

                        subscribeAlways(SelectionChangeEvent::class.java) {
                            onCursorChanged(
                                cursor.leftLine,
                                cursor.leftColumn,
                                canUndo(),
                                canRedo()
                            )
                        }

                        editorRef = this
                    }
                },
                update = { editor ->
                    editorRef = editor
                    editor.setTextSize(settings.editorFontSizeSp)
                    editor.tabWidth = settings.tabWidth
                    editor.isWordwrap = settings.wordWrap
                    editor.isLineNumberEnabled = settings.showLineNumbers

                    if (lastAppliedThemeId != ideTheme.id) {
                        editor.colorScheme = EditorThemeManager.createSoraScheme(ideTheme)
                        lastAppliedThemeId = ideTheme.id
                    }
                }
            )
        }

        // 5. TinaIDE / VS Code Style Bottom Status & Linter Bar (24.dp)
        TinaIdeBottomStatusBar(
            ideTheme = ideTheme,
            pythonVersion = editorState.runtimeContextInfo.pythonVersion,
            tabWidth = settings.tabWidth,
            cursorLine = editorState.cursorLine + 1,
            cursorCol = editorState.cursorColumn + 1,
            outlineCount = editorState.outlineSymbols.size,
            warningCount = editorState.linterWarnings.size,
            firstDiagnostic = editorState.syntaxDiagnostics.firstOrNull(),
            onClickStatusOrOutline = {
                onRefreshRuntimeDiagnostics(false)
                showOutlineAndContextSheet = true
            },
            onClickGoToLine = { showGoToLineDialog = true },
            onClickDiagnostic = { diag ->
                val targetLine = (diag.line - 1).coerceAtLeast(0)
                runCatching {
                    editorRef?.setSelection(targetLine, (diag.column - 1).coerceAtLeast(0))
                    editorRef?.ensureSelectionVisible()
                }
            }
        )

        // 6. Pydroid 3 / Acode Flagship 2-Row Pro Coding Bar
        ProCodingKeyBar(
            ideTheme = ideTheme,
            twoRowMode = settings.twoRowSymbolBar,
            tabWidth = settings.tabWidth,
            onInsertSymbol = { sym, cursorOffset ->
                val editor = editorRef ?: return@ProCodingKeyBar
                editor.insertText(sym, cursorOffset)
                editor.requestFocus()
            },
            onUnindent = { unindentCurrentLine() },
            onToggleComment = { toggleCurrentLineComment() },
            onDuplicateLine = { duplicateCurrentLineDown() },
            onDeleteLine = { deleteCurrentLine() },
            onOpenSnippets = { showSnippetsDialog = true },
            onFormatQuick = triggerFormat,
            onOpenCommandPalette = { showCommandPalette = true },
            onMoveCursor = { dLine, dCol ->
                val editor = editorRef ?: return@ProCodingKeyBar
                runCatching {
                    val curL = editor.cursor.leftLine
                    val curC = editor.cursor.leftColumn
                    val newL = (curL + dLine).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                    val lineLen = editor.text.getColumnCount(newL)
                    val newC = when {
                        dCol == -9999 -> 0
                        dCol == 9999 -> lineLen
                        else -> (curC + dCol).coerceIn(0, lineLen)
                    }
                    editor.setSelection(newL, newC)
                    editor.ensureSelectionVisible()
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            editorRef = null
        }
    }

    // --- Modals & Bottom Sheets ---

    // 1. Acode-style Command Palette (`⚡ 命令面板`)
    if (showCommandPalette) {
        val actions = remember(editorState, settings) {
            listOf(
                CommandPaletteAction("format", "✨ 格式化 Python 代码 (PEP 8)", "自动对齐缩进、运算符与逗号空格 (支持撤销)", "Shift+Alt+F") {
                    triggerFormat()
                },
                CommandPaletteAction("theme", "🎨 切换编辑器配色主题 (6 套精选主题)", "当前: ${ideTheme.displayName}", "Theme") {
                    showThemePickerDialog = true
                },
                CommandPaletteAction("snippets", "🧩 插入常用 Python 代码模板", "内置 10+ 常用网络/文件/数据库/硬件模板", "Snippets") {
                    showSnippetsDialog = true
                },
                CommandPaletteAction("comment", "# 切换当前行注释", "在当前行首添加或移除 # 注释", "Ctrl+/") {
                    toggleCurrentLineComment()
                },
                CommandPaletteAction("dup_line", "📋 向下复制当前行 (Duplicate Line)", "快速克隆光标所在的代码行", "Shift+Alt+↓") {
                    duplicateCurrentLineDown()
                },
                CommandPaletteAction("del_line", "🗑️ 删除当前整行 (Delete Line)", "一键删除光标所在的整行代码", "Ctrl+Shift+K") {
                    deleteCurrentLine()
                },
                CommandPaletteAction("move_up", "⬆️ 向上移动当前行 (Move Line Up)", "将当前行与上一行互换位置", "Alt+↑") {
                    moveCurrentLineVertically(true)
                },
                CommandPaletteAction("move_down", "⬇️ 向下移动当前行 (Move Line Down)", "将当前行与下一行互换位置", "Alt+↓") {
                    moveCurrentLineVertically(false)
                },
                CommandPaletteAction("unindent", "⇤ 减少当前行缩进 (Unindent)", "向左退格 ${settings.tabWidth} 个空格", "Shift+Tab") {
                    unindentCurrentLine()
                },
                CommandPaletteAction("goto_line", "🔢 跳转到指定行号 (Go to Line...)", "输入行号快速定位光标", "Ctrl+G") {
                    showGoToLineDialog = true
                },
                CommandPaletteAction("run_args", "⚙️ 带命令行参数运行 (Run with sys.argv)", "当前参数: ${editorState.cliArgs.ifBlank { "(无)" }}", "sys.argv") {
                    showRunWithArgsDialog = true
                },
                CommandPaletteAction("find", "🔍 查找与正则表达式替换", "在当前脚本中搜索与批量替换", "Ctrl+F") {
                    onToggleFindReplace()
                },
                CommandPaletteAction("outline", "📊 代码大纲、静态检查与代码行数统计", "函数/类跳转、Flake8 风格警告与代码统计", "Outline") {
                    onRefreshRuntimeDiagnostics(false)
                    showOutlineAndContextSheet = true
                },
                CommandPaletteAction("reload_cache", "🔄 清理并重载 Python 模块缓存", "执行 importlib.invalidate_caches()", "Cache") {
                    onRefreshRuntimeDiagnostics(true)
                },
                CommandPaletteAction("share", "📤 分享当前 Python 脚本源码", "通过系统分享发送代码文本", "Share") {
                    shareCurrentCode()
                }
            )
        }
        CommandPaletteDialog(
            actions = actions,
            onDismiss = { showCommandPalette = false }
        )
    }

    // 2. Built-in Code Snippets Picker Modal
    if (showSnippetsDialog) {
        SnippetsPickerDialog(
            onDismiss = { showSnippetsDialog = false },
            onSelectSnippet = { snippet ->
                showSnippetsDialog = false
                val editor = editorRef ?: return@SnippetsPickerDialog
                editor.insertText(snippet.code, snippet.code.length)
                editor.requestFocus()
            }
        )
    }

    // 3. Quick IDE Color Theme Switcher Modal
    if (showThemePickerDialog) {
        ThemePickerDialog(
            currentTheme = ideTheme,
            onDismiss = { showThemePickerDialog = false },
            onSelectTheme = { chosen ->
                showThemePickerDialog = false
                onSelectIdeTheme(chosen)
            }
        )
    }

    // 4. Go to Line Dialog
    if (showGoToLineDialog) {
        val maxLine = (editorRef?.text?.lineCount ?: 1).coerceAtLeast(1)
        GoToLineDialog(
            maxLine = maxLine,
            onDismiss = { showGoToLineDialog = false },
            onConfirmLine = { lineOneBased ->
                showGoToLineDialog = false
                val targetZero = (lineOneBased - 1).coerceIn(0, maxLine - 1)
                runCatching {
                    editorRef?.setSelection(targetZero, 0)
                    editorRef?.ensureSelectionVisible()
                }
            }
        )
    }

    // 5. Run with CLI Arguments (`sys.argv`) Dialog
    if (showRunWithArgsDialog) {
        RunWithArgsDialog(
            initialArgs = editorState.cliArgs,
            scriptName = editorState.fileName,
            onDismiss = { showRunWithArgsDialog = false },
            onRunWithArgs = { customArgs ->
                showRunWithArgsDialog = false
                onRunWithArgsClicked(customArgs)
            }
        )
    }

    // 6. 3-Tab Code Outline, Static Linter & Metrics, and Runtime Context Sheet
    if (showOutlineAndContextSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineAndContextSheet = false }
        ) {
            OutlineAndContextSheetContent(
                outlineSymbols = editorState.outlineSymbols,
                syntaxDiagnostics = editorState.syntaxDiagnostics,
                linterWarnings = editorState.linterWarnings,
                codeMetrics = editorState.codeMetrics,
                runtimeInfo = editorState.runtimeContextInfo,
                onJumpToLine = { line ->
                    showOutlineAndContextSheet = false
                    val zeroBased = (line - 1).coerceAtLeast(0)
                    runCatching {
                        editorRef?.setSelection(zeroBased, 0)
                        editorRef?.ensureSelectionVisible()
                    }
                },
                onInvalidateImportCache = {
                    onRefreshRuntimeDiagnostics(true)
                }
            )
        }
    }

    if (showNewFileDialog) {
        FileNameInputDialog(
            title = "新建 Python 脚本",
            initialName = "script_${System.currentTimeMillis() % 1000}.py",
            confirmText = "创建",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                showNewFileDialog = false
                onNewFileClicked(name)
            }
        )
    }

    if (showSaveAsDialog) {
        FileNameInputDialog(
            title = "另存为新文件",
            initialName = editorState.fileName,
            confirmText = "保存",
            onDismiss = { showSaveAsDialog = false },
            onConfirm = { name ->
                showSaveAsDialog = false
                onSaveAsClicked(name)
            }
        )
    }
}

@Composable
private fun MultiTabStrip(
    tabs: List<EditorTabItem>,
    activeTabId: String,
    ideTheme: IdeColorTheme,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Surface(
        color = ideTheme.tabStripBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == activeTabId
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    color = if (isSelected) ideTheme.editorBg else Color.Transparent,
                    modifier = Modifier
                        .height(28.dp)
                        .clickable { onSelectTab(tab.id) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = when {
                                        tab.isModified -> Color(0xFFFACC15)
                                        isSelected -> ideTheme.accentColor
                                        else -> Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = tab.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ideTheme.textPrimary else ideTheme.textSecondary
                        )
                        if (tabs.size > 1) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭标签",
                                tint = ideTheme.textSecondary,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { onCloseTab(tab.id) }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onNewTab,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "新建脚本标签",
                    modifier = Modifier.size(15.dp),
                    tint = ideTheme.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TinaIdeBottomStatusBar(
    ideTheme: IdeColorTheme,
    pythonVersion: String,
    tabWidth: Int,
    cursorLine: Int,
    cursorCol: Int,
    outlineCount: Int,
    warningCount: Int,
    firstDiagnostic: AstSyntaxDiagnostic?,
    onClickStatusOrOutline: () -> Unit,
    onClickGoToLine: () -> Unit,
    onClickDiagnostic: (AstSyntaxDiagnostic) -> Unit
) {
    val bgColor = if (firstDiagnostic != null) {
        Color(0xFF7F1D1D)
    } else {
        ideTheme.statusBarBg
    }
    val fgColor = if (firstDiagnostic != null) {
        Color(0xFFFECACA)
    } else {
        ideTheme.textSecondary
    }

    Surface(
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (firstDiagnostic != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClickDiagnostic(firstDiagnostic) }
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ln ${firstDiagnostic.line}: ${firstDiagnostic.message}",
                        color = fgColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClickStatusOrOutline() }
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (warningCount > 0) Color(0xFFFACC15) else Color(0xFF22C55E),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val warnBadge = if (warningCount > 0) " • ⚠$warningCount" else ""
                    Text(
                        text = "Py$pythonVersion • UTF-8 • Tab:$tabWidth$warnBadge",
                        color = fgColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = "Ln $cursorLine:$cursorCol",
                color = ideTheme.accentColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clickable { onClickGoToLine() }
                    .padding(horizontal = 6.dp)
            )

            Text(
                text = "大纲($outlineCount) ▴",
                color = fgColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable { onClickStatusOrOutline() }
            )
        }
    }
}

@Composable
private fun ProCodingKeyBar(
    ideTheme: IdeColorTheme,
    twoRowMode: Boolean,
    tabWidth: Int,
    onInsertSymbol: (String, Int) -> Unit,
    onUnindent: () -> Unit,
    onToggleComment: () -> Unit,
    onDuplicateLine: () -> Unit,
    onDeleteLine: () -> Unit,
    onOpenSnippets: () -> Unit,
    onFormatQuick: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    onMoveCursor: (Int, Int) -> Unit
) {
    val symbols = listOf(
        "Tab" to (" ".repeat(tabWidth) to tabWidth),
        ":" to (":" to 1),
        "(" to ("()" to 1),
        ")" to (")" to 1),
        "[" to ("[]" to 1),
        "]" to ("]" to 1),
        "{" to ("{}" to 1),
        "}" to ("}" to 1),
        "\"" to ("\"\"" to 1),
        "'" to ("''" to 1),
        "=" to (" = " to 3),
        "==" to (" == " to 4),
        "!=" to (" != " to 4),
        "+" to (" + " to 3),
        "-" to (" - " to 3),
        "*" to ("*" to 1),
        "/" to ("/" to 1),
        "_" to ("_" to 1),
        "." to ("." to 1),
        "," to (", " to 2),
        "->" to (" -> " to 4),
        "def" to ("def " to 4),
        "for" to ("for " to 4),
        "if" to ("if " to 3),
        "print" to ("print()" to 6),
        "import" to ("import " to 7)
    )

    Surface(
        color = ideTheme.chromeBg,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1 (Pydroid 3 / Acode Pro Action & Cursor Row)
            if (twoRowMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProKeyPill("⚡命令", ideTheme.accentColor, Color(0xFF0F172A), onClick = onOpenCommandPalette)
                    ProKeyPill("🧩片段", ideTheme.tabStripBg, ideTheme.accentColor, onClick = onOpenSnippets)
                    ProKeyPill("✨格式化", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = onFormatQuick)
                    ProKeyPill("⇧Tab", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = onUnindent)
                    ProKeyPill("#注释", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = onToggleComment)
                    ProKeyPill("复制行", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = onDuplicateLine)
                    ProKeyPill("删行", ideTheme.tabStripBg, Color(0xFFF87171), onClick = onDeleteLine)
                    ProKeyPill("←", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = { onMoveCursor(0, -1) })
                    ProKeyPill("↓", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = { onMoveCursor(1, 0) })
                    ProKeyPill("↑", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = { onMoveCursor(-1, 0) })
                    ProKeyPill("→", ideTheme.tabStripBg, ideTheme.textPrimary, onClick = { onMoveCursor(0, 1) })
                    ProKeyPill("HOME", ideTheme.tabStripBg, ideTheme.textSecondary, onClick = { onMoveCursor(0, -9999) })
                    ProKeyPill("END", ideTheme.tabStripBg, ideTheme.textSecondary, onClick = { onMoveCursor(0, 9999) })
                }
            }

            // Row 2 (High-speed Python Symbols & Keywords Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!twoRowMode) {
                    ProKeyPill("⚡", ideTheme.accentColor, Color(0xFF0F172A), onClick = onOpenCommandPalette)
                    ProKeyPill("🧩", ideTheme.tabStripBg, ideTheme.accentColor, onClick = onOpenSnippets)
                }
                symbols.forEach { (label, pair) ->
                    val (insertText, offset) = pair
                    ProKeyPill(
                        label = label,
                        bgColor = ideTheme.editorBg,
                        fgColor = if (label in setOf("def", "for", "if", "print", "import", "Tab")) ideTheme.accentColor else ideTheme.textPrimary,
                        onClick = { onInsertSymbol(insertText, offset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProKeyPill(
    label: String,
    bgColor: Color,
    fgColor: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp)
    ) {
        Text(
            text = label,
            color = fgColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CommandPaletteDialog(
    actions: List<CommandPaletteAction>,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, actions) {
        if (query.isBlank()) actions
        else actions.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true) ||
                it.badge.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("⚡ 命令面板 (Command Palette)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索指令 (如: 格式化、注释、主题、跳行、参数)...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { act ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    act.onExecute()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(act.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        act.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = act.badge,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun SnippetsPickerDialog(
    onDismiss: () -> Unit,
    onSelectSnippet: (PythonSnippetTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🧩 Python 常用代码片段模板库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BUILTIN_SNIPPET_TEMPLATES) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSnippet(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.category,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.code.trim(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ThemePickerDialog(
    currentTheme: IdeColorTheme,
    onDismiss: () -> Unit,
    onSelectTheme: (IdeColorTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🎨 选择 IDE 专属配色主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IdeColorTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.editorBg)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) theme.accentColor else theme.textSecondary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectTheme(theme) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelectTheme(theme) })
                        Text(
                            text = theme.displayName,
                            color = theme.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(theme.accentColor, CircleShape)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun GoToLineDialog(
    maxLine: Int,
    onDismiss: () -> Unit,
    onConfirmLine: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔢 跳转到指定行 (1 ~ $maxLine)") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { ch -> ch.isDigit() } },
                label = { Text("输入目标行号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ln = input.toIntOrNull()
                    if (ln != null) onConfirmLine(ln)
                },
                enabled = input.toIntOrNull() != null
            ) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun RunWithArgsDialog(
    initialArgs: String,
    scriptName: String,
    onDismiss: () -> Unit,
    onRunWithArgs: (String) -> Unit
) {
    var argsText by remember { mutableStateOf(initialArgs) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ 带命令行参数运行 (sys.argv)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "相当于执行: python $scriptName <参数>",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    placeholder = { Text("例如: --input data.csv --verbose") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRunWithArgs(argsText.trim()) }) {
                Text("立即运行 ▶")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun OutlineAndContextSheetContent(
    outlineSymbols: List<AstOutlineSymbol>,
    syntaxDiagnostics: List<AstSyntaxDiagnostic>,
    linterWarnings: List<AstLinterWarning>,
    codeMetrics: CodeMetrics,
    runtimeInfo: RuntimeContextInfo,
    onJumpToLine: (Int) -> Unit,
    onInvalidateImportCache: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .padding(horizontal = 16.dp)
    ) {
        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("大纲 (${outlineSymbols.size})", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("检查与统计 (${syntaxDiagnostics.size + linterWarnings.size})", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSection == 2,
                onClick = { selectedSection = 2 },
                text = { Text("运行环境", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedSection) {
            0 -> {
                if (outlineSymbols.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("当前脚本未定义 class 或 def 函数", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(outlineSymbols) { sym ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJumpToLine(sym.line) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (sym.kind == "class") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (sym.kind == "class") "C" else "ƒ",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = sym.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Ln ${sym.line}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("总行数: ${codeMetrics.totalLines}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("有效代码: ${codeMetrics.codeLines}", fontSize = 12.sp)
                                Text("注释: ${codeMetrics.commentLines}", fontSize = 12.sp)
                                Text("函数/类: ${codeMetrics.funcCount}/${codeMetrics.classCount}", fontSize = 12.sp)
                            }
                        }
                    }

                    items(syntaxDiagnostics) { diag ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToLine(diag.line) }
                        ) {
                            Text(
                                text = "❌ [语法错误] Ln ${diag.line}: ${diag.message}",
                                color = Color(0xFFFECACA),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    items(linterWarnings) { warn ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToLine(warn.line) }
                        ) {
                            Text(
                                text = "⚠️ [${warn.code}] Ln ${warn.line}: ${warn.message}",
                                color = Color(0xFFFEF08A),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (syntaxDiagnostics.isEmpty() && linterWarnings.isEmpty()) {
                        item {
                            Text(
                                text = "✅ AST 静态语法检查与代码规范扫描通过，未发现潜在问题。",
                                color = Color(0xFF4ADE80),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CPython ${runtimeInfo.pythonVersion} (Chaquopy 17.1.0)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${runtimeInfo.platform} • 已加载模块: ${runtimeInfo.loadedModulesCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = onInvalidateImportCache) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重载缓存", fontSize = 12.sp)
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("当前工作目录 (cwd):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(runtimeInfo.cwd, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("私有 pip 库目录 (site-packages):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(runtimeInfo.userSitePackages, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }

                    item {
                        Text(
                            text = "sys.path 模块搜索优先级列表 (${runtimeInfo.sysPath.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(runtimeInfo.sysPath.mapIndexed { idx, p -> idx to p }) { (idx, pathStr) ->
                        Text(
                            text = "[$idx] $pathStr",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (idx < 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(30.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun FindReplaceToolbar(
    findQuery: String,
    replaceText: String,
    caseSensitive: Boolean,
    useRegex: Boolean,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onToggleRegex: () -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("查找内容...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFindPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = onFindNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭查找栏")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = onReplaceChange,
                    placeholder = { Text("替换为...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReplaceOne) {
                    Text("替换", fontSize = 12.sp)
                }
                TextButton(onClick = onReplaceAll) {
                    Text("全部", fontSize = 12.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = caseSensitive,
                    onClick = onToggleCase,
                    label = { Text("区分大小写 (Aa)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = useRegex,
                    onClick = onToggleRegex,
                    label = { Text("正则表达式 (.*)", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun FileNameInputDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
