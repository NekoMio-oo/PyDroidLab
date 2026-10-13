package com.pydroidlab.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.ui.learn.LearnScreen
import com.pydroidlab.app.ui.packages.PackagesScreen
import com.pydroidlab.app.ui.projects.ProjectsScreen
import com.pydroidlab.app.ui.settings.SettingsScreen

/**
 * 底部导航主框架（Phase 1，TinaIDE 风格）：项目 / 软件包 / 教程 / 我的。
 *
 * Tab 内容切换用状态而非嵌套 NavHost —— 四个页面的滚动位置与临时状态由
 * 各自的 remember 保持；编辑器等工作区状态全部在 [AppViewModel]（单例），
 * 因此「底部 4 Tab 切换不丢工作区状态」天然成立。
 */
private data class MainTab(val label: String)

@Composable
fun MainScaffold(vm: AppViewModel, onOpenWorkspace: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("项目", "软件包", "教程", "我的")

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("项目") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    label = { Text("软件包") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.School, contentDescription = null) },
                    label = { Text("教程") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 页标题：20sp 加粗（视觉规范）
            Text(
                text = titles[tab],
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> ProjectsScreen(vm, onOpenWorkspace)
                    1 -> PackagesScreen(vm)
                    2 -> LearnScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
