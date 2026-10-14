package com.pydroidlab.app.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    LaunchedEffect(snapshot.lines.size, snapshot.lines.lastOrNull()?.plainText, tfv.text, isReplMode) {
        val totalItems = snapshot.lines.size + 1
        if (totalItems > 0) {
            runCatching { listState.animateScrollToItem(totalItems - 1) }
        }
    }

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
        color = Color(0xFF0D1117),
        contentColor = Color(0xFFE6EDF3)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Minimal Terminal Header (38.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFullScreen) {
                    IconButton(onClick = onCloseSheet, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = Color(0xFFE6EDF3),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Pure text mode tabs: 输出 | 交互
                Text(
                    text = "输出",
                    fontSize = 13.sp,
                    fontWeight = if (!isReplMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isReplMode) Color(0xFF58A6FF) else Color(0xFF8B949E),
                    modifier = Modifier
                        .clickable { onToggleReplMode(false) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = "REPL",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isReplMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (isReplMode) Color(0xFF58A6FF) else Color(0xFF8B949E),
                    modifier = Modifier
                        .clickable { onToggleReplMode(true) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isReplMode && !snapshot.isRunning) {
                    TextButton(onClick = onResetRepl) {
                        Text("重置", fontSize = 12.sp, color = Color(0xFF8B949E))
                    }
                }

                TextButton(onClick = onClearTerminal) {
                    Text("清屏", fontSize = 12.sp, color = Color(0xFF8B949E))
                }

                if (snapshot.isRunning) {
                    IconButton(onClick = onStopScript, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Stop,
                            contentDescription = "停止",
                            tint = Color(0xFFF85149),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onRerunScript, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "运行",
                            tint = Color(0xFF58A6FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleFullScreen(!isFullScreen) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = "全屏切换",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (!isFullScreen) {
                    IconButton(onClick = onCloseSheet, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF30363D))

            // 2. Direct Inline Terminal Canvas
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
                            onToggleReplMode(true)
                        }
                        runCatching {
                            inputFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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

                    item(key = "active_inline_prompt") {
                        val inlinePromptPrefix = when {
                            lastLineIncomplete && isInteractiveLineVisible -> {
                                snapshot.lines.last().plainText
                            }
                            isReplMode && !snapshot.isRunning -> {
                                if (replContinuation) "... " else ">>> "
                            }
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
                                text = "点击屏幕进入 REPL 交互模式",
                                color = Color(0xFF484F58),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSizeSp - 1f).sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF30363D))

            // 3. Minimal Termux Extra Keys Row
            TermuxExtraKeysBar(
                onEsc = { tfv = TextFieldValue("") },
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
                        if (isReplMode) onResetRepl()
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
                onHome = { tfv = tfv.copy(selection = TextRange(0)) },
                onEnd = { tfv = tfv.copy(selection = TextRange(tfv.text.length)) },
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
                color = if (isReplPrompt) Color(0xFF58A6FF) else Color(0xFFE6EDF3),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isReplPrompt) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.35f).sp
            )
        }

        BasicTextField(
            value = tfv,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color(0xFFE6EDF3),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.35f).sp
            ),
            cursorBrush = SolidColor(Color(0xFF58A6FF)),
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
        "ENTER" to onEnter
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF161B22))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { (label, action) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(30.dp)
                    .clickable { action() }
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = label,
                    color = if (label == "CTRL+C") Color(0xFFF85149) else Color(0xFFC9D1D9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
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
                    fontWeight = if (seg.isBold) FontWeight.SemiBold else FontWeight.Normal,
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
            Text(
                text = "行 $lineNumMatch",
                color = Color(0xFFF85149),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { onJumpToLine(lineNumMatch) }
            )
        }
    }
}

private fun ansiIndexToColor(index: Int, isError: Boolean): Color {
    if (isError && index == TerminalSegment.DEFAULT_FG) {
        return Color(0xFFF85149)
    }
    return when (index) {
        30, 90 -> Color(0xFF8B949E)
        31, 91 -> Color(0xFFF85149)
        32, 92 -> Color(0xFF3FB950)
        33, 93 -> Color(0xFFD29922)
        34, 94 -> Color(0xFF58A6FF)
        35, 95 -> Color(0xFFBC8CFF)
        36, 96 -> Color(0xFF39C5CF)
        37, 97 -> Color(0xFFE6EDF3)
        else -> Color(0xFFE6EDF3)
    }
}
