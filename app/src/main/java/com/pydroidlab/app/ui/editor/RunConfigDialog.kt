package com.pydroidlab.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.python.RunOptions

/**
 * 「运行配置」对话框：命令行参数 / 工作目录 / 环境变量 / stdin 预设。
 *
 * 这些字段最终会被 [RunOptions.toRunConfig] 转成 `RunConfig`，
 * 交给 `pdl_runner.run_script(path, argv, cwd, env, stdin_preset)`。
 */
@Composable
fun RunConfigDialog(
    initial: RunOptions,
    onDismiss: () -> Unit,
    onSave: (RunOptions) -> Unit,
    onSaveAndRun: (RunOptions) -> Unit
) {
    var argv by remember { mutableStateOf(initial.argv) }
    var cwd by remember { mutableStateOf(initial.cwd) }
    var env by remember { mutableStateOf(initial.env) }
    var stdin by remember { mutableStateOf(initial.stdinPreset) }

    fun current() = initial.copy(
        argv = argv,
        cwd = cwd,
        env = env,
        stdinPreset = stdin
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = initial.filePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = argv,
                    onValueChange = { argv = it },
                    label = { Text("命令行参数（空格分隔，支持引号）") },
                    placeholder = { Text("--verbose 输入文件.txt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    label = { Text("工作目录（留空 = 脚本所在目录）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = env,
                    onValueChange = { env = it },
                    label = { Text("环境变量（每行 KEY=VALUE）") },
                    placeholder = { Text("LANG=zh_CN.UTF-8\nDEBUG=1") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                )

                OutlinedTextField(
                    value = stdin,
                    onValueChange = { stdin = it },
                    label = { Text("预置 stdin（会先于交互输入喂给 input()）") },
                    placeholder = { Text("第一行输入\n第二行输入") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                )

                val parsed = current()
                Text(
                    text = "argv: ${parsed.argvAsList()}\n" +
                        "env : ${parsed.envAsMap().size} 项\n" +
                        "stdin: ${stdin.length} 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave(current()) }) { Text("保存") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onSaveAndRun(current()) }) { Text("保存并运行") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
