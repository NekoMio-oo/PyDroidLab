package com.pydroidlab.app.ui.repl

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

/**
 * ⑧ REPL
 *
 * 交互式 Python 控制台，globals 在多次求值之间保持。
 * 支持多行块（`def` / `for` … 未闭合时会继续等待输入）与 Tab 补全，
 * 输出经 pdl_host 桥返回，与「控制台」共用同一条事件流。
 */
@Composable
fun ReplScreen(vm: AppViewModel) {
    val repl = vm.services.repl
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val entries = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }
    var continuation by remember { mutableStateOf(false) }
    var completions by remember { mutableStateOf<List<String>>(emptyList()) }
    var seen by remember { mutableIntStateOf(vm.lines.size) }

    // 把 REPL 会话期间新产生的输出并入本页记录
    val lineCount = vm.lines.size
    LaunchedEffect(lineCount) {
        if (lineCount > seen) {
            for (i in seen until lineCount) entries.add(vm.lines[i].text)
            seen = lineCount
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1)
    }

    fun submit() {
        val text = input
        val prompt = if (continuation) "... " else ">>> "
        entries.add(prompt + text)
        input = ""
        completions = emptyList()
        scope.launch {
            val more = repl.push(text)
            // push 会同步写输出，这里刷新一次 seen 让输出即时并入
            for (i in seen until vm.lines.size) entries.add(vm.lines[i].text)
            seen = vm.lines.size
            continuation = more
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
                TextButton(onClick = {
                    repl.reset()
                    entries.clear()
                    continuation = false
                    seen = vm.lines.size
                    vm.postNotice("REPL 已重置（globals 已清空）")
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重置")
                }
                TextButton(onClick = { entries.clear(); seen = vm.lines.size }) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清屏")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (continuation) "块未结束，继续输入" else "就绪",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = ">>> 试试输入 2**10、import math 再 math.pi，或写一个多行函数。\n" +
                    "长按下方输入框右侧的「补全」可查看候选。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(entries) { _, entry ->
                    val isPrompt = entry.startsWith(">>> ") || entry.startsWith("... ")
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = if (isPrompt) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 1.dp)
                    )
                }
            }
        }

        if (completions.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                LazyColumn(modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)) {
                    itemsIndexed(completions) { _, c ->
                        Text(
                            text = c,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (continuation) "... 继续输入块" else ">>> 输入 Python") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    val prefix = input.trim()
                    if (prefix.isEmpty()) {
                        vm.postNotice("先输入几个字符再补全")
                    } else {
                        scope.launch {
                            completions = repl.complete(prefix)
                            if (completions.isEmpty()) vm.postNotice("没有候选项")
                        }
                    }
                }) { Text("补全") }
                TextButton(onClick = { submit() }, enabled = input.isNotEmpty()) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("执行")
                }
            }
        }
    }
}
