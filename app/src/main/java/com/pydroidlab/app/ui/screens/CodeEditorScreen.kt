package com.pydroidlab.app.ui.screens

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pydroidlab.app.data.AppSettings
import com.pydroidlab.app.data.ThemeMode
import com.pydroidlab.app.editor.PythonSoraLanguage
import com.pydroidlab.app.runtime.AstOutlineSymbol
import com.pydroidlab.app.runtime.AstSyntaxDiagnostic
import com.pydroidlab.app.runtime.RuntimeContextInfo
import com.pydroidlab.app.ui.EditorTabItem
import com.pydroidlab.app.ui.EditorUiState
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse

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
    onStopClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onSaveAsClicked: (String) -> Unit,
    onNewFileClicked: (String) -> Unit,
    onToggleFindReplace: () -> Unit,
    onUpdateFindReplace: (String, String, Boolean, Boolean) -> Unit,
    onToggleTerminal: () -> Unit,
    onRefreshRuntimeDiagnostics: (Boolean) -> Unit,
    onConsumeJumpToLine: () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkEditor = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var lastLoadedVersion by remember { mutableLongStateOf(-1L) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showOutlineAndContextSheet by remember { mutableStateOf(false) }

    // Apply Undoable code formatting into Sora CodeEditor
    val triggerFormat = {
        onFormatCode { formattedText ->
            val editor = editorRef ?: return@onFormatCode
            runCatching {
                val curLine = editor.cursor.leftLine
                val curCol = editor.cursor.leftColumn
                val lastLine = (editor.text.lineCount - 1).coerceAtLeast(0)
                val lastCol = editor.text.getColumnCount(lastLine)
                // Using editor.text.replace preserves Undo/Redo stack!
                editor.text.replace(0, 0, lastLine, lastCol, formattedText)
                val targetLine = curLine.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                val targetCol = curCol.coerceIn(0, editor.text.getColumnCount(targetLine))
                editor.setSelection(targetLine, targetCol)
            }
        }
    }

    // Synchronize external file loads, tab switches, or sample loads into the Sora CodeEditor instance
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

    // Handle Jump-To-Line requests from Terminal Traceback clicks or Code Outline selection
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

    // Push real-time `ast.parse` syntax diagnostics into Sora Editor's DiagnosticsContainer (draws red wave underline!)
    LaunchedEffect(editorState.syntaxDiagnostics, editorRef) {
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
                    DiagnosticRegion(
                        startIndex,
                        endIndex,
                        DiagnosticRegion.SEVERITY_ERROR
                    )
                )
            }
            editor.diagnostics = container
        }
    }

    // Apply Find query changes dynamically to Sora EditorSearcher
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. TinaIDE-style Sleek Top App Bar (46.dp) with Left Drawer Button (`☰`) + Title + Core Actions
        Surface(
            tonalElevation = 2.dp,
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
                // Left Drawer Toggle (`☰` opens TinaIDE Workspace & Navigation Drawer)
                CompactToolbarIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "打开工作区抽屉与功能菜单",
                    onClick = onOpenDrawer
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Active file title
                Text(
                    text = editorState.fileName + if (editorState.isModified) " *" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Core Editor Actions: Undo, Redo, Format (✨), Save, Terminal, Run, More (⋮)
                CompactToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    enabled = editorState.canUndo,
                    onClick = { editorRef?.undo() }
                )

                CompactToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "重做",
                    enabled = editorState.canRedo,
                    onClick = { editorRef?.redo() }
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.AutoFixHigh,
                    contentDescription = "PEP 8 格式化代码",
                    onClick = triggerFormat
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Save,
                    contentDescription = "保存文件",
                    onClick = onSaveClicked
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Terminal,
                    contentDescription = "打开终端",
                    onClick = onToggleTerminal
                )

                Spacer(modifier = Modifier.width(3.dp))

                if (isRunning) {
                    FilledIconButton(
                        onClick = onStopClicked,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止运行",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onRunClicked,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "运行脚本",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Box {
                    CompactToolbarIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "更多功能",
                        onClick = { showOverflowMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("格式化代码 (PEP 8 Format)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                triggerFormat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("查找与替换 (Find & Replace)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onToggleFindReplace()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("代码大纲与编译环境诊断") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onRefreshRuntimeDiagnostics(false)
                                showOutlineAndContextSheet = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("新建脚本 (New .py)") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showNewFileDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("另存为 (Save As...)") },
                            leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showSaveAsDialog = true
                            }
                        )
                    }
                }
            }
        }

        // 2. Always-Visible TinaIDE Multi-Tab Strip (32.dp) with `+` quick new script button
        MultiTabStrip(
            tabs = editorState.openTabs,
            activeTabId = editorState.activeTabId,
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

        HorizontalDivider()

        // 4. Rosemoe Sora CodeEditor View (Occupies full immersive workspace height!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    CodeEditor(context).apply {
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
                        colorScheme = if (useDarkEditor) SchemeDarcula() else SchemeEclipse()
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

                    val isCurrentlyDarcula = editor.colorScheme is SchemeDarcula
                    if (useDarkEditor && !isCurrentlyDarcula) {
                        editor.colorScheme = SchemeDarcula()
                    } else if (!useDarkEditor && isCurrentlyDarcula) {
                        editor.colorScheme = SchemeEclipse()
                    }
                }
            )
        }

        // 5. TinaIDE-style Compact Bottom Status & Context Bar (24.dp)
        TinaIdeBottomStatusBar(
            pythonVersion = editorState.runtimeContextInfo.pythonVersion,
            tabWidth = settings.tabWidth,
            cursorLine = editorState.cursorLine + 1,
            cursorCol = editorState.cursorColumn + 1,
            outlineCount = editorState.outlineSymbols.size,
            firstDiagnostic = editorState.syntaxDiagnostics.firstOrNull(),
            onClickStatusOrOutline = {
                onRefreshRuntimeDiagnostics(false)
                showOutlineAndContextSheet = true
            },
            onClickDiagnostic = { diag ->
                val targetLine = (diag.line - 1).coerceAtLeast(0)
                runCatching {
                    editorRef?.setSelection(targetLine, (diag.column - 1).coerceAtLeast(0))
                    editorRef?.ensureSelectionVisible()
                }
            }
        )

        // 6. Slim 36.dp Quick Symbol Input Bar
        QuickSymbolBar(
            tabWidth = settings.tabWidth,
            onInsertSymbol = { sym, cursorOffset ->
                val editor = editorRef ?: return@QuickSymbolBar
                editor.insertText(sym, cursorOffset)
                editor.requestFocus()
            },
            onFormatQuick = triggerFormat
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            editorRef = null
        }
    }

    if (showOutlineAndContextSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineAndContextSheet = false }
        ) {
            OutlineAndContextSheetContent(
                outlineSymbols = editorState.outlineSymbols,
                syntaxDiagnostics = editorState.syntaxDiagnostics,
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
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    tonalElevation = if (isSelected) 2.dp else 0.dp,
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
                                        isSelected -> MaterialTheme.colorScheme.primary
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tabs.size > 1) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭标签",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TinaIdeBottomStatusBar(
    pythonVersion: String,
    tabWidth: Int,
    cursorLine: Int,
    cursorCol: Int,
    outlineCount: Int,
    firstDiagnostic: AstSyntaxDiagnostic?,
    onClickStatusOrOutline: () -> Unit,
    onClickDiagnostic: (AstSyntaxDiagnostic) -> Unit
) {
    val bgColor = if (firstDiagnostic != null) {
        Color(0xFF7F1D1D)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val fgColor = if (firstDiagnostic != null) {
        Color(0xFFFECACA)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Python $pythonVersion • UTF-8 • Spaces: $tabWidth",
                        color = fgColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = "Ln $cursorLine, Col $cursorCol  |  大纲($outlineCount) ▴",
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
private fun OutlineAndContextSheetContent(
    outlineSymbols: List<AstOutlineSymbol>,
    syntaxDiagnostics: List<AstSyntaxDiagnostic>,
    runtimeInfo: RuntimeContextInfo,
    onJumpToLine: (Int) -> Unit,
    onInvalidateImportCache: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .padding(horizontal = 16.dp)
    ) {
        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("代码大纲 (${outlineSymbols.size})") }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("Python 运行上下文诊断") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedSection == 0) {
            if (syntaxDiagnostics.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onJumpToLine(syntaxDiagnostics.first().line) }
                ) {
                    Text(
                        text = "⚠️ 语法错误 (第 ${syntaxDiagnostics.first().line} 行): ${syntaxDiagnostics.first().message}",
                        color = Color(0xFFFECACA),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (outlineSymbols.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "当前脚本未定义 class 或 def 函数",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        } else {
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
                                text = "CPython ${runtimeInfo.pythonVersion} (Chaquopy 15.0.1)",
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

@Composable
private fun CompactToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
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
private fun QuickSymbolBar(
    tabWidth: Int,
    onInsertSymbol: (String, Int) -> Unit,
    onFormatQuick: () -> Unit
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
        "#" to ("# " to 2),
        "_" to ("_" to 1),
        "+" to (" + " to 3),
        "-" to (" - " to 3),
        "*" to ("*" to 1),
        "/" to ("/" to 1),
        "->" to (" -> " to 4)
    )

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Quick Format Pill right at the start of the symbol bar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onFormatQuick() }
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "✨ 格式化",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            symbols.forEach { (label, pair) ->
                val (insertText, offset) = pair
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                        .clickable { onInsertSymbol(insertText, offset) }
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
