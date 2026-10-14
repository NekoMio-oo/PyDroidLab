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
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showRunWithArgsDialog by remember { mutableStateOf(false) }

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

    val toggleCurrentLineComment = {
        val editor = editorRef
        if (editor != null) {
            runCatching {
                val ln = editor.cursor.leftLine
                val col = editor.cursor.leftColumn
                val lineStr = editor.text.getLineString(ln)
                val trimmed = lineStr.trimStart()
                val indent = lineStr.substring(0, lineStr.length - trimmed.length)
                val newLineStr = when {
                    trimmed.startsWith("# ") -> indent + trimmed.removePrefix("# ")
                    trimmed.startsWith("#") -> indent + trimmed.removePrefix("#")
                    else -> "$indent# $trimmed"
                }
                editor.text.replace(ln, 0, ln, lineStr.length, newLineStr)
                editor.setSelection(ln, (col + (newLineStr.length - lineStr.length)).coerceIn(0, newLineStr.length))
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
            context.startActivity(Intent.createChooser(sendIntent, editorState.fileName))
        }
    }

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
                container.addDiagnostic(DiagnosticRegion(startIndex, endIndex, DiagnosticRegion.SEVERITY_ERROR))
            }
            for (warn in editorState.linterWarnings) {
                val lineIdx = (warn.line - 1).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                val lineLen = editor.text.getColumnCount(lineIdx)
                if (lineLen > 0) {
                    val startIndex = editor.text.getCharIndex(lineIdx, 0)
                    val endIndex = editor.text.getCharIndex(lineIdx, lineLen).coerceAtLeast(startIndex + 1)
                    container.addDiagnostic(DiagnosticRegion(startIndex, endIndex, DiagnosticRegion.SEVERITY_WARNING))
                }
            }
            editor.diagnostics = container
        }
    }

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
        // 1. Minimalist TinaIDE Top Bar (44.dp) — Only Menu, Title, Undo, Redo, Terminal, Run, More
        Surface(
            color = ideTheme.chromeBg,
            contentColor = ideTheme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MinimalIconButton(
                    icon = Icons.Outlined.Menu,
                    contentDescription = "菜单",
                    tint = ideTheme.textPrimary,
                    onClick = onOpenDrawer
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = editorState.fileName + if (editorState.isModified) " *" else "",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = ideTheme.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                MinimalIconButton(
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = "撤销",
                    enabled = editorState.canUndo,
                    tint = if (editorState.canUndo) ideTheme.textPrimary else ideTheme.textSecondary.copy(alpha = 0.35f),
                    onClick = { editorRef?.undo() }
                )

                MinimalIconButton(
                    icon = Icons.AutoMirrored.Outlined.Redo,
                    contentDescription = "重做",
                    enabled = editorState.canRedo,
                    tint = if (editorState.canRedo) ideTheme.textPrimary else ideTheme.textSecondary.copy(alpha = 0.35f),
                    onClick = { editorRef?.redo() }
                )

                MinimalIconButton(
                    icon = Icons.Outlined.Terminal,
                    contentDescription = "终端",
                    tint = ideTheme.textPrimary,
                    onClick = onToggleTerminal
                )

                if (isRunning) {
                    MinimalIconButton(
                        icon = Icons.Outlined.Stop,
                        contentDescription = "停止",
                        tint = Color(0xFFF85149),
                        onClick = onStopClicked
                    )
                } else {
                    MinimalIconButton(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = "运行",
                        tint = ideTheme.accentColor,
                        onClick = onRunClicked
                    )
                }

                Box {
                    MinimalIconButton(
                        icon = Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        tint = ideTheme.textPrimary,
                        onClick = { showOverflowMenu = true }
                    )
                    // Pure text menu without clutter or emojis
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("保存", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                onSaveClicked()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("格式化代码", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                triggerFormat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("切换行注释", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                toggleCurrentLineComment()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("查找与替换", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                onToggleFindReplace()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("跳转到行", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                showGoToLineDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("代码大纲与诊断", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                onRefreshRuntimeDiagnostics(false)
                                showOutlineSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("运行参数", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                showRunWithArgsDialog = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("新建文件", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                showNewFileDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("另存为", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                showSaveAsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("分享源码", fontSize = 14.sp) },
                            onClick = {
                                showOverflowMenu = false
                                shareCurrentCode()
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = ideTheme.textSecondary.copy(alpha = 0.2f))

        // 2. Minimalist File Tab Bar (30.dp) — Flat tabs with thin bottom indicator
        MinimalTabStrip(
            tabs = editorState.openTabs,
            activeTabId = editorState.activeTabId,
            ideTheme = ideTheme,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onNewTab = { showNewFileDialog = true }
        )

        HorizontalDivider(thickness = 0.5.dp, color = ideTheme.textSecondary.copy(alpha = 0.2f))

        // 3. Clean Collapsible Find & Replace Bar
        AnimatedVisibility(visible = editorState.showFindReplaceBar) {
            MinimalFindReplaceBar(
                ideTheme = ideTheme,
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

        // 4. Sora CodeEditor Canvas
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
                            onTextChanged(
                                text.toString(),
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

        HorizontalDivider(thickness = 0.5.dp, color = ideTheme.textSecondary.copy(alpha = 0.2f))

        // 5. Minimalist TinaIDE Status Bar (22.dp) — Pure monospace text, no icons
        MinimalStatusBar(
            ideTheme = ideTheme,
            pythonVersion = editorState.runtimeContextInfo.pythonVersion,
            tabWidth = settings.tabWidth,
            cursorLine = editorState.cursorLine + 1,
            cursorCol = editorState.cursorColumn + 1,
            firstDiagnostic = editorState.syntaxDiagnostics.firstOrNull(),
            onClickLeft = {
                onRefreshRuntimeDiagnostics(false)
                showOutlineSheet = true
            },
            onClickRight = { showGoToLineDialog = true },
            onClickDiagnostic = { diag ->
                val targetLine = (diag.line - 1).coerceAtLeast(0)
                runCatching {
                    editorRef?.setSelection(targetLine, (diag.column - 1).coerceAtLeast(0))
                    editorRef?.ensureSelectionVisible()
                }
            }
        )

        HorizontalDivider(thickness = 0.5.dp, color = ideTheme.textSecondary.copy(alpha = 0.15f))

        // 6. Clean Single-Row Minimal Symbol Bar (34.dp) — Pure monospace symbols, no colored pills
        MinimalSymbolBar(
            ideTheme = ideTheme,
            tabWidth = settings.tabWidth,
            onInsertSymbol = { sym, cursorOffset ->
                val editor = editorRef ?: return@MinimalSymbolBar
                editor.insertText(sym, cursorOffset)
                editor.requestFocus()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            editorRef = null
        }
    }

    if (showOutlineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false }
        ) {
            MinimalOutlineAndDiagnosticsSheet(
                outlineSymbols = editorState.outlineSymbols,
                syntaxDiagnostics = editorState.syntaxDiagnostics,
                linterWarnings = editorState.linterWarnings,
                codeMetrics = editorState.codeMetrics,
                runtimeInfo = editorState.runtimeContextInfo,
                onJumpToLine = { line ->
                    showOutlineSheet = false
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

    if (showNewFileDialog) {
        FileNameInputDialog(
            title = "新建文件",
            initialName = "untitled.py",
            confirmText = "确定",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                showNewFileDialog = false
                onNewFileClicked(name)
            }
        )
    }

    if (showSaveAsDialog) {
        FileNameInputDialog(
            title = "另存为",
            initialName = editorState.fileName,
            confirmText = "确定",
            onDismiss = { showSaveAsDialog = false },
            onConfirm = { name ->
                showSaveAsDialog = false
                onSaveAsClicked(name)
            }
        )
    }
}

@Composable
private fun MinimalIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MinimalTabStrip(
    tabs: List<EditorTabItem>,
    activeTabId: String,
    ideTheme: IdeColorTheme,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(ideTheme.tabStripBg)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == activeTabId
            Column(
                modifier = Modifier
                    .height(30.dp)
                    .background(if (isSelected) ideTheme.editorBg else Color.Transparent)
                    .clickable { onSelectTab(tab.id) },
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = tab.title + if (tab.isModified) " *" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) ideTheme.textPrimary else ideTheme.textSecondary
                    )
                    if (tabs.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = ideTheme.textSecondary,
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onCloseTab(tab.id) }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) ideTheme.accentColor else Color.Transparent)
                )
            }
        }

        IconButton(
            onClick = onNewTab,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "新建",
                modifier = Modifier.size(15.dp),
                tint = ideTheme.textSecondary
            )
        }
    }
}

@Composable
private fun MinimalStatusBar(
    ideTheme: IdeColorTheme,
    pythonVersion: String,
    tabWidth: Int,
    cursorLine: Int,
    cursorCol: Int,
    firstDiagnostic: AstSyntaxDiagnostic?,
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    onClickDiagnostic: (AstSyntaxDiagnostic) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(ideTheme.statusBarBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (firstDiagnostic != null) {
            Text(
                text = "行 ${firstDiagnostic.line}: ${firstDiagnostic.message}",
                color = Color(0xFFF85149),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClickDiagnostic(firstDiagnostic) }
            )
        } else {
            Text(
                text = "Python $pythonVersion   UTF-8   缩进 $tabWidth",
                color = ideTheme.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClickLeft() }
            )
        }

        Text(
            text = "行 $cursorLine, 列 $cursorCol",
            color = ideTheme.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.clickable { onClickRight() }
        )
    }
}

@Composable
private fun MinimalSymbolBar(
    ideTheme: IdeColorTheme,
    tabWidth: Int,
    onInsertSymbol: (String, Int) -> Unit
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
        "+" to (" + " to 3),
        "-" to (" - " to 3),
        "*" to ("*" to 1),
        "/" to ("/" to 1),
        "_" to ("_" to 1),
        "." to ("." to 1),
        "," to (", " to 2),
        "#" to ("# " to 2)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(ideTheme.chromeBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEach { (label, pair) ->
            val (insertText, offset) = pair
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(30.dp)
                    .clickable { onInsertSymbol(insertText, offset) }
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = label,
                    color = ideTheme.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MinimalFindReplaceBar(
    ideTheme: IdeColorTheme,
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
        color = ideTheme.chromeBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("查找", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                MinimalIconButton(Icons.Outlined.KeyboardArrowUp, "上一个", tint = ideTheme.textPrimary, onClick = onFindPrev)
                MinimalIconButton(Icons.Outlined.KeyboardArrowDown, "下一个", tint = ideTheme.textPrimary, onClick = onFindNext)
                MinimalIconButton(Icons.Outlined.Close, "关闭", tint = ideTheme.textPrimary, onClick = onClose)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = onReplaceChange,
                    placeholder = { Text("替换", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReplaceOne) { Text("替换", fontSize = 12.sp) }
                TextButton(onClick = onReplaceAll) { Text("全部", fontSize = 12.sp) }
                TextButton(onClick = onToggleCase) {
                    Text("Aa", color = if (caseSensitive) ideTheme.accentColor else ideTheme.textSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = onToggleRegex) {
                    Text(".*", color = if (useRegex) ideTheme.accentColor else ideTheme.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MinimalOutlineAndDiagnosticsSheet(
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
            .height(400.dp)
            .padding(horizontal = 16.dp)
    ) {
        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("大纲", fontSize = 13.sp) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("诊断", fontSize = 13.sp) }
            )
            Tab(
                selected = selectedSection == 2,
                onClick = { selectedSection = 2 },
                text = { Text("环境", fontSize = 13.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedSection) {
            0 -> {
                if (outlineSymbols.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无符号定义", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn {
                        items(outlineSymbols) { sym ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJumpToLine(sym.line) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sym.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "行 ${sym.line}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "总行数 ${codeMetrics.totalLines}  •  有效代码 ${codeMetrics.codeLines}  •  函数 ${codeMetrics.funcCount}  •  类 ${codeMetrics.classCount}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }

                    items(syntaxDiagnostics) { diag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToLine(diag.line) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "行 ${diag.line}: ${diag.message}",
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }

                    items(linterWarnings) { warn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToLine(warn.line) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "行 ${warn.line} [${warn.code}]: ${warn.message}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }

                    if (syntaxDiagnostics.isEmpty() && linterWarnings.isEmpty()) {
                        item {
                            Text(
                                text = "未发现语法或规范问题",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Python ${runtimeInfo.pythonVersion}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = runtimeInfo.platform,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onInvalidateImportCache) {
                                Text("刷新模块缓存", fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }

                    items(runtimeInfo.sysPath) { pathStr ->
                        Text(
                            text = pathStr,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
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
        title = { Text("跳转到行", fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { ch -> ch.isDigit() } },
                label = { Text("行号 (1 ~ $maxLine)") },
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
                Text("确定")
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
        title = { Text("运行参数", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "python $scriptName",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    placeholder = { Text("命令行参数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRunWithArgs(argsText.trim()) }) {
                Text("运行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
        title = { Text(title, fontSize = 16.sp) },
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
