package com.pydroidlab.app.ui.console

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.ConsoleKind
import com.pydroidlab.app.ui.shareFile
import com.pydroidlab.app.ui.shareText
import com.pydroidlab.app.ui.theme.MonoFontFamily

/**
 * ② 运行控制台
 *
 * stdout / stderr 流式渲染；`input()` 交互输入；中断、清屏、复制、分享。
 */
@Composable
fun ConsoleScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    val lines = vm.lines
    val tail = vm.pendingLine

    // 有新输出时自动滚到底部
    LaunchedEffect(lines.size, tail) {
        val total = lines.size + if (tail.isNotEmpty()) 1 else 0
        if (total > 0) {
            scrollState.scrollToItem(total - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(onClick = { vm.interrupt() }, enabled = vm.isRunning) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("中断")
                }
                TextButton(onClick = { vm.clearConsole() }) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清屏")
                }
                TextButton(onClick = {
                    val text = vm.consoleText()
                    if (text.isNotBlank()) {
                        clipboard.setText(AnnotatedString(text))
                        vm.postNotice("已复制到剪贴板")
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制")
                }
                TextButton(onClick = {
                    val text = vm.consoleText()
                    if (text.isNotBlank()) shareText(context, "PyDroidLab 输出", text)
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("分享")
                }
                TextButton(onClick = {
                    val saved = vm.saveConsoleToFile()
                    if (saved != null) shareFile(context, saved, "text/plain", saved.name)
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("存为文件")
                }
            }
        }

        if (vm.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (lines.isEmpty() && tail.isEmpty()) {
            Text(
                text = "这里会显示脚本的 print() / 异常输出。\n在「编辑器」中点击「运行」即可开始。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                items(lines) { line ->
                    Text(
                        text = line.text.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        // Bug 4：按行的种类分色 —— 运行横幅 primary、
                        // stderr error、结束行弱化、stdout 常规。
                        color = when (line.kind) {
                            ConsoleKind.HEADER -> MaterialTheme.colorScheme.primary
                            ConsoleKind.FOOTER -> MaterialTheme.colorScheme.onSurfaceVariant
                            ConsoleKind.STDERR -> MaterialTheme.colorScheme.error
                            ConsoleKind.STDOUT -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 1.dp)
                    )
                }
                if (tail.isNotEmpty()) {
                    item {
                        Text(
                            text = tail,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(if (vm.isRunning) "输入并回车 → input()" else "程序未运行") },
                    singleLine = true,
                    enabled = vm.isRunning,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (draft.isNotEmpty() || vm.isRunning) {
                            vm.submitInput(draft)
                            draft = ""
                        }
                    },
                    enabled = vm.isRunning
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("发送")
                }
            }
        }
    }
}
