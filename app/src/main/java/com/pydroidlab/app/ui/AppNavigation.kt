package com.pydroidlab.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pydroidlab.app.ui.charts.ChartsScreen
import com.pydroidlab.app.ui.console.ConsoleScreen
import com.pydroidlab.app.ui.debugger.DebuggerScreen
import com.pydroidlab.app.ui.editor.EditorScreen
import com.pydroidlab.app.ui.files.FilesScreen
import com.pydroidlab.app.ui.learn.LearnScreen
import com.pydroidlab.app.ui.packages.PackagesScreen
import com.pydroidlab.app.ui.repl.ReplScreen
import com.pydroidlab.app.ui.settings.SettingsScreen
import com.pydroidlab.app.ui.terminal.TerminalScreen
import com.pydroidlab.app.ui.theme.PyDroidLabTheme
import com.pydroidlab.app.ui.workspace.WorkspaceScreen

/** 导航里的一项（旧 10 页在 Phase 2 迁移期间保留独立入口）。 */
data class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** 全部 10 个功能界面。 */
val APP_DESTINATIONS: List<AppDestination> = listOf(
    AppDestination(AppViewModel.ROUTE_EDITOR, "编辑器", Icons.Default.Code),
    AppDestination(AppViewModel.ROUTE_CONSOLE, "控制台", Icons.Default.Article),
    AppDestination(AppViewModel.ROUTE_TERMINAL, "终端", Icons.Default.Terminal),
    AppDestination(AppViewModel.ROUTE_FILES, "文件", Icons.Default.Folder),
    AppDestination(AppViewModel.ROUTE_PACKAGES, "软件包", Icons.Default.Extension),
    AppDestination(AppViewModel.ROUTE_CHARTS, "图表", Icons.Default.InsertChart),
    AppDestination(AppViewModel.ROUTE_DEBUGGER, "调试", Icons.Default.BugReport),
    AppDestination(AppViewModel.ROUTE_REPL, "REPL", Icons.Default.Terminal),
    AppDestination(AppViewModel.ROUTE_LEARN, "教程", Icons.Default.School),
    AppDestination(AppViewModel.ROUTE_SETTINGS, "设置", Icons.Default.Settings)
)

private fun destinationIcon(route: String): ImageVector =
    APP_DESTINATIONS.firstOrNull { it.route == route }?.icon ?: Icons.Default.Folder

private fun destinationLabel(route: String): String =
    APP_DESTINATIONS.firstOrNull { it.route == route }?.label ?: "PyDroidLab"

@Composable
fun PyDroidLabRoot(vm: AppViewModel) {
    PyDroidLabTheme(themeMode = vm.settings.themeMode) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        // ViewModel 发起的跳转（例如「运行」后自动打开控制台）。
        LaunchedEffect(vm.navigateTo) {
            val route = vm.navigateTo
            if (route != null) {
                navController.navigateTo(route)
                vm.consumeNavigate()
            }
        }

        LaunchedEffect(vm.notice) {
            val msg = vm.notice
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                vm.consumeNotice()
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: AppViewModel.ROUTE_MAIN

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppViewModel.ROUTE_MAIN,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // 底部导航主框架（项目 / 软件包 / 教程 / 我的）
                composable(AppViewModel.ROUTE_MAIN) {
                    MainScaffold(
                        vm = vm,
                        onOpenWorkspace = {
                            navController.navigate(AppViewModel.ROUTE_WORKSPACE) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // 项目工作区（独立于底部导航；状态在 AppViewModel，不随导航丢失）
                composable(AppViewModel.ROUTE_WORKSPACE) {
                    WorkspaceScreen(vm, onNavigate = { r -> navController.navigateTo(r) })
                }

                // 旧 10 页：Phase 2 陆续迁入工作区；期间全部保留可达
                composable(AppViewModel.ROUTE_EDITOR) {
                    SubPage(vm, AppViewModel.ROUTE_EDITOR, onBack = { navController.popBackStack() }) {
                        EditorScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_CONSOLE) {
                    SubPage(vm, AppViewModel.ROUTE_CONSOLE, onBack = { navController.popBackStack() }) {
                        ConsoleScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_TERMINAL) {
                    SubPage(vm, AppViewModel.ROUTE_TERMINAL, onBack = { navController.popBackStack() }) {
                        TerminalScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_FILES) {
                    SubPage(vm, AppViewModel.ROUTE_FILES, onBack = { navController.popBackStack() }) {
                        FilesScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_PACKAGES) {
                    SubPage(vm, AppViewModel.ROUTE_PACKAGES, onBack = { navController.popBackStack() }) {
                        PackagesScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_CHARTS) {
                    SubPage(vm, AppViewModel.ROUTE_CHARTS, onBack = { navController.popBackStack() }) {
                        ChartsScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_DEBUGGER) {
                    SubPage(vm, AppViewModel.ROUTE_DEBUGGER, onBack = { navController.popBackStack() }) {
                        DebuggerScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_REPL) {
                    SubPage(vm, AppViewModel.ROUTE_REPL, onBack = { navController.popBackStack() }) {
                        ReplScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_LEARN) {
                    SubPage(vm, AppViewModel.ROUTE_LEARN, onBack = { navController.popBackStack() }) {
                        LearnScreen(vm)
                    }
                }
                composable(AppViewModel.ROUTE_SETTINGS) {
                    SubPage(vm, AppViewModel.ROUTE_SETTINGS, onBack = { navController.popBackStack() }) {
                        SettingsScreen(vm)
                    }
                }
            }

            // 全局 Snackbar 悬浮在底部（所有页面共享 VM 的 postNotice）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (currentRoute == AppViewModel.ROUTE_MAIN) 88.dp else 16.dp)
            )
        }
    }
}

/** 带返回箭头的独立页面容器（旧 10 页通用）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPage(vm: AppViewModel, route: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destinationLabel(route)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            content()
        }
    }
}

/** 单层返回栈 + 单个实例，避免反复点击堆栈膨胀。 */
private fun androidx.navigation.NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
