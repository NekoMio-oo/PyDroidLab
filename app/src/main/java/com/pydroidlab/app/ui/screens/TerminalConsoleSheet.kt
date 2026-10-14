package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.background
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

@Composable
fun TerminalConsoleSheet(
    snapshot: TerminalSnapshot,
    fontSizeSp: Float,
    onSubmitInput: (String) -> Unit,
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

    // Automatically focus the stdin input bar when Python calls `input()`
    LaunchedEffect(snapshot.isAwaitingInput) {
        if (snapshot.isAwaitingInput) {
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
            // 1. Terminal Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when {
                                snapshot.isAwaitingInput -> Color(0xFFFACC15) // Yellow: awaiting input()
                                snapshot.isRunning -> Color(0xFF22C55E)       // Green: running
                                else -> Color(0xFF64748B)                     // Grey: idle
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        snapshot.isAwaitingInput -> "终端控制台 • 等待 input() 输入..."
                        snapshot.isRunning -> "终端控制台 • 运行中..."
                        else -> "终端控制台 (Terminal Emulator)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (snapshot.isRunning) {
                    IconButton(onClick = onStopScript) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "中断程序 (KeyboardInterrupt)",
                            tint = Color(0xFFEF4444)
                        )
                    }
                } else {
                    IconButton(onClick = onRerunScript) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "重新运行",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                }

                IconButton(onClick = onClearTerminal) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "清屏",
                        tint = Color(0xFF94A3B8)
                    )
                }

                IconButton(onClick = onCloseSheet) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "最小化终端",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155))

            // 2. Scrollback Output Area (Renders ANSI SGR colors & stdout/stderr)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (snapshot.lines.isEmpty()) {
                    item {
                        Text(
                            text = "点击右上角 ▶ 运行按钮执行当前 Python 脚本，stdout/stderr 将实时显示在此处。",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp
                        )
                    }
                } else {
                    items(snapshot.lines, key = { it.id }) { line ->
                        TerminalLineRow(line = line, fontSizeSp = fontSizeSp)
                    }
                }
            }

            // 3. Interactive stdin / input() bar
            Surface(
                color = if (snapshot.isAwaitingInput) Color(0xFF1E293B) else Color(0xFF111827),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (snapshot.isAwaitingInput) "stdin ❯" else "❯",
                        color = if (snapshot.isAwaitingInput) Color(0xFFFACC15) else Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = inputBuffer,
                        onValueChange = { inputBuffer = it },
                        enabled = snapshot.isRunning,
                        placeholder = {
                            Text(
                                text = if (snapshot.isAwaitingInput) {
                                    "程序正在等待 input() 输入，输入后按回车发送..."
                                } else if (snapshot.isRunning) {
                                    "预输入一行到标准输入缓冲区 (stdin)..."
                                } else {
                                    "程序未运行 (点击 ▶ 运行脚本后可在此处交互输入)"
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
                                if (snapshot.isRunning) {
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
                        enabled = snapshot.isRunning,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (snapshot.isAwaitingInput) Color(0xFFFACC15) else Color(0xFF0284C7),
                            contentColor = if (snapshot.isAwaitingInput) Color(0xFF0F172A) else Color.White
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送输入",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("发送", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(
    line: TerminalLine,
    fontSizeSp: Float
) {
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
    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.35f).sp,
        modifier = Modifier.fillMaxWidth()
    )
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
