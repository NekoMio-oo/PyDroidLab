package com.pydroidlab.app.ui.projects

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.project.Project
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.ConfirmDialog
import com.pydroidlab.app.ui.EmptyHint
import com.pydroidlab.app.ui.TextInputDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * ① 项目主页（Phase 1，TinaIDE 风格）
 *
 * 项目卡片列表：首字母头像 + 名称 + Python 标签 + 存储路径 + 最近更新绿点
 * + ⋮ 菜单；顶部搜索按名过滤；「最近打开 / 其他项目」分区；FAB 新建项目。
 * 点卡片进入「项目工作区」。
 */

/** 8 种柔和底色：按项目名哈希稳定取色。 */
private val AVATAR_COLORS = listOf(
    Color(0xFFDCEBFF), Color(0xFFDDF3E4), Color(0xFFFFF0D2), Color(0xFFFDE2E2),
    Color(0xFFEAE4FF), Color(0xFFDFF4F4), Color(0xFFFBE8F6), Color(0xFFEFF3D8)
)

private val AVATAR_FG = listOf(
    Color(0xFF2D5DA8), Color(0xFF2C7A4B), Color(0xFF9A6A1B), Color(0xFFA83B3B),
    Color(0xFF5B4BA8), Color(0xFF1F7A78), Color(0xFF93356E), Color(0xFF5F6B1F)
)

private fun avatarColorOf(name: String): Pair<Color, Color> {
    val idx = abs(name.hashCode()) % AVATAR_COLORS.size
    return AVATAR_COLORS[idx] to AVATAR_FG[idx]
}

private val mtimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@Composable
fun ProjectsScreen(vm: AppViewModel, onOpenWorkspace: () -> Unit) {
    var menuFor by remember { mutableStateOf<Project?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<Project?>(null) }
    var deleteFor by remember { mutableStateOf<Project?>(null) }

    vm.filesRevision // 项目内文件改动会刷新 mtime
    val recent = vm.recentProjects
    val filtered = vm.filteredProjects
    val others = filtered.filter { p -> recent.none { it.name == p.name } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索框（按名过滤）
            OutlinedTextField(
                value = vm.projectSearchQuery,
                onValueChange = vm::setProjectSearch,
                placeholder = { Text("搜索项目…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                EmptyHint(
                    if (vm.projectSearchQuery.isBlank()) "还没有项目，点右下角「＋」新建一个。"
                    else "没有匹配「${vm.projectSearchQuery}」的项目"
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (recent.isNotEmpty()) {
                        item { SectionLabel("最近打开") }
                        items(recent, key = { "r_" + it.name }) { p ->
                            ProjectCard(
                                project = p,
                                onOpen = {
                                    vm.openProject(p)
                                    onOpenWorkspace()
                                },
                                onMenu = { menuFor = p }
                            )
                        }
                        if (others.isNotEmpty()) {
                            item { SectionLabel("其他项目") }
                        }
                    }
                    items(others, key = { it.name }) { p ->
                        ProjectCard(
                            project = p,
                            onOpen = {
                                vm.openProject(p)
                                onOpenWorkspace()
                            },
                            onMenu = { menuFor = p }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建项目")
        }
    }

    // ---- 菜单与对话框 ------------------------------------------------------
    menuFor?.let { p ->
        DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
            DropdownMenuItem(
                text = { Text("打开") },
                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                onClick = {
                    menuFor = null
                    vm.openProject(p)
                    onOpenWorkspace()
                }
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                onClick = {
                    menuFor = null
                    renameFor = p
                }
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    menuFor = null
                    deleteFor = p
                }
            )
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "新建项目",
            label = "项目名称",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                vm.createProject(name)
                showCreate = false
                vm.openProject(vm.projects.firstOrNull { it.name == name.trim() } ?: vm.projects.first())
                onOpenWorkspace()
            }
        )
    }

    renameFor?.let { p ->
        TextInputDialog(
            title = "重命名项目",
            label = "新名称",
            initial = p.name,
            onDismiss = { renameFor = null },
            onConfirm = { name ->
                vm.renameProject(p, name)
                renameFor = null
            }
        )
    }

    deleteFor?.let { p ->
        ConfirmDialog(
            title = "删除项目",
            message = "「${p.name}」及其全部文件将被删除，且无法恢复。确定删除吗？",
            confirmLabel = "删除",
            onDismiss = { deleteFor = null },
            onConfirm = {
                vm.deleteProject(p)
                deleteFor = null
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun ProjectCard(project: Project, onOpen: () -> Unit, onMenu: () -> Unit) {
    val (bg, fg) = avatarColorOf(project.name)
    val updated = remember(project.dir.absolutePath, project.dir.lastModified()) {
        mtimeFmt.format(Date(project.dir.lastModified()))
    }
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 首字母头像（按名称哈希取柔和底色）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(bg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = project.name.take(1).uppercase(),
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Python",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        modifier = Modifier
                            .background(bg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = shortPath(project.dir),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 「最近更新」绿点
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF34A853), RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "最近更新",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = updated,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = "项目菜单")
            }
        }
    }
}

/** 路径展示收敛成 …/projects/<名>，避免撑破卡片。 */
private fun shortPath(dir: File): String {
    val s = dir.absolutePath
    val marker = "projects/"
    val idx = s.lastIndexOf(marker)
    return if (idx >= 0) "…/" + s.substring(idx) else s
}
