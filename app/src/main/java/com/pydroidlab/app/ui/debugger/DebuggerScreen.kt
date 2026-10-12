package com.pydroidlab.app.ui.debugger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.python.DebugState
import com.pydroidlab.app.python.StackFrame
import com.pydroidlab.app.python.Variable
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.SectionCard
import com.pydroidlab.app.ui.theme.MonoFontFamily
import java.io.File

/**
 * ⑦ 调试器
 *
 * 基于 `bdb` 的 `pdl_debugger`：设置断点后启动会话，程序会在断点处暂停，
 * UI 可以查看调用栈 / 局部变量，并执行 继续 / 单步 / 下一行 / 跳出 / 停止。
 */
@Composable
fun DebuggerScreen(vm: AppViewModel) {
    val debugger = vm.services.debugger
    val state by debugger.state.collectAsState()
    val breakpoints by debugger.breakpoints.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var showAddBreakpoint by remember { mutableStateOf(false) }
    var watch by remember { mutableStateOf<Set<String>>(emptySet()) }
    var watchInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !debugger.running,
                        onClick = { vm.startDebug() }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("开始调试")
                    }
                    TextButton(
                        enabled = debugger.running && state.paused,
                        onClick = { debugger.resume() }
                    ) { Text("继续") }
                    TextButton(
                        enabled = debugger.running && state.paused,
                        onClick = { debugger.step() }
                    ) { Text("单步") }
                    TextButton(
                        enabled = debugger.running && state.paused,
                        onClick = { debugger.next() }
                    ) { Text("下一行") }
                    TextButton(
                        enabled = debugger.running && state.paused,
                        onClick = { debugger.stepOut() }
                    ) { Text("跳出") }
                    TextButton(
                        enabled = debugger.running,
                        onClick = { debugger.stop() }
                    ) { Text("停止") }
                    TextButton(onClick = { showAddBreakpoint = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("断点")
                    }
                }
                StatusLine(debugger.running, state)
            }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("变量") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("调用栈") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("断点") })
        }

        when (tab) {
            0 -> VariablesTab(
                state = state,
                watch = watch,
                watchInput = watchInput,
                onWatchInput = { watchInput = it },
                onAddWatch = {
                    val w = watchInput.trim()
                    if (w.isNotEmpty()) {
                        watch = watch + w
                        watchInput = ""
                    }
                },
                onRemoveWatch = { watch = watch - it }
            )

            1 -> StackTab(state)
            else -> BreakpointsTab(
                breakpoints = breakpoints,
                onRemove = { file, line -> debugger.toggleBreakpoint(file, line) },
                onClear = { breakpoints.forEach { (f, l) -> debugger.toggleBreakpoint(f, l) } }
            )
        }
    }

    if (showAddBreakpoint) {
        AddBreakpointDialog(
            initialFile = vm.activeFile?.absolutePath ?: "",
            onDismiss = { showAddBreakpoint = false },
            onConfirm = { file, line ->
                debugger.toggleBreakpoint(file, line)
                showAddBreakpoint = false
            }
        )
    }
}

