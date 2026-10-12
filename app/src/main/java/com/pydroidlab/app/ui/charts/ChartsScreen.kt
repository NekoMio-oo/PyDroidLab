package com.pydroidlab.app.ui.charts

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.core.EventBus
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.theme.MonoFontFamily
import java.io.File

/**
 * ⑥ 图表面板
 *
 * `pdl_runtime.install_matplotlib_bridge()` 会把 matplotlib 强制切到 Agg 后端，
 * 并把 `plt.show()` 重定向成「保存 PNG + 通知本面板」，因此这里的图片
 * 都是脚本运行时真实渲染出来的，可以直接保存。
 */
@Composable
fun ChartsScreen(vm: AppViewModel) {
    val charts by EventBus.chartList.collectAsState()
    var index by remember { mutableStateOf(0) }

    val safeIndex = if (charts.isEmpty()) 0 else index.coerceIn(0, charts.size - 1)
    val current = charts.getOrNull(safeIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    enabled = charts.size > 1,
                    onClick = { if (safeIndex > 0) index = safeIndex - 1 }
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("上一张")
                }
                Text(
                    text = if (charts.isEmpty()) "0 / 0" else "${safeIndex + 1} / ${charts.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(64.dp)
                )
                TextButton(
                    enabled = charts.size > 1,
                    onClick = { if (safeIndex < charts.size - 1) index = safeIndex + 1 }
                ) {
                    Text("下一张")
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = current != null,
                    onClick = {
                        val c = current ?: return@TextButton
                        val dir = vm.currentDir ?: vm.currentProject?.dir ?: return@TextButton
                        val name = c.title.replace(Regex("[^A-Za-z0-9_\\u4e00-\\u9fa5\\-]"), "_")
                        var target = File(dir, "$name.png")
                        var n = 1
                        while (target.exists()) target = File(dir, "${name}_${n++}.png")
                        vm.saveChart(c, target)
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存 PNG")
                }
                TextButton(
                    enabled = charts.isNotEmpty(),
                    onClick = {
                        EventBus.clearCharts()
                        index = 0
                    }
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清空")
                }
            }
        }

        HorizontalDivider()

        if (current == null) {
            // 与编辑器空状态同理：Column 必须拿到确定宽度，否则示例代码这种
            // 长行的固有宽度会超出屏幕，居中对齐失效后整块贴左溢出。
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "还没有图表",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "装好 matplotlib 后，脚本里调用 plt.show() 就会出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(16.dp))
                    // 示例代码用等宽字体 + 横向滚动，避免长行被折行或裁切。
                    Text(
                        text = "import matplotlib.pyplot as plt\n" +
                            "import numpy as np\n\n" +
                            "x = np.linspace(0, 6.28, 200)\n" +
                            "plt.plot(x, np.sin(x))\n" +
                            "plt.show()",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        } else {
            Text(
                text = current.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            ChartImage(path = current.path, modifier = Modifier.weight(1f))
            Text(
                text = current.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChartImage(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Text("图片读取失败：$path", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
