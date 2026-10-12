package com.pydroidlab.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

/** 导航抽屉里的一项。 */
data class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** 全部 10 个功能界面（顺序即抽屉顺序）。 */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PyDroidLabRoot(vm: AppViewModel) {
    PyDroidLabTheme(themeMode = vm.settings.themeMode) {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
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
        val currentRoute = backStackEntry?.destination?.route ?: AppViewModel.ROUTE_EDITOR
        val currentTitle = APP_DESTINATIONS.firstOrNull { it.route == currentRoute }?.label ?: "PyDroidLab"

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "PyDroidLab",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Python 3.11 · Chaquopy · Compose",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    APP_DESTINATIONS.forEach { dest ->
                        NavigationDrawerItem(
                            label = { Text(dest.label) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            selected = dest.route == currentRoute,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (dest.route != currentRoute) navController.navigateTo(dest.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "打开导航")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = AppViewModel.ROUTE_EDITOR,
                    modifier = Modifier
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    composable(AppViewModel.ROUTE_EDITOR) { EditorScreen(vm) }
                    composable(AppViewModel.ROUTE_CONSOLE) { ConsoleScreen(vm) }
                    composable(AppViewModel.ROUTE_TERMINAL) { TerminalScreen(vm) }
                    composable(AppViewModel.ROUTE_FILES) { FilesScreen(vm) }
                    composable(AppViewModel.ROUTE_PACKAGES) { PackagesScreen(vm) }
                    composable(AppViewModel.ROUTE_CHARTS) { ChartsScreen(vm) }
                    composable(AppViewModel.ROUTE_DEBUGGER) { DebuggerScreen(vm) }
                    composable(AppViewModel.ROUTE_REPL) { ReplScreen(vm) }
                    composable(AppViewModel.ROUTE_LEARN) { LearnScreen(vm) }
                    composable(AppViewModel.ROUTE_SETTINGS) { SettingsScreen(vm) }
                }
            }
        }
    }
}

/** 单层返回栈 + 单个实例，避免抽屉反复点击堆栈膨胀。 */
private fun androidx.navigation.NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
