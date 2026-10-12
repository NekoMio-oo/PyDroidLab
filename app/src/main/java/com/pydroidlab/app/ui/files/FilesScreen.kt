package com.pydroidlab.app.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.project.Project
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.ConfirmDialog
import com.pydroidlab.app.ui.EmptyHint
import com.pydroidlab.app.ui.shareFile
import java.io.File

/**
 * ④ 文件浏览器
 *
 * 项目切换 + 目录导航 + 文件 CRUD（新建 / 重命名 / 删除 / 复制 / 移动）
 * 支持多选，并可通过 SAF 从系统文件选择器导入文件。
 */
@Composable
fun FilesScreen(vm: AppViewModel) {
    vm.filesRevision

    val context = LocalContext.current
    val project = vm.currentProject
    val dir = vm.currentDir

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewDir by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf<PickMode?>(null) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importViaSaf(uri)
    }

    val entries = remember(dir, vm.filesRevision) {
        dir?.let { vm.services.projectManager.listFiles(it) } ?: emptyList()
    }
    val selectedFiles = entries.filter { it.absolutePath in selected }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 顶部：项目选择 ----
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "项目：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        TextButton(onClick = { showProjectMenu = true }) {
                            Text(project?.name ?: "（无项目）", fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                        selected = emptySet()
                                        showProjectMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("＋ 新建项目") },
                                onClick = {
                                    showProjectMenu = false
                                    showNewProject = true
                                }
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.refreshProjects(); vm.refreshDir() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.goUp() }, enabled = dir != null && dir != project?.dir) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("上级")
                    }
                    Text(
                        text = dir?.let { relPath(it, project?.dir) } ?: "-",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ---- 操作栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = { showNewFile = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("新建文件")
            }
            TextButton(onClick = { showNewDir = true }) { Text("新建文件夹") }
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("导入(SAF)")
            }
            TextButton(
                onClick = { showRename = true },
                enabled = selectedFiles.size == 1
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("重命名")
            }
            TextButton(
                onClick = { showPicker = PickMode.COPY },
                enabled = selectedFiles.isNotEmpty()
            ) { Text("复制到") }
            TextButton(
                onClick = { showPicker = PickMode.MOVE },
                enabled = selectedFiles.isNotEmpty()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("移动到")
            }
            TextButton(
                onClick = { showDelete = true },
                enabled = selectedFiles.isNotEmpty()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("删除")
            }
            TextButton(
                onClick = {
                    project?.let {
                        val dest = File(
                            vm.services.appContext.getExternalFilesDir(null) ?: vm.services.appContext.filesDir,
                            "${it.name}.zip"
                        )
                        // 备份完直接拉起分享面板，方便导出到电脑
                        vm.backupProject(it, dest) { zip -> shareFile(context, zip, "application/zip", zip.name) }
                    }
                },
                enabled = project != null
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("备份 zip")
            }
        }

        Text(
            text = if (selected.isEmpty()) {
                "点击打开文件 · 勾选可多选"
            } else {
                "已选 ${selected.size} 项"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        HorizontalDivider()

        // ---- 列表 ----
        if (entries.isEmpty()) {
            EmptyHint("这个目录是空的，点「新建文件」开始写代码。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.absolutePath }) { f ->
                    val checked = f.absolutePath in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selected.isNotEmpty()) {
                                    selected = toggle(selected, f.absolutePath)
                                } else if (f.isDirectory) {
                                    vm.enterDir(f)
                                } else {
                                    vm.openFile(f)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected = toggle(selected, f.absolutePath) }
                        )
                        Icon(
                            imageVector = if (f.isDirectory) Icons.Default.Folder else Icons.Default.Article,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = f.name + if (f.isDirectory) "/" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (f.isDirectory) "目录" else humanSize(f.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!f.isDirectory && f.name.endsWith(".py")) {
                            TextButton(onClick = { vm.runFile(f) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // ---- 对话框 ----
    if (showNewFile) {
        NameDialog(
            title = "新建文件",
            initial = "main.py",
            onDismiss = { showNewFile = false },
            onConfirm = { name ->
                vm.newFile(name)
                showNewFile = false
            }
        )
    }

    if (showNewDir) {
        NameDialog(
            title = "新建文件夹",
            initial = "new_folder",
            onDismiss = { showNewDir = false },
            onConfirm = { name ->
                vm.newDir(name)
                showNewDir = false
            }
        )
    }

    if (showNewProject) {
        NameDialog(
            title = "新建项目",
            initial = "my_project",
            onDismiss = { showNewProject = false },
            onConfirm = { name ->
                vm.createProject(name)
                selected = emptySet()
                showNewProject = false
            }
        )
    }

    if (showRename && selectedFiles.size == 1) {
        val target = selectedFiles.first()
        NameDialog(
            title = "重命名",
            initial = target.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                vm.renameFile(target, name)
                selected = emptySet()
                showRename = false
            }
        )
    }

    if (showDelete && selectedFiles.isNotEmpty()) {
        ConfirmDialog(
            title = "删除确认",
            message = "将永久删除 ${selectedFiles.size} 个项目：\n" +
                selectedFiles.joinToString("\n") { it.name },
            confirmLabel = "删除",
            onDismiss = { showDelete = false },
            onConfirm = {
                vm.deleteFiles(selectedFiles)
                selected = emptySet()
                showDelete = false
            }
        )
    }

    val mode = showPicker
    if (mode != null) {
        DirectoryPicker(
            root = project?.dir,
            title = if (mode == PickMode.COPY) "复制到" else "移动到",
            onDismiss = { showPicker = null },
            onPick = { dest ->
                if (mode == PickMode.COPY) vm.copyFiles(selectedFiles, dest) else vm.moveFiles(selectedFiles, dest)
                selected = emptySet()
                showPicker = null
            }
        )
    }
}

private enum class PickMode { COPY, MOVE }

private fun toggle(set: Set<String>, path: String): Set<String> =
    if (path in set) set - path else set + path

private fun relPath(dir: File, root: File?): String {
    if (root == null) return dir.absolutePath
    val p = dir.absolutePath
    val r = root.absolutePath
    return if (p == r) root.name else p.removePrefix("$r/")
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DirectoryPicker(
    root: File?,
    title: String,
    onDismiss: () -> Unit,
    onPick: (File) -> Unit
) {
    val dirs = remember(root) { root?.let { flattenDirs(it, 3) } ?: emptyList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (dirs.isEmpty()) {
                Text("没有可选的子目录")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(dirs) { entry ->
                        Text(
                            text = "  ".repeat(entry.second) + entry.first.name + "/",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry.first) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun flattenDirs(root: File, maxDepth: Int): List<Pair<File, Int>> {
    val out = mutableListOf<Pair<File, Int>>()
    fun walk(dir: File, depth: Int) {
        out.add(dir to depth)
        if (depth >= maxDepth) return
        dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { walk(it, depth + 1) }
    }
    walk(root, 0)
    return out
}
