package com.pydroidlab.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.BuildConfig
import com.pydroidlab.app.python.PipMirror
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.SectionCard
import kotlinx.coroutines.launch
import java.io.File

/**
 * ⑩ 设置页
 *
 * 主题 / 字体 / 行高 / Tab 宽度 / 符号栏自定义 / 编辑器行为 / 配色方案 /
 * 设置导入导出 / 项目备份 zip。
 */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings = vm.settings
    val editor = settings.editor
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var fontSize by remember(editor.fontSizeSp) { mutableStateOf(editor.fontSizeSp) }
    var lineHeight by remember(editor.lineHeightMultiplier) { mutableStateOf(editor.lineHeightMultiplier) }
    var newSymbol by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showProjectMenu by remember { mutableStateOf(false) }
    var backupInfo by remember { mutableStateOf<String?>(null) }

    fun commit(transform: (EditorSettings) -> EditorSettings) {
        vm.updateEditorSettings(transform(editor))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------------------------------------------------- 外观 --
        SectionCard(title = "外观") {
            Text("主题模式", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { mode ->
                    val selected = settings.themeMode == mode
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        TextButton(onClick = { vm.updateTheme(mode) }) {
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- 编辑器 --
        SectionCard(title = "编辑器") {
            SliderRow(
                label = "字号：%.0f sp".format(fontSize),
                value = fontSize,
                range = 10f..28f,
                steps = 17,
                onChange = { fontSize = it },
                onCommit = { commit { it.copy(fontSizeSp = fontSize) } }
            )
            SliderRow(
                label = "行高倍数：%.1f".format(lineHeight),
                value = lineHeight,
                range = 0.8f..2.0f,
                steps = 11,
                onChange = { lineHeight = it },
                onCommit = { commit { it.copy(lineHeightMultiplier = lineHeight) } }
            )

            Text("Tab 宽度", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 8).forEach { w ->
                    val selected = editor.tabWidth == w
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        TextButton(onClick = { commit { it.copy(tabWidth = w) } }) {
                            Text(
                                text = "$w 空格",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            SwitchRow("自动换行", editor.wordWrap) { v -> commit { it.copy(wordWrap = v) } }
            SwitchRow("显示行号", editor.showLineNumbers) { v -> commit { it.copy(showLineNumbers = v) } }
            SwitchRow("自动缩进", editor.autoIndent) { v -> commit { it.copy(autoIndent = v) } }
            SwitchRow("括号/引号自动配对", editor.autoPair) { v -> commit { it.copy(autoPair = v) } }
            SwitchRow("保存时用 black 格式化", editor.formatOnSave) { v ->
                commit { it.copy(formatOnSave = v) }
            }

            Text("配色方案", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorColorScheme.values().forEach { scheme ->
                    val selected = editor.colorScheme == scheme
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        TextButton(onClick = { commit { it.copy(colorScheme = scheme) } }) {
                            Text(
                                text = scheme.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------- 符号栏 --
        SectionCard(title = "符号栏（${settings.symbolBar.size} 个）") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                settings.symbolBar.forEach { sym ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                        ) {
                            Text(sym, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                vm.updateSymbolBar(settings.symbolBar - sym)
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSymbol,
                    onValueChange = { newSymbol = it.take(4) },
                    label = { Text("添加符号") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = newSymbol.isNotEmpty(),
                    onClick = {
                        vm.updateSymbolBar(settings.symbolBar + newSymbol)
                        newSymbol = ""
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("添加")
                }
            }
            TextButton(onClick = { vm.updateSymbolBar(AppSettings.DEFAULT_SYMBOLS) }) {
                Text("恢复默认符号栏")
            }
        }

        // -------------------------------------------------------- 快捷键 --
        SectionCard(title = "外接键盘快捷键") {
            Text(
                text = "支持 Ctrl / Alt / Shift + 字母组合，例如 Ctrl+S。修改后立即生效。\n" +
                    "撤销 / 重做由编辑器内置处理（默认 Ctrl+Z / Ctrl+Y），不在此处改写。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val shortcutLabels = mapOf(
                "save" to "保存",
                "run" to "运行",
                "find" to "查找",
                "replace" to "替换",
                "format" to "格式化",
                "debug" to "调试"
            )
            shortcutLabels.forEach { (action, label) ->
                var value by remember(settings.shortcuts[action]) {
                    mutableStateOf(settings.shortcuts[action].orEmpty())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(64.dp)
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        placeholder = { Text("Ctrl+S") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        onClick = { vm.updateShortcuts(settings.shortcuts + (action to value.trim())) },
                        enabled = value.trim() != settings.shortcuts[action]
                    ) { Text("应用") }
                }
            }
            TextButton(onClick = {
                vm.updateShortcuts(
                    settings.shortcuts.keys.associateWith { key ->
                        AppSettings.DEFAULT_SHORTCUTS[key] ?: settings.shortcuts[key].orEmpty()
                    }
                )
            }) { Text("恢复默认快捷键") }
            Text(
                text = "当前：" + settings.shortcuts.entries.joinToString("  ") { "${it.key}=${it.value}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // -------------------------------------------------------- 包管理 --
        SectionCard(title = "pip 镜像源") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PipMirror.values().forEach { m ->
                    val selected = settings.pipMirror == m.id
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.small
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
        }

        // ---------------------------------------------------- 导入 / 导出 --
        SectionCard(title = "设置导入 / 导出") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        exportText = vm.exportSettings()
                        showExport = true
                    }
                }) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("导出 JSON")
                }
                TextButton(onClick = {
                    importText = ""
                    showImport = true
                }) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("导入 JSON")
                }
            }
            Text(
                text = "导出内容可在其它设备粘贴导入，包含主题、字体、Tab 宽度、符号栏与快捷键。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // -------------------------------------------------------- 备份 --
        SectionCard(title = "项目备份") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showProjectMenu = true }) {
                    Text(vm.currentProject?.name ?: "（无项目）")
                }
                DropdownMenu(
                    expanded = showProjectMenu,
                    onDismissRequest = { showProjectMenu = false }
                ) {
                    vm.projects.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                vm.selectProject(p)
                                showProjectMenu = false
                            }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = vm.currentProject != null,
                    onClick = {
                        val project = vm.currentProject ?: return@TextButton
                        val dir = vm.services.appContext.getExternalFilesDir(null)
                            ?: vm.services.appContext.filesDir
                        val dest = File(dir, "backup_${project.name}_${System.currentTimeMillis()}.zip")
                        vm.backupProject(project, dest)
                        backupInfo = dest.absolutePath
                    }
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("导出 zip")
                }
            }
            Text(
                text = backupInfo ?: "导出的 zip 位于 App 外部私有目录，可用文件管理器或分享导出到电脑。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "关于") {
            // 版本号从 BuildConfig 读取，避免升级时忘记同步这里的硬编码文案。
            val version = BuildConfig.VERSION_NAME
            val build = BuildConfig.VERSION_CODE
            Text(
                text = "PyDroidLab $version（build $build）\n" +
                    "Python 3.11（Chaquopy 内嵌）· Jetpack Compose Material 3 · sora-editor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showExport) {
        JsonDialog(
            title = "设置 JSON（可复制）",
            value = exportText,
            readOnly = true,
            onDismiss = { showExport = false },
            onAction = {
                clipboard.setText(AnnotatedString(exportText))
                vm.postNotice("已复制设置 JSON")
            },
            actionLabel = "复制"
        )
    }

    if (showImport) {
        JsonDialog(
            title = "粘贴设置 JSON",
            value = importText,
            readOnly = false,
            onValueChange = { importText = it },
            onDismiss = { showImport = false },
            onAction = {
                scope.launch {
                    val ok = vm.importSettings(importText)
                    vm.postNotice(if (ok) "设置已导入" else "导入失败：JSON 格式不正确")
                    if (ok) showImport = false
                }
            },
            actionLabel = "导入"
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun JsonDialog(
    title: String,
    value: String,
    readOnly: Boolean,
    onValueChange: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    actionLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = { Text("JSON") }
            )
        },
        confirmButton = { TextButton(onClick = onAction) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
