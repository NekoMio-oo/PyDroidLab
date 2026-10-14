package com.pydroidlab.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.terminal.TerminalLine
import com.pydroidlab.terminal.TerminalSegment
import com.pydroidlab.terminal.TerminalSnapshot

private val TRACEBACK_LINE_REGEX = Regex("""\bline\s+(\d+)\b""", RegexOption.IGNORE_CASE)

/**
 * Termux-style Interactive Terminal Console:
 * - No clunky external input box or "Evaluate/Send" button.
 * - Tapping anywhere on the terminal screen focuses the inline prompt (`>>> `, `... `, or `input()` prompt)
 *   and brings up the soft keyboard.
 * - Pressing Enter (`↵`) on the soft keyboard immediately executes the command or submits `input()`.
 * - Includes a Termux-style Extra Keys Bar (`ESC`, `TAB`, `CTRL+C`, `CTRL+L`, `↑`, `↓`, `←`, `→`, `HOME`, `END`, `↵`)
 *   with full command history navigation (`↑` / `↓`).
 */
@Composable
fun TerminalConsoleSheet(
    snapshot: TerminalSnapshot,
    fontSizeSp: Float,
    isFullScreen: Boolean,
    isReplMode: Boolean,
    replContinuation: Boolean,
    onToggleFullScreen: (Boolean) -> Unit,
    onToggleReplMode: (Boolean) -> Unit,
    onResetRepl: () -> Unit,
    onSubmitInput: (String) -> Unit,
    onJumpToLine: (Int) -> Unit,
    onStopScript: () -> Unit,
    onRerunScript: () -> Unit,
    onClearTerminal: () -> Unit,
    onCloseSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tfv by remember { mutableStateOf(TextFieldValue("")) }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }

    val isInteractiveLineVisible = snapshot.isRunning || isReplMode

    BackHandler(enabled = isFullScreen) {
        onCloseSheet()
    }

    fun commitLine(rawLine: String) {
        val cleaned = rawLine.replace("\r", "")
        if (cleaned.isNotBlank() && (commandHistory.isEmpty() || commandHistory.last() != cleaned)) {
            commandHistory.add(cleaned)
        }
        historyIndex = commandHistory.size
        onSubmitInput(cleaned)
    }

    // Auto-scroll to the active bottom prompt line whenever output or typing changes
    LaunchedEffect(snapshot.lines.size, snapshot.lines.lastOrNull()?.plainText, tfv.text, isReplMode) {
        val totalItems = snapshot.lines.size + 1
        if (totalItems > 0) {
            runCatching { listState.animateScrollToItem(totalItems - 1) }
        }
    }

    // Automatically focus the inline terminal cursor when `input()` is requested or REPL is opened
    LaunchedEffect(snapshot.isAwaitingInput, isReplMode) {
        if (snapshot.isAwaitingInput || isReplMode) {
            runCatching {
                inputFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF090D16),
        contentColor = Color(0xFFE2E8F0),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Compact Terminal Header Bar (34.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFullScreen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCloseSheet() }
                            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回编辑器",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "代码",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when {
                                snapshot.isAwaitingInput -> Color(0xFFFACC15)
                                snapshot.isRunning -> Color(0xFF22C55E)
                                isReplMode -> Color(0xFF38BDF8)
                                else -> Color(0xFF64748B)
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Mode Switcher Pills ([脚本输出] vs [REPL >>>])
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF0F172A))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!isReplMode) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { onToggleReplMode(false) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (snapshot.isAwaitingInput) "等待 input()" else "脚本终端",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isReplMode) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { onToggleReplMode(true) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REPL >>>",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isReplMode && !snapshot.isRunning) {
                    IconButton(onClick = onResetRepl, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "重置 REPL",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (snapshot.isRunning) {
                    IconButton(onClick = onStopScript, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "中断程序",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onRerunScript, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "重新运行脚本",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleFullScreen(!isFullScreen) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "切换为底部分屏" else "切换为全屏终端",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onClearTerminal, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "清屏",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onCloseSheet, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "收起终端",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155))

            // 2. Termux-style Direct Inline Terminal Canvas
            // Tapping anywhere in the terminal focuses the inline cursor and brings up the keyboard!
            val lastLineIncomplete = snapshot.lines.lastOrNull()?.isComplete == false

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        if (!snapshot.isRunning && !isReplMode) {
                            // Tapping an idle script terminal automatically activates interactive REPL >>> like Termux!
                            onToggleReplMode(true)
                        }
                        runCatching {
                            inputFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val displayLines = if (lastLineIncomplete && isInteractiveLineVisible) {
                        snapshot.lines.dropLast(1)
                    } else {
                        snapshot.lines
                    }

                    itemsIndexed(displayLines, key = { _, item -> item.id }) { _, line ->
                        TerminalLineRow(
                            line = line,
                            fontSizeSp = fontSizeSp,
                            onJumpToLine = onJumpToLine
                        )
                    }

                    // Active inline input row (merges trailing incomplete prompt like `input("Name: ")` or shows `>>> `)
                    item(key = "active_inline_prompt") {
                        val inlinePromptPrefix = when {
                            lastLineIncomplete && isInteractiveLineVisible -> {
                                snapshot.lines.last().plainText
                            }
                            isReplMode && !snapshot.isRunning -> {
                                if (replContinuation) "... " else ">>> "
                            }
                            snapshot.isAwaitingInput -> ""
                            else -> ""
                        }

                        if (isInteractiveLineVisible) {
                            InlineTermuxPromptRow(
                                promptPrefix = inlinePromptPrefix,
                                isReplPrompt = isReplMode && !snapshot.isRunning,
                                tfv = tfv,
                                fontSizeSp = fontSizeSp,
                                focusRequester = inputFocusRequester,
                                onValueChange = { newValue ->
                                    // If the soft keyboard inserted a newline ('\n'), immediately execute line(s) like Termux!
                                    if (newValue.text.contains('\n')) {
                                        val parts = newValue.text.split('\n')
                                        for (i in 0 until parts.size - 1) {
                                            commitLine(parts[i])
                                        }
                                        val remainder = parts.last()
                                        tfv = TextFieldValue(remainder, TextRange(remainder.length))
                                    } else {
                                        tfv = newValue
                                    }
                                },
                                onSubmitCurrent = {
                                    val lineToSubmit = tfv.text
                                    tfv = TextFieldValue("")
                                    commitLine(lineToSubmit)
                                }
                            )
                        } else {
                            Text(
                                text = "轻触终端屏幕直接进入 >>> 交互命令模式",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSizeSp - 1f).sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Termux-style Extra Keys Bar (ESC, TAB, CTRL+C, CTRL+L, ↑, ↓, ←, →, HOME, END, ↵)
            TermuxExtraKeysBar(
                onEsc = {
                    tfv = TextFieldValue("")
                },
                onTab = {
                    val cur = tfv.text
                    val sel = tfv.selection.start.coerceIn(0, cur.length)
                    val updated = cur.substring(0, sel) + "    " + cur.substring(sel)
                    tfv = TextFieldValue(updated, TextRange(sel + 4))
                },
                onCtrlC = {
                    if (snapshot.isRunning) {
                        onStopScript()
                    } else {
                        tfv = TextFieldValue("")
                        if (isReplMode) {
                            onResetRepl()
                        }
                    }
                },
                onCtrlL = onClearTerminal,
                onHistoryUp = {
                    if (commandHistory.isNotEmpty()) {
                        val nextIdx = (if (historyIndex <= 0) 0 else historyIndex - 1)
                            .coerceIn(0, commandHistory.size - 1)
                        historyIndex = nextIdx
                        val cmd = commandHistory[nextIdx]
                        tfv = TextFieldValue(cmd, TextRange(cmd.length))
                    }
                },
                onHistoryDown = {
                    if (commandHistory.isNotEmpty()) {
                        if (historyIndex < commandHistory.size - 1) {
                            historyIndex += 1
                            val cmd = commandHistory[historyIndex]
                            tfv = TextFieldValue(cmd, TextRange(cmd.length))
                        } else {
                            historyIndex = commandHistory.size
                            tfv = TextFieldValue("")
                        }
                    }
                },
                onCursorLeft = {
                    val pos = (tfv.selection.start - 1).coerceAtLeast(0)
                    tfv = tfv.copy(selection = TextRange(pos))
                },
                onCursorRight = {
                    val pos = (tfv.selection.start + 1).coerceAtMost(tfv.text.length)
                    tfv = tfv.copy(selection = TextRange(pos))
                },
                onHome = {
                    tfv = tfv.copy(selection = TextRange(0))
                },
                onEnd = {
                    tfv = tfv.copy(selection = TextRange(tfv.text.length))
                },
                onInsertSymbol = { sym ->
                    val cur = tfv.text
                    val sel = tfv.selection.start.coerceIn(0, cur.length)
                    val updated = cur.substring(0, sel) + sym + cur.substring(sel)
                    tfv = TextFieldValue(updated, TextRange(sel + sym.length))
                },
                onEnter = {
                    if (!snapshot.isRunning && !isReplMode) {
                        onToggleReplMode(true)
                    } else {
                        val lineToSubmit = tfv.text
                        tfv = TextFieldValue("")
                        commitLine(lineToSubmit)
                    }
                }
            )
        }
    }
}

@Composable
private fun InlineTermuxPromptRow(
    promptPrefix: String,
    isReplPrompt: Boolean,
    tfv: TextFieldValue,
    fontSizeSp: Float,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmitCurrent: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (promptPrefix.isNotEmpty()) {
            Text(
                text = promptPrefix,
                color = if (isReplPrompt) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isReplPrompt) FontWeight.Bold else FontWeight.Normal,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.35f).sp
            )
        }

        BasicTextField(
            value = tfv,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color(0xFF4ADE80),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.35f).sp
            ),
            cursorBrush = SolidColor(Color(0xFF4ADE80)),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { onSubmitCurrent() },
                onDone = { onSubmitCurrent() },
                onGo = { onSubmitCurrent() }
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                         event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                    ) {
                        onSubmitCurrent()
                        true
                    } else {
                        false
                    }
                }
        )
    }
}

