package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.data.AppSettings
import com.pydroidlab.app.data.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onTerminalFullScreenChanged: (Boolean) -> Unit,
    onEditorFontSizeChanged: (Float) -> Unit,
    onTerminalFontSizeChanged: (Float) -> Unit,
    onTabWidthChanged: (Int) -> Unit,
    onWordWrapChanged: (Boolean) -> Unit,
    onShowLineNumbersChanged: (Boolean) -> Unit,
    onAutoSaveOnRunChanged: (Boolean) -> Unit,
    onCustomPipIndexChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "编辑器与运行环境设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Theme & Terminal Display Mode Card (Compact, equal-width buttons that never clip or stretch vertically)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "外观主题 (Theme)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // 3 equal-width buttons: 跟随系统 | 浅色模式 | 深色模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        SegmentedOptionButton(
                            label = mode.label,
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChanged(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "运行终端显示方式 (Terminal Mode)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // 2 equal-width buttons: 全屏终端 (默认) | 底部分屏抽屉
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentedOptionButton(
                        label = "全屏终端 (默认)",
                        selected = settings.terminalFullScreen,
                        onClick = { onTerminalFullScreenChanged(true) },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedOptionButton(
                        label = "底部分屏半屏",
                        selected = !settings.terminalFullScreen,
                        onClick = { onTerminalFullScreenChanged(false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Font Size & Tab Width Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "排版与缩进 (Typography & Indentation)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Editor Font Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("代码编辑器字体大小", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${settings.editorFontSizeSp.roundToInt()} sp",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.editorFontSizeSp,
                        onValueChange = { onEditorFontSizeChanged(it.roundToInt().toFloat()) },
                        valueRange = 10f..28f,
                        steps = 17
                    )
                }

                // Terminal Font Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("终端控制台字体大小", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${settings.terminalFontSizeSp.roundToInt()} sp",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.terminalFontSizeSp,
                        onValueChange = { onTerminalFontSizeChanged(it.roundToInt().toFloat()) },
                        valueRange = 10f..24f,
                        steps = 13
                    )
                }

                // Tab Width (Placed cleanly on its own row with 3 equal-width buttons so 8 空格 is never squished!)
                Text(
                    text = "Tab 缩进宽度 (Spaces)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 4, 8).forEach { width ->
                        SegmentedOptionButton(
                            label = "$width 空格",
                            selected = settings.tabWidth == width,
                            onClick = { onTabWidthChanged(width) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Editor Behavior Toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "编辑器与运行选项",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SettingToggleRow(
                    title = "显示行号 (Line Numbers)",
                    subtitle = "在代码编辑器左侧固定显示行号栏",
                    checked = settings.showLineNumbers,
                    onCheckedChange = onShowLineNumbersChanged
                )

                HorizontalDivider()

                SettingToggleRow(
                    title = "自动折行 (Word Wrap)",
                    subtitle = "长代码行超出屏幕宽度时自动换行显示",
                    checked = settings.wordWrap,
                    onCheckedChange = onWordWrapChanged
                )

                HorizontalDivider()

                SettingToggleRow(
                    title = "运行前自动保存文件",
                    subtitle = "点击运行按钮时自动将缓冲区写入当前 .py 文件",
                    checked = settings.autoSaveOnRun,
                    onCheckedChange = onAutoSaveOnRunChanged
                )
            }
        }

        // 4. Custom PyPI Mirror Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "pip 镜像源配置 (可选)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "留空默认使用官方 PyPI + Chaquopy Android 预编译源；国内网络环境可填入清华源 https://pypi.tuna.tsinghua.edu.cn/simple",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = settings.customPipIndexUrl,
                    onValueChange = onCustomPipIndexChanged,
                    placeholder = { Text("https://pypi.tuna.tsinghua.edu.cn/simple") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SegmentedOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
