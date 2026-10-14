package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.data.WorkspaceFileItem
import com.pydroidlab.app.ui.FileBrowserUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerScreen(
    browserState: FileBrowserUiState,
    workspaceRoot: File,
    onOpenFile: (File) -> Unit,
    onNavigateDirectory: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameItem: (WorkspaceFileItem, String) -> Unit,
    onMoveItem: (WorkspaceFileItem, File) -> Unit,
    onDeleteItem: (WorkspaceFileItem) -> Unit
) {
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<WorkspaceFileItem?>(null) }
    var itemToMove by remember { mutableStateOf<WorkspaceFileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<WorkspaceFileItem?>(null) }

    val isAtRoot = browserState.currentDir.canonicalPath == workspaceRoot.canonicalPath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Breadcrumb & Action Toolbar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onNavigateUp,
                        enabled = !isAtRoot
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "返回上级目录")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "应用私有工作区文件浏览器",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = browserState.relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("新建文件")
                    }

                    OutlinedButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("新建文件夹")
                    }
                }
            }
        }

        HorizontalDivider()

        // 2. Directory Contents List
        if (browserState.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "当前文件夹为空",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(browserState.items, key = { it.path }) { item ->
                    WorkspaceFileRow(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                onNavigateDirectory(item.file)
                            } else {
                                onOpenFile(item.file)
                            }
                        },
                        onRename = { itemToRename = item },
                        onMove = { itemToMove = item },
                        onDelete = { itemToDelete = item }
                    )
                }
            }
        }
    }

    if (showNewFileDialog) {
        FileNameInputDialog(
            title = "新建文件",
            initialName = "untitled.py",
            confirmText = "创建",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                showNewFileDialog = false
                onCreateFile(name)
            }
        )
    }

    if (showNewFolderDialog) {
        FileNameInputDialog(
            title = "新建文件夹",
            initialName = "new_module",
            confirmText = "创建",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                onCreateFolder(name)
            }
        )
    }

    itemToRename?.let { target ->
        FileNameInputDialog(
            title = "重命名 ${target.name}",
            initialName = target.name,
            confirmText = "重命名",
            onDismiss = { itemToRename = null },
            onConfirm = { newName ->
                onRenameItem(target, newName)
                itemToRename = null
            }
        )
    }

    itemToMove?.let { target ->
        MoveFileDialog(
            item = target,
            directories = browserState.allDirectories,
            workspaceRoot = workspaceRoot,
            onDismiss = { itemToMove = null },
            onConfirmMove = { destDir ->
                onMoveItem(target, destDir)
                itemToMove = null
            }
        )
    }

    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = {
                Text("确定要永久删除 ${if (target.isDirectory) "文件夹" else "文件"} '${target.name}' 吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(target)
                        itemToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceFileRow(
    item: WorkspaceFileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                val meta = if (item.isDirectory) {
                    "文件夹 • ${dateFormat.format(Date(item.lastModified))}"
                } else {
                    "${item.sizeBytes} B • ${dateFormat.format(Date(item.lastModified))}"
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRename) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "重命名")
            }
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "移动")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MoveFileDialog(
    item: WorkspaceFileItem,
    directories: List<File>,
    workspaceRoot: File,
    onDismiss: () -> Unit,
    onConfirmMove: (File) -> Unit
) {
    var selectedDir by remember { mutableStateOf(workspaceRoot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动 '${item.name}' 到...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                directories.forEach { dir ->
                    val rel = if (dir.canonicalPath == workspaceRoot.canonicalPath) {
                        "/workspace (根目录)"
                    } else {
                        "/workspace" + dir.canonicalPath.removePrefix(workspaceRoot.canonicalPath)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDir = dir }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedDir.canonicalPath == dir.canonicalPath,
                            onClick = { selectedDir = dir }
                        )
                        Text(
                            text = rel,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmMove(selectedDir) }) {
                Text("移动")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
