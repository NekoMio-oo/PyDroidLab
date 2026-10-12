package com.pydroidlab.app.ui.packages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.python.PipMirror
import com.pydroidlab.app.python.SCI_PACKAGES
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⑤ 包管理
 *
 * pip 安装 / 卸载、镜像源（PyPI ↔ 清华 TUNA）切换、requirements 导入导出、
 * 本地 .whl 安装，以及科学计算栈的一键安装。
 *
 * pip 的原始输出会通过 pdl_host 桥流到「控制台」，方便排查安装失败原因。
 */
@Composable
fun PackagesScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm = vm.services.packageManager

    var spec by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch { installed = pm.listInstalled() }
    }

    LaunchedEffect(Unit) { refresh() }

    // SAF：导入 requirements.txt / 安装本地 whl
    val requirementsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val f = materialize(context, uri, vm, "requirements.txt")
                val code = if (f != null) pm.installRequirements(f.absolutePath) else -1
                lastResult = "requirements 安装退出码：$code"
                busy = false
                vm.postNotice("pip 输出见「控制台」")
            }
        }
    }

    val wheelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val f = materialize(context, uri, vm, "package.whl")
                val code = if (f != null) pm.installWheel(f.absolutePath) else -1
                lastResult = "whl 安装退出码：$code"
                busy = false
                vm.postNotice("pip 输出见「控制台」")
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // ---- 镜像源 ----
                SectionCard(title = "镜像源") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PipMirror.values().forEach { m ->
                            val selected = vm.settings.pipMirror == m.id
                            Surface(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = if (selected) 2.dp else 0.dp
                            ) {
                                TextButton(onClick = { vm.updateMirror(m.id) }) {
                                    Text(
                                        text = m.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "当前：${PipMirror.values().firstOrNull { it.id == vm.settings.pipMirror }?.label ?: "PyPI"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ---- 安装 / 卸载 ----
                SectionCard(title = "安装 / 卸载") {
                    OutlinedTextField(
                        value = spec,
                        onValueChange = { spec = it },
                        label = { Text("包名，可带版本：requests==2.31.0") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = spec.isNotBlank() && !busy,
                            onClick = {
                                val s = spec.trim()
                                busy = true
                                scope.launch {
                                    val code = pm.install(s)
                                    lastResult = "install $s → 退出码 $code"
                                    busy = false
                                    refresh()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("安装")
                        }
                        TextButton(
                            enabled = spec.isNotBlank() && !busy,
                            onClick = {
                                val s = spec.trim()
                                busy = true
                                scope.launch {
                                    val code = pm.upgrade(s)
                                    lastResult = "upgrade $s → 退出码 $code"
                                    busy = false
                                    refresh()
                                }
                            }
                        ) { Text("升级") }
                        TextButton(
                            enabled = spec.isNotBlank() && !busy,
                            onClick = {
                                val s = spec.trim()
                                busy = true
                                scope.launch {
                                    val code = pm.uninstall(s)
                                    lastResult = "uninstall $s → 退出码 $code"
                                    busy = false
                                    refresh()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("卸载")
                        }
                    }
                }

                // ---- 科学栈 ----
                SectionCard(title = "科学计算栈（一键安装）") {
                    Text(
                        text = SCI_PACKAGES.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val results = mutableListOf<String>()
                                pm.installScientific { pkg, code -> results.add("$pkg:$code") }
                                lastResult = results.joinToString("  ")
                                busy = false
                                refresh()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装全部（约 200MB，建议 WiFi）")
                    }
                }

                // ---- requirements / whl ----
                SectionCard(title = "requirements 与本地 whl") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !busy,
                            onClick = { requirementsPicker.launch(arrayOf("text/*", "*/*")) }
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入 requirements")
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = { wheelPicker.launch(arrayOf("*/*")) }
                        ) { Text("安装 .whl") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    val list = pm.freeze()
                                    val dir = vm.currentDir
                                        ?: vm.currentProject?.dir
                                        ?: vm.services.appContext.filesDir
                                    val f = File(dir, "requirements.txt")
                                    withContext(Dispatchers.IO) { f.writeText(list.joinToString("\n") + "\n") }
                                    lastResult = "已导出 ${list.size} 个包到 ${f.absolutePath}"
                                    busy = false
                                }
                            }
                        ) { Text("导出 requirements.txt") }
                        TextButton(onClick = { refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("刷新列表")
                        }
                    }
                    lastResult?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "pip 的完整输出请在「控制台」查看。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ---- 已安装 ----
                SectionCard(title = "已安装包（${installed.size}）") {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("过滤") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(installed.filter { filter.isBlank() || it.contains(filter, ignoreCase = true) }) { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val pkg = name.substringBefore("==")
                        busy = true
                        scope.launch {
                            pm.uninstall(pkg)
                            busy = false
                            refresh()
                        }
                    }
                ) { Text("卸载") }
            }
            HorizontalDivider()
        }
    }
}

private suspend fun materialize(
    context: android.content.Context,
    uri: Uri,
    vm: AppViewModel,
    defaultName: String
): File? = withContext(Dispatchers.IO) {
    try {
        val dir = vm.currentDir ?: vm.currentProject?.dir ?: vm.services.appContext.filesDir
        val name = queryName(context, uri) ?: defaultName
        val target = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        target
    } catch (t: Throwable) {
        null
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && idx >= 0) return it.getString(idx)
    }
    return null
}
