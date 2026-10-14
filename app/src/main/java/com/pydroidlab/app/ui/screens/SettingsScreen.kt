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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.data.AppSettings
import com.pydroidlab.app.data.ThemeMode
import com.pydroidlab.app.editor.IdeColorTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onIdeColorThemeChanged: (IdeColorTheme) -> Unit,
    onTerminalFullScreenChanged: (Boolean) -> Unit,
    onTwoRowSymbolBarChanged: (Boolean) -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 外观与配色
        SectionTitle("界面外观")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                MinimalSegmentButton(
                    label = mode.label,
                    selected = settings.themeMode == mode,
                    onClick = { onThemeModeChanged(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionTitle("编辑器配色方案")

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IdeColorTheme.entries.chunked(3).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowThemes.forEach { ideTheme ->
                        MinimalSegmentButton(
                            label = ideTheme.displayName,
                            selected = settings.ideColorThemeId == ideTheme.id,
                            onClick = { onIdeColorThemeChanged(ideTheme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // 2. 终端模式与缩进
        SectionTitle("终端布局")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MinimalSegmentButton(
                label = "全屏终端",
                selected = settings.terminalFullScreen,
                onClick = { onTerminalFullScreenChanged(true) },
                modifier = Modifier.weight(1f)
            )
            MinimalSegmentButton(
                label = "底部分屏",
                selected = !settings.terminalFullScreen,
                onClick = { onTerminalFullScreenChanged(false) },
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitle("缩进空格数")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(2, 4, 8).forEach { width ->
                MinimalSegmentButton(
                    label = "$width 空格",
                    selected = settings.tabWidth == width,
                    onClick = { onTabWidthChanged(width) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // 3. 字号调节
        SectionTitle("字号大小")

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("编辑器字号", fontSize = 14.sp)
                Text(
                    "${settings.editorFontSizeSp.roundToInt()} sp",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.editorFontSizeSp,
                onValueChange = { onEditorFontSizeChanged(it.roundToInt().toFloat()) },
                valueRange = 10f..28f,
                steps = 17
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("终端字号", fontSize = 14.sp)
                Text(
                    "${settings.terminalFontSizeSp.roundToInt()} sp",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.terminalFontSizeSp,
                onValueChange = { onTerminalFontSizeChanged(it.roundToInt().toFloat()) },
                valueRange = 10f..24f,
                steps = 13
            )
        }

        HorizontalDivider(thickness = 0.5.dp)

        // 4. 行为开关
        SectionTitle("编辑器行为")

        MinimalToggleRow(
            title = "显示行号",
            checked = settings.showLineNumbers,
            onCheckedChange = onShowLineNumbersChanged
        )

        MinimalToggleRow(
            title = "自动折行",
            checked = settings.wordWrap,
            onCheckedChange = onWordWrapChanged
        )

        MinimalToggleRow(
            title = "运行前自动保存",
            checked = settings.autoSaveOnRun,
            onCheckedChange = onAutoSaveOnRunChanged
        )

        HorizontalDivider(thickness = 0.5.dp)

        // 5. 镜像源
        SectionTitle("pip 镜像源")

        OutlinedTextField(
            value = settings.customPipIndexUrl,
            onValueChange = onCustomPipIndexChanged,
            placeholder = { Text("默认官方源，可填 https://pypi.tuna.tsinghua.edu.cn/simple", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun MinimalSegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun MinimalToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
