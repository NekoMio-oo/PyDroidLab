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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pydroidlab.app.ui.EditorUiState
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse

@Composable
fun CodeEditorScreen(
    editorState: EditorUiState,
    settings: AppSettings,
    isRunning: Boolean,
    onTextChanged: (String, Int, Int, Boolean, Boolean) -> Unit,
    onCursorChanged: (Int, Int, Boolean, Boolean) -> Unit,
    onRunClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onSaveAsClicked: (String) -> Unit,
    onNewFileClicked: (String) -> Unit,
    onToggleFindReplace: () -> Unit,
    onUpdateFindReplace: (String, String, Boolean, Boolean) -> Unit,
    onToggleTerminal: () -> Unit
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

    // Synchronize external file loads or sample loads into the Sora CodeEditor instance
    LaunchedEffect(editorState.contentVersion, editorRef) {
        val editor = editorRef ?: return@LaunchedEffect
        if (lastLoadedVersion != editorState.contentVersion) {
            lastLoadedVersion = editorState.contentVersion
            if (editor.text.toString() != editorState.codeContent) {
                editor.setText(editorState.codeContent)
            }
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
        // 1. Sleek, fixed-height (48.dp) Single-Line Top Toolbar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: File name + compact cursor position (strictly single-line, never wraps vertically)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = editorState.fileName + if (editorState.isModified) " *" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ln ${editorState.cursorLine + 1}:${editorState.cursorColumn + 1} • Tab ${settings.tabWidth}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                // Right: Compact 34.dp action buttons so the filename always has ample horizontal width
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
                    icon = Icons.Default.Search,
                    contentDescription = "查找与替换",
                    onClick = onToggleFindReplace
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Save,
                    contentDescription = "保存文件",
                    onClick = onSaveClicked
                )

                CompactToolbarIconButton(
                    icon = Icons.Default.Terminal,
                    contentDescription = "终端控制台",
                    onClick = onToggleTerminal
                )

                Spacer(modifier = Modifier.width(4.dp))

                if (isRunning) {
                    FilledIconButton(
                        onClick = onStopClicked,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止运行",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onRunClicked,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "运行脚本",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Overflow menu for New File / Save As
                Box {
                    CompactToolbarIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        onClick = { showOverflowMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
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

        // 2. Collapsible Find & Replace Toolbar
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

        // 3. Rosemoe Sora CodeEditor View (Occupies maximum screen height)
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

        // 4. Slim 36.dp Quick Symbol Input Bar
        QuickSymbolBar(
            tabWidth = settings.tabWidth,
            onInsertSymbol = { sym, cursorOffset ->
                val editor = editorRef ?: return@QuickSymbolBar
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
private fun CompactToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
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
            modifier = Modifier.size(19.dp)
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
            .height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            symbols.forEach { (label, pair) ->
                val (insertText, offset) = pair
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(28.dp)
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
