package com.pydroidlab.app.ui.workspace

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.editor.EditorScreen
import com.pydroidlab.app.ui.shareText
import kotlinx.coroutines.launch
import java.io.File

/**
 * 「项目工作区」（Phase 1，TinaIDE 风格）
 *
 * - 顶部 pill 工具栏：☰ 文件树抽屉 ｜ 运行配置下拉 ｜ ▶ 运行 ｜ 🐞 调试 ｜ 💾 保存 ｜ ⋮ 更多
 * - 文件标签页 + 编辑器：复用 [EditorScreen]（embedded 模式，Phase 0 的
 *   Bug 1/2/3/7 修复都保留在内）
 * - 左侧抽屉：当前项目的树形文件浏览，点文件在标签页打开
 * - 控制台 / 终端 / 图表 / 调试 / REPL / 文件管理：Phase 1 先以菜单跳转到
 *   独立页（Phase 2 换成工作区内嵌面板）
 */
@Composable
fun WorkspaceScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val project = vm.currentProject
    if (project == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("还没有打开的项目", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.closeProject() }) { Text("回到项目页") }
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FileTreeDrawer(
                    vm = vm,
                    root = project.dir,
                    onFileOpen = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PillToolbar(
                vm = vm,
                onToggleDrawer = { scope.launch { drawerState.open() } },
                onNavigate = onNavigate,
                onShare = {
                    val text = vm.editor.active?.content?.toString().orEmpty()
                    if (text.isBlank()) vm.postNotice("没有可分享的内容")
                    else shareText(context, vm.editor.active?.title ?: "code.py", text)
                }
            )
            EditorScreen(vm, embedded = true)
        }
    }
}

/** 顶部 pill 工具栏：每个动作是一枚圆角 pill，横向可滚动。 */
@Composable
private fun PillToolbar(
    vm: AppViewModel,
    onToggleDrawer: () -> Unit,
    onNavigate: (String) -> Unit,
    onShare: () -> Unit
) {
    var runConfigMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val activeFile = vm.editor.active?.file

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PillIcon(Icons.Default.Menu, "文件树", onClick = onToggleDrawer)

            // 运行配置下拉（只读概览 + 跳转编辑对话框）
            Box {
                PillIcon(Icons.Default.Tune, "运行配置", onClick = { runConfigMenu = true })
                DropdownMenu(expanded = runConfigMenu, onDismissRequest = { runConfigMenu = false }) {
                    val file = activeFile
                    if (file == null) {
                        DropdownMenuItem(
                            text = { Text("先在标签页打开一个文件") },
                            onClick = { runConfigMenu = false }
                        )
                    } else {
                        val opts = vm.runOptionsFor(file)
                        DropdownMenuItem(
                            text = { Text("argv：${opts.argv.ifBlank { "（无）" }}") },
                            onClick = { runConfigMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("cwd：${opts.cwd.ifBlank { "脚本所在目录" }}") },
                            onClick = { runConfigMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("env：${opts.env.ifBlank { "（无）" }}") },
                            onClick = { runConfigMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("stdin：${opts.stdinPreset.ifBlank { "（无）" }}") },
                            onClick = { runConfigMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("编辑运行配置…") },
                            onClick = {
                                runConfigMenu = false
                                vm.requestEditRunConfig()
                            }
                        )
                    }
                }
            }

            PillAction(
                icon = Icons.Default.PlayArrow,
                label = "运行",
                emphasized = true,
                enabled = !vm.isRunning
            ) { vm.runActiveScript() }

            PillAction(Icons.Default.BugReport, "调试", enabled = !vm.isRunning) {
                vm.startDebug()
            }

            // 未命名文件保存时自动落 scratch（Phase 0 Bug 3 的逻辑接在这里）
            PillAction(Icons.Default.Save, "保存") { vm.saveActive() }

            Box {
                PillIcon(Icons.Default.MoreVert, "更多", onClick = { moreMenu = true })
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("格式化（black）") },
                        leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            vm.formatActiveTab()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("分享当前文件") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            onShare()
                        }
                    )
                    MenuDivider()
                    DropdownMenuItem(
                        text = { Text("控制台") },
                        leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                        onClick = { moreMenu = false; onNavigate(AppViewModel.ROUTE_CONSOLE) }
                    )
                    DropdownMenuItem(
                        text = { Text("调试器") },
                        leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        onClick = { moreMenu = false; onNavigate(AppViewModel.ROUTE_DEBUGGER) }
                    )
                    DropdownMenuItem(
                        text = { Text("终端 / 图表 / REPL / 文件管理") },
                        onClick = { moreMenu = false; onNavigate(AppViewModel.ROUTE_TERMINAL) }
                    )
                    MenuDivider()
                    DropdownMenuItem(
                        text = { Text("关闭项目") },
                        onClick = {
                            moreMenu = false
                            vm.closeProject()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 圆形图标 pill。 */
@Composable
private fun PillIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(38.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
            Icon(icon, contentDescription = desc, modifier = Modifier.size(20.dp))
        }
    }
}

/** 图标 + 文字 pill；[emphasized] 的运行键用 primary 底色。 */
@Composable
private fun PillAction(
    icon: ImageVector,
    label: String,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (emphasized) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val fg = if (emphasized) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = CircleShape,
        color = bg,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) fg else fg.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = if (enabled) fg else fg.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * 左侧抽屉：当前项目的树形文件浏览。
 * 项目文件数量级小，目录直接递归渲染；点文件关闭抽屉并在标签页打开。
 */
@Composable
private fun FileTreeDrawer(vm: AppViewModel, root: File, onFileOpen: () -> Unit) {
    // 已展开目录的绝对路径集合
    val expanded = remember { mutableStateOf(setOf<String>()) }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewDir by remember { mutableStateOf(false) }

    vm.filesRevision

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                text = vm.currentProject?.name ?: "项目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = root.absolutePath,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            TreeNode(vm, root, 0, expanded, onFileOpen)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = { showNewFile = true }) {
                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建文件")
            }
            TextButton(onClick = { showNewDir = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建文件夹")
            }
        }
    }

    if (showNewFile) {
        com.pydroidlab.app.ui.TextInputDialog(
            title = "新建文件",
            label = "文件名（如 main.py）",
            onDismiss = { showNewFile = false },
            onConfirm = { name ->
                vm.newFile(name)
                showNewFile = false
            }
        )
    }
    if (showNewDir) {
        com.pydroidlab.app.ui.TextInputDialog(
            title = "新建文件夹",
            label = "文件夹名",
            onDismiss = { showNewDir = false },
            onConfirm = { name ->
                vm.newDir(name)
                showNewDir = false
            }
        )
    }
}

@Composable
private fun TreeNode(
    vm: AppViewModel,
    file: File,
    depth: Int,
    expanded: androidx.compose.runtime.MutableState<Set<String>>,
    onFileOpen: () -> Unit
) {
    val isDir = file.isDirectory
    val isOpen = file.absolutePath in expanded.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDir) {
                    expanded.value = if (isOpen) expanded.value - file.absolutePath
                    else expanded.value + file.absolutePath
                } else {
                    vm.openFile(file)
                    onFileOpen()
                }
            }
            .padding(start = (12 + depth * 16).dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                isDir && isOpen -> Icons.Default.FolderOpen
                isDir -> Icons.Default.Folder
                else -> Icons.Default.Description
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }

    if (isDir && isOpen) {
        vm.services.projectManager.listFiles(file).forEach { child ->
            TreeNode(vm, child, depth + 1, expanded, onFileOpen)
        }
    }
}
