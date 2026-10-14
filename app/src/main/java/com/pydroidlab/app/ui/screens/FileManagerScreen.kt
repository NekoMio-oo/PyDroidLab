package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Minimal Path & Action Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isAtRoot) {
                IconButton(onClick = onNavigateUp, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "上级目录",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = browserState.relativePath,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showNewFileDialog = true }) {
                Text("新建文件", fontSize = 13.sp)
            }
            TextButton(onClick = { showNewFolderDialog = true }) {
                Text("新建目录", fontSize = 13.sp)
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        if (browserState.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无文件",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(browserState.items, key = { it.path }) { item ->
                    MinimalFileRow(
                        item = item,
                        onClick = {
                            if (item.isDirectory) onNavigateDirectory(item.file)
                            else onOpenFile(item.file)
                        },
                        onRename = { itemToRename = item },
                        onMove = { itemToMove = item },
                        onDelete = { itemToDelete = item }
                    )
                    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                }
            }
        }
    }

    if (showNewFileDialog) {
        FileNameInputDialog(
            title = "新建文件",
            initialName = "untitled.py",
            confirmText = "确定",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                showNewFileDialog = false
                onCreateFile(name)
            }
        )
    }

    if (showNewFolderDialog) {
        FileNameInputDialog(
            title = "新建目录",
            initialName = "module",
            confirmText = "确定",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                onCreateFolder(name)
            }
        )
    }

    itemToRename?.let { target ->
        FileNameInputDialog(
            title = "重命名",
            initialName = target.name,
            confirmText = "确定",
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
            title = { Text("删除确认", fontSize = 16.sp) },
            text = { Text("确定删除 ${target.name}？") },
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
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MinimalFileRow(
    item: WorkspaceFileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            val meta = if (item.isDirectory) {
                dateFormat.format(Date(item.lastModified))
            } else {
                "${item.sizeBytes} B  •  ${dateFormat.format(Date(item.lastModified))}"
            }
            Text(
                text = meta,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("移动") },
                    onClick = {
                        menuExpanded = false
                        onMove()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
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
        title = { Text("移动 ${item.name}", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                directories.forEach { dir ->
                    val rel = if (dir.canonicalPath == workspaceRoot.canonicalPath) {
                        "/workspace"
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
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmMove(selectedDir) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
