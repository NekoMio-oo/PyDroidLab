package com.pydroidlab.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun FilePreviewDialog(
    file: File,
    sqliteInspector: ((String) -> String)? = null,
    onDismiss: () -> Unit
) {
    val ext = file.extension.lowercase()
    val isImage = ext in setOf("png", "jpg", "jpeg", "webp", "bmp")
    val isSqlite = ext in setOf("db", "sqlite", "sqlite3")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(540.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        val subtitle = when {
                            isImage -> "图像预览 (支持双指缩放与拖拽) • ${file.length()} B"
                            isSqlite -> "SQLite 数据库可视化预览 • ${file.length()} B"
                            else -> "结构化数据预览 • ${file.length()} B"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭预览")
                    }
                }

                HorizontalDivider()

                if (isImage) {
                    ZoomableImagePreview(file = file, modifier = Modifier.weight(1f))
                } else {
                    FormattedTextPreview(
                        file = file,
                        ext = ext,
                        isSqlite = isSqlite,
                        sqliteInspector = sqliteInspector,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePreview(
    file: File,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(file.absolutePath, file.lastModified()) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1120))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
            Surface(
                color = Color(0x991E293B),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.height(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重置缩放",
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            Text(
                text = "无法解码图像文件: ${file.name}",
                color = Color(0xFFF87171)
            )
        }
    }
}

@Composable
private fun FormattedTextPreview(
    file: File,
    ext: String,
    isSqlite: Boolean,
    sqliteInspector: ((String) -> String)?,
    modifier: Modifier = Modifier
) {
    val formatted = remember(file.absolutePath, file.lastModified()) {
        runCatching {
            if (isSqlite && sqliteInspector != null) {
                sqliteInspector(file.absolutePath)
            } else {
                val raw = file.readText(Charsets.UTF_8)
                if (ext == "json") {
                    val trimmed = raw.trim()
                    if (trimmed.startsWith("{")) {
                        JSONObject(trimmed).toString(2)
                    } else if (trimmed.startsWith("[")) {
                        JSONArray(trimmed).toString(2)
                    } else {
                        raw
                    }
                } else {
                    raw
                }
            }
        }.getOrElse { "读取失败: ${it.localizedMessage}" }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = formatted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