@Composable
private fun TermuxExtraKeysBar(
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onCtrlC: () -> Unit,
    onCtrlL: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onCursorLeft: () -> Unit,
    onCursorRight: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onInsertSymbol: (String) -> Unit,
    onEnter: () -> Unit
) {
    val keys = listOf<Pair<String, () -> Unit>>(
        "ESC" to onEsc,
        "TAB" to onTab,
        "CTRL+C" to onCtrlC,
        "CTRL+L" to onCtrlL,
        "↑" to onHistoryUp,
        "↓" to onHistoryDown,
        "←" to onCursorLeft,
        "→" to onCursorRight,
        "HOME" to onHome,
        "END" to onEnd,
        "(" to { onInsertSymbol("(") },
        ")" to { onInsertSymbol(")") },
        ":" to { onInsertSymbol(":") },
        "\"" to { onInsertSymbol("\"") },
        "=" to { onInsertSymbol("=") },
        "↵" to onEnter
    )

    Surface(
        color = Color(0xFF151F32),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.forEach { (label, action) ->
                val isAccent = label == "CTRL+C" || label == "↵"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (label) {
                                "CTRL+C" -> Color(0xFF7F1D1D)
                                "↵" -> Color(0xFF0284C7)
                                else -> Color(0xFF26354D)
                            }
                        )
                        .clickable { action() }
                        .padding(horizontal = 9.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isAccent) Color.White else Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(
    line: TerminalLine,
    fontSizeSp: Float,
    onJumpToLine: (Int) -> Unit
) {
    val plain = line.plainText
    val lineNumMatch = remember(plain) {
        if (plain.contains("File ") || plain.contains("line ", ignoreCase = true)) {
            TRACEBACK_LINE_REGEX.find(plain)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } else {
            null
        }
    }

    val annotated = buildAnnotatedString {
        for (seg in line.segments) {
            withStyle(
                style = SpanStyle(
                    color = ansiIndexToColor(seg.fgColorIndex, seg.isError),
                    fontWeight = if (seg.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp
                )
            ) {
                append(seg.text)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = annotated,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.35f).sp,
            modifier = Modifier.weight(1f)
        )

        if (lineNumMatch != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF7F1D1D))
                    .clickable { onJumpToLine(lineNumMatch) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "跳转 Ln $lineNumMatch ↗",
                    color = Color(0xFFFECACA),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun ansiIndexToColor(index: Int, isError: Boolean): Color {
    if (isError && index == TerminalSegment.DEFAULT_FG) {
        return Color(0xFFF87171)
    }
    return when (index) {
        30, 90 -> Color(0xFF94A3B8)
        31, 91 -> Color(0xFFF87171) // Red
        32, 92 -> Color(0xFF4ADE80) // Green
        33, 93 -> Color(0xFFFACC15) // Yellow
        34, 94 -> Color(0xFF60A5FA) // Blue
        35, 95 -> Color(0xFFC084FC) // Magenta
        36, 96 -> Color(0xFF38BDF8) // Cyan
        37, 97 -> Color(0xFFF8FAFC) // White
        else -> Color(0xFFE2E8F0)
    }
}
