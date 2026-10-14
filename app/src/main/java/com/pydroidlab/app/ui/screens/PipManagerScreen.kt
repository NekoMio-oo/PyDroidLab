package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    placeholder = { Text("输入包名，如 requests", fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !pipState.isBusy,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = {
                        if (packageInput.isNotBlank()) {
                            onInstallPackage(packageInput.trim(), false)
                        }
                    },
                    enabled = !pipState.isBusy && packageInput.isNotBlank()
                ) {
                    Text("安装", fontWeight = FontWeight.SemiBold)
                }
            }

            // Minimal quick-install tags
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RECOMMENDED_PACKAGES.forEach { pkg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .clickable(enabled = !pipState.isBusy) {
                                packageInput = pkg
                                onInstallPackage(pkg, false)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = pkg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (pipState.isBusy || pipState.pipLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0D1117))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pipState.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF58A6FF)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pipState.currentActionLabel,
                                color = Color(0xFF58A6FF),
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = pipState.lastResultBanner ?: "完成",
                                color = Color(0xFF8B949E),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (pipState.pipLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = pipState.pipLogs,
                                color = Color(0xFFC9D1D9),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已安装 (${pipState.installedPackages.size})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefreshList, enabled = !pipState.isBusy) {
                Text("刷新", fontSize = 12.sp)
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(pipState.installedPackages, key = { it.name }) { pkg ->
                MinimalPackageRow(
                    pkg = pkg,
                    isBusy = pipState.isBusy,
                    onUpgrade = { onInstallPackage(pkg.name, true) },
                    onUninstall = { onUninstallPackage(pkg.name) }
                )
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun MinimalPackageRow(
    pkg: InstalledPackage,
    isBusy: Boolean,
    onUpgrade: () -> Unit,
    onUninstall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pkg.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pkg.version,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (pkg.isUserInstalled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "用户",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (pkg.summary.isNotEmpty()) {
                Text(
                    text = pkg.summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        TextButton(onClick = onUpgrade, enabled = !isBusy) {
            Text("升级", fontSize = 12.sp)
        }

        if (pkg.isUserInstalled) {
            TextButton(onClick = onUninstall, enabled = !isBusy) {
                Text("卸载", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
