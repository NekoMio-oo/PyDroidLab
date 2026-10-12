package com.pydroidlab.app.ui.terminal

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import jackpal.androidterm.emulatorview.ColorScheme
import jackpal.androidterm.emulatorview.EmulatorView
import com.pydroidlab.app.shell.PyShell
import com.pydroidlab.app.terminal.TerminalController
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.settings.ThemeMode
import kotlin.math.roundToInt

/** 一个终端会话：控制器 + 显示用的序号。 */
private class TermSessionEntry(val id: Int, val controller: TerminalController)

/**
 * ③ 终端界面
 *
 * 使用 jackpal 的 [EmulatorView]（VT100 模拟器）+ [TerminalController]。
 * 设备上没有可用的本地 pty，所以跑的是内置命令集 + Python 执行器
 * （详见 `shell/PyShell.kt` 的文档）。
 *
 * 多会话：每个标签一个独立的 `TerminalController`（各自的命令历史、
 * 当前目录、滚动缓冲）。所有会话都保持组合状态，只是用 alpha/zIndex
 * 控制可见与可交互 —— 这样切标签**不会**丢掉另一个会话的输出，
 * 也避免了销毁再重建 EmulatorView 时重新挂接 TermSession 的时序问题。
 */
@Composable
fun TerminalScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val shell = remember {
        PyShell(
            cwdProvider = { vm.currentDir ?: vm.currentProject?.dir },
            cwdSetter = { vm.enterDir(it) },
            openInEditor = { vm.openFile(it) },
            mirrorProvider = { vm.settings.pipMirror }
        )
    }

    val sessions = remember {
        mutableStateListOf(TermSessionEntry(1, TerminalController(shell, scope)))
    }
    var selected by remember { mutableStateOf(0) }
    var nextId by remember { mutableStateOf(2) }

    val safeIndex = selected.coerceIn(0, sessions.size - 1)
    val current = sessions[safeIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 会话标签 ----
        ScrollableTabRow(selectedTabIndex = safeIndex, edgePadding = 4.dp) {
            sessions.forEachIndexed { index, entry ->
                Tab(
                    selected = index == safeIndex,
                    onClick = { selected = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("会话 ${entry.id}", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = {
                                entry.controller.shutdown()
                                sessions.removeAt(index)
                                if (sessions.isEmpty()) {
                                    sessions.add(TermSessionEntry(nextId++, TerminalController(shell, scope)))
                                }
                                selected = (index - 1).coerceAtLeast(0)
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭会话",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                )
            }
        }

        // ---- 工具栏 ----
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(onClick = {
                    sessions.add(TermSessionEntry(nextId++, TerminalController(shell, scope)))
                    selected = sessions.size - 1
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新会话")
                }
                TextButton(onClick = { current.controller.session.emit(PyShell.CLEAR_SCREEN) }) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清屏")
                }
                TextButton(onClick = { copyTranscript(vm, clipboard, current.controller) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制")
                }
                TextButton(onClick = {
                    current.controller.shutdown()
                    val replacement = TermSessionEntry(nextId++, TerminalController(shell, scope))
                    sessions[safeIndex] = replacement
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重置本会话")
                }
            }
        }

        Text(
            text = "工作目录：${(vm.currentDir ?: vm.currentProject?.dir)?.absolutePath ?: "-"}" +
                "   ·   ${sessions.size} 个会话",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
        )

        // ---- 所有会话都保持组合；仅当前会话可见 / 可交互 ----
        Box(modifier = Modifier.weight(1f)) {
            sessions.forEachIndexed { index, entry ->
                key(entry.id) {
                    TerminalPane(
                        vm = vm,
                        controller = entry.controller,
                        visible = index == safeIndex
                    )
                }
            }
        }
    }

    // 离开终端页时统一收尾，避免线程泄漏
    DisposableEffect(Unit) {
        onDispose {
            sessions.forEach { it.controller.shutdown() }
        }
    }
}

@Composable
private fun TerminalPane(
    vm: AppViewModel,
    controller: TerminalController,
    visible: Boolean
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics

    val dark = when (vm.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val fore = if (dark) 0xFFE6E6E6.toInt() else 0xFF1A1B21.toInt()
    val back = if (dark) 0xFF0F1218.toInt() else 0xFFFDFBFF.toInt()

    var emulator by remember { mutableStateOf<EmulatorView?>(null) }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .alpha(if (visible) 1f else 0f),
        factory = { ctx ->
            EmulatorView(ctx, controller.session, metrics).apply {
                // jackpal 的 setTextSize(int) 参数单位是 **dp**，内部会自己乘
                // mDensity 换算成 px。这里直接传 sp 值，不能再乘 density，
                // 否则字体在高密度屏上会被放大好几倍（Bug 5 的根因）。
                setTextSize(vm.settings.editor.fontSizeSp.roundToInt())
                setColorScheme(ColorScheme(fore, back))
                setUseCookedIME(true)
                emulator = this
            }
        },
        update = { ev ->
            ev.setTextSize(vm.settings.editor.fontSizeSp.roundToInt())
            ev.setColorScheme(ColorScheme(fore, back))
        }
    )

    // 启动会话（幂等），并只让可见会话抢占焦点
    LaunchedEffect(Unit) {
        controller.start()
    }

    DisposableEffect(visible) {
        if (visible) {
            emulator?.onResume()
            emulator?.requestFocus()
        } else {
            emulator?.onPause()
        }
        onDispose {
            emulator?.onPause()
        }
    }
}

private fun copyTranscript(
    vm: AppViewModel,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    controller: TerminalController
) {
    val text = try {
        controller.session.transcriptText
    } catch (_: Throwable) {
        ""
    }
    if (text.isBlank()) {
        vm.postNotice("终端还没有内容")
    } else {
        clipboard.setText(AnnotatedString(text))
        vm.postNotice("已复制终端内容")
    }
}
