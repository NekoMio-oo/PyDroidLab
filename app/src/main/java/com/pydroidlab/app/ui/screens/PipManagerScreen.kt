package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.runtime.InstalledPackage
import com.pydroidlab.app.ui.PipManagerUiState

private val RECOMMENDED_PACKAGES = listOf(
    "requests",
    "beautifulsoup4",
    "rich",
    "sympy",
    "pygments",
    "httpx",
    "colorama",
    "tabulate"
)

@Composable
fun PipManagerScreen(
    pipState: PipManagerUiState,
    userSitePackagesPath: String,
    onInstallPackage: (String, Boolean) -> Unit,
    onUninstallPackage: (String) -> Unit,
    onRefreshList: () -> Unit
) {
    var packageInput by remember { mutableStateOf("") }
    var showLogConsole by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header & Install Bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "pip 第三方库管理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "安装目录: $userSitePackagesPath",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onRefreshList, enabled = !pipState.isBusy) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新已安装列表")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        label = { Text("输入包名 (如 requests 或 requests==2.31.0)") },
                        singleLine = true,
                        enabled = !pipState.isBusy,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (packageInput.isNotBlank()) {
                                showLogConsole = true
                                onInstallPackage(packageInput.trim(), false)
                            }
                        },
                        enabled = !pipState.isBusy && packageInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("安装")
                    }
                }

                // Quick install chips
                Text(
                    text = "常用库一键安装 / 升级：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RECOMMENDED_PACKAGES.forEach { pkg ->
                        AssistChip(
                            onClick = {
                                packageInput = pkg
                                showLogConsole = true
                                onInstallPackage(pkg, false)
                            },
                            enabled = !pipState.isBusy,
                            label = { Text(pkg, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // 2. Progress / Live Pip Output Log
        if (pipState.isBusy || pipState.pipLogs.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (pipState.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pipState.currentActionLabel,
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = pipState.lastResultBanner ?: "pip 执行日志",
                                color = Color(0xFF4ADE80),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (pipState.pipLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF020617), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = pipState.pipLogs,
                                color = Color(0xFFCBD5E1),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Installed Packages List
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "已安装的包 (${pipState.installedPackages.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "支持升级与卸载私有目录包",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pipState.installedPackages, key = { it.name }) { pkg ->
                InstalledPackageCard(
                    pkg = pkg,
                    isBusy = pipState.isBusy,
                    onUpgrade = {
                        showLogConsole = true
                        onInstallPackage(pkg.name, true)
                    },
                    onUninstall = {
                        showLogConsole = true
                        onUninstallPackage(pkg.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun InstalledPackageCard(
    pkg: InstalledPackage,
    isBusy: Boolean,
    onUpgrade: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = pkg.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "v${pkg.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = if (pkg.isUserInstalled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (pkg.isUserInstalled) "用户安装 (私有目录)" else "内置预装",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (pkg.summary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pkg.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            OutlinedButton(
                onClick = onUpgrade,
                enabled = !isBusy,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Icon(Icons.Default.SystemUpdateAlt, contentDescription = "升级", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("升级", fontSize = 12.sp)
            }

            if (pkg.isUserInstalled) {
                IconButton(
                    onClick = onUninstall,
                    enabled = !isBusy
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "卸载",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
