package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    isReplMode: Boolean,
    replContinuation: Boolean,
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
    var inputBuffer by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }

    // Auto-scroll to bottom when new stdout/stderr lines arrive
    LaunchedEffect(snapshot.lines.size, snapshot.lines.lastOrNull()?.plainText) {
        if (snapshot.lines.isNotEmpty()) {
            listState.animateScrollToItem(snapshot.lines.size - 1)
        }
    }

    // Automatically focus the stdin input bar when Python calls `input()` or REPL mode is enabled
    LaunchedEffect(snapshot.isAwaitingInput, isReplMode) {
        if (snapshot.isAwaitingInput || isReplMode) {
            runCatching { inputFocusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0B1120),
        contentColor = Color(0xFFE2E8F0),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Terminal Header Bar with Mode Switcher ([脚本输出] vs [REPL >>>])
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
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

                // Mode Switcher Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!isReplMode) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { onToggleReplMode(false) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
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
                            .padding(horizontal = 8.dp, vertical = 3.dp)
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
                    IconButton(onClick = onResetRepl, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "重置 REPL 上下文",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (snapshot.isRunning) {
                    IconButton(onClick = onStopScript, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "中断程序",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onRerunScript, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "重新运行脚本",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(onClick = onClearTerminal, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "清屏",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onCloseSheet, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "最小化终端",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155))

            // 2. Scrollback Output Area (with Clickable Traceback `line X` Jump Chips!)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (snapshot.lines.isEmpty()) {
                    item {
                        Text(
                            text = if (isReplMode) {
                                "🐍 Python 3 Interactive REPL 已就绪，在下方输入任意 Python 表达式回车即可求值。"
                            } else {
                                "点击右上角 ▶ 运行当前脚本，或切换到「REPL >>>」直接交互试跑代码。"
                            },
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp
                        )
                    }
                } else {
                    items(snapshot.lines, key = { it.id }) { line ->
                        TerminalLineRow(
                            line = line,
                            fontSizeSp = fontSizeSp,
                            onJumpToLine = onJumpToLine
                        )
                    }
                }
            }

            // 3. Interactive stdin / input() / REPL bar
            val inputEnabled = snapshot.isRunning || isReplMode
            Surface(
                color = if (snapshot.isAwaitingInput || isReplMode) Color(0xFF1E293B) else Color(0xFF111827),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            snapshot.isAwaitingInput -> "stdin ❯"
                            isReplMode -> if (replContinuation) "... " else ">>> "
                            else -> "❯"
                        },
                        color = when {
                            snapshot.isAwaitingInput -> Color(0xFFFACC15)
                            isReplMode -> Color(0xFF38BDF8)
                            else -> Color(0xFF64748B)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = inputBuffer,
                        onValueChange = { inputBuffer = it },
                        enabled = inputEnabled,
                        placeholder = {
                            Text(
                                text = when {
                                    snapshot.isAwaitingInput -> "程序正在等待 input() 输入，输入后按回车发送..."
                                    isReplMode -> "输入 Python 表达式或语句 (如 1 + 1 或 import math)..."
                                    snapshot.isRunning -> "预输入一行到标准输入缓冲区 (stdin)..."
                                    else -> "点击上方「REPL >>>」可直接交互输入命令"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF8FAFC)
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (snapshot.isAwaitingInput) Color(0xFFFACC15) else Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            disabledBorderColor = Color(0xFF1E293B)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputEnabled) {
                                    onSubmitInput(inputBuffer)
                                    inputBuffer = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(inputFocusRequester)
                    )

                    Button(
                        onClick = {
                            onSubmitInput(inputBuffer)
                            inputBuffer = ""
                        },
                        enabled = inputEnabled,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (snapshot.isAwaitingInput) Color(0xFFFACC15) else Color(0xFF0284C7),
                            contentColor = if (snapshot.isAwaitingInput) Color(0xFF0F172A) else Color.White
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isReplMode && !snapshot.isRunning) "求值" else "发送", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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