@Composable
private fun StatusLine(running: Boolean, state: DebugState) {
    val text = when {
        !running && state.finished -> "未在调试"
        state.paused -> "已暂停于 ${File(state.file).name}:${state.line}"
        else -> "运行中…"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (state.paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (state.paused) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun VariablesTab(
    state: DebugState,
    watch: Set<String>,
    watchInput: String,
    onWatchInput: (String) -> Unit,
    onAddWatch: () -> Unit,
    onRemoveWatch: (String) -> Unit
) {
    // 过滤掉 __builtins__ 这类 dunder：它的 repr 有几千字符，会把整个面板冲掉
    val displayed = state.displayLocals()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.exception?.let {
            SectionCard(title = "异常") {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        SectionCard(title = "变量监视（${watch.size}）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = watchInput,
                    onValueChange = onWatchInput,
                    label = { Text("变量名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAddWatch) { Text("关注") }
            }
            if (watch.isEmpty()) {
                Text(
                    text = "添加变量名后，暂停时会持续显示它的值。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                watch.sorted().forEach { name ->
                    val v = displayed.firstOrNull { it.name == name }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRemoveWatch(name) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.width(110.dp)
                        )
                        Text(
                            text = v?.value ?: "（当前帧中不存在）",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = if (v == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        SectionCard(title = "当前帧局部变量（${displayed.size}）") {
            if (displayed.isEmpty()) {
                Text(
                    text = if (state.paused) "该帧没有局部变量" else "程序暂停后这里会列出所有局部变量",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayed.forEach { v ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = v.name,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.width(110.dp)
                        )
                        Text(
                            text = v.value,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StackTab(state: DebugState) {
    val frames = state.displayStack()
    if (frames.isEmpty()) {
        Text(
            text = "程序暂停后会显示调用栈（只列出你自己的代码帧，已隐藏解释器与线程内部帧）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(frames.reversed()) { frame ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = frame.function,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.width(120.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = File(frame.file).name,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = frame.file,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "行 ${frame.line}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
        }
    }
}

// -------------------------------------------------------------- 展示层过滤 --

private const val MAX_VALUE_CHARS = 160

/**
 * 变量列表的展示版本。
 *
 * 调试会话里 `f_locals` 会带上 `__builtins__`（repr 有数千字符）以及
 * `__name__` / `__file__` / `__loader__` 等内部名字，直接铺开会把面板冲掉，
 * 所以这里过滤 dunder 并把超长值截断。
 */
private fun DebugState.displayLocals(): List<Variable> =
    locals.filter { !it.name.startsWith("__") }
        .map { it.copy(value = it.value.abbreviate(MAX_VALUE_CHARS)) }
        .sortedBy { it.name }

/**
 * 调用栈的展示版本：只保留用户代码帧。
 *
 * `pdl_debugger` 是从一个 Python 线程里启动的，原始栈里会包含
 * `threading._bootstrap*`、`<frozen importlib._bootstrap>`、`bdb.py`、
 * `pdl_debugger.py` 等运行库帧；对定位用户代码没有帮助，反而淹没重点。
 */
private fun DebugState.displayStack(): List<StackFrame> =
    stack.filter { frame ->
        val f = frame.file
        f.isNotEmpty() &&
            !f.startsWith("<") &&
            !f.contains("/lib/python3") &&
            !f.contains("/site-packages/") &&
            !f.endsWith("/threading.py") &&
            !f.endsWith("/bdb.py") &&
            !f.endsWith("/pdl_debugger.py") &&
            !f.endsWith("/pdl_runtime.py")
    }

private fun String.abbreviate(max: Int): String =
    if (length <= max) this else substring(0, max) + " …(共 $length 字符)"

@Composable
private fun BreakpointsTab(
    breakpoints: Set<Pair<String, Int>>,
    onRemove: (String, Int) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共 ${breakpoints.size} 个断点",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(enabled = breakpoints.isNotEmpty(), onClick = onClear) { Text("全部清除") }
        }
        HorizontalDivider()
        if (breakpoints.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "还没有断点。可以在这里添加，或在编辑器里把光标放到目标行后点「断点」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(breakpoints.toList()) { (file, line) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${File(file).name}:$line",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                            )
                            Text(
                                text = file,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRemove(file, line) }) { Text("移除") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AddBreakpointDialog(
    initialFile: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var file by remember { mutableStateOf(initialFile) }
    var line by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加断点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = file,
                    onValueChange = { file = it },
                    label = { Text("文件绝对路径") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it.filter { c -> c.isDigit() } },
                    label = { Text("行号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = file.isNotBlank() && (line.toIntOrNull() ?: 0) > 0,
                onClick = { onConfirm(file.trim(), line.toInt()) }
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
