package com.pydroidlab.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.theme.MonoFontFamily

/**
 * ⑨ 教程区
 *
 * 内容来自 `src/main/python/tutorials/`（Markdown 讲义 + 可运行示例）
 * 与 `src/main/python/snippets/`（可复用代码片段），由 `pdl_resources` 读取。
 *
 * 每一课都配了一个 `NN_xxx_example.py`，可以一键「导入到项目并打开」。
 */
@Composable
fun LearnScreen(vm: AppViewModel) {
    var tab by remember { mutableStateOf(0) }
    var tutorials by remember { mutableStateOf<List<String>>(emptyList()) }
    var snippets by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf(CATEGORY_ALL) }

    LaunchedEffect(reload) {
        tutorials = vm.listResources(TUTORIALS).filter { it.endsWith(".md") }
        snippets = vm.listResources(SNIPPETS).filter { it.endsWith(".py") }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0; selected = null }, text = { Text("教程（${tutorials.size}）") })
            Tab(selected = tab == 1, onClick = { tab = 1; selected = null }, text = { Text("代码片段（${snippets.size}）") })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (selected != null) {
                TextButton(onClick = { selected = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("返回列表")
                }
            }
            TextButton(onClick = { reload++ }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("重新加载")
            }
        }
        HorizontalDivider()

        val current = selected
        if (current == null) {
            CategoryRow(current = category, onPick = { category = it })
            val shown = when (tab) {
                0 -> tutorials.filter { category == CATEGORY_ALL || categoryOf(it) == category }
                else -> snippets.filter { category == CATEGORY_ALL || categoryOf(it) == category }
            }
            if (shown.isEmpty()) {
                Text(
                    text = "该分类下暂无内容，试试切换到「全部」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (tab == 0) {
                TutorialList(shown) { selected = it }
            } else {
                SnippetList(shown) { selected = it }
            }
        } else {
            if (tab == 0) {
                TutorialDetail(vm, current)
            } else {
                SnippetDetail(vm, current)
            }
        }
    }
}

/** 分类筛选栏：全部 / 起步 / 基础 / 进阶 / 数据与网络。 */
@Composable
private fun CategoryRow(current: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CATEGORIES.forEach { c ->
            val picked = c == current
            Surface(
                color = if (picked) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = c,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onPick(c) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TutorialList(files: List<String>, onOpen: (String) -> Unit) {
    if (files.isEmpty()) {
        Text(
            text = "没有读到教程文件。\n若刚改过工程，请点「重新加载」；\n" +
                "内容位于 src/main/python/tutorials/。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(files) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(file) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileNumber(file),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(36.dp)
                )
                Text(
                    text = fileLabel(file),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text("›", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SnippetList(files: List<String>, onOpen: (String) -> Unit) {
    if (files.isEmpty()) {
        Text(
            text = "没有读到代码片段。内容位于 src/main/python/snippets/。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(files) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(file) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileLabel(file),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text("›", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TutorialDetail(vm: AppViewModel, file: String) {
    var content by remember(file) { mutableStateOf("") }
    var example by remember(file) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val exampleName = "${file.removeSuffix(".md")}$EXAMPLE_SUFFIX"

    LaunchedEffect(file) {
        content = vm.readResource(TUTORIALS, file)
        example = vm.readResource(TUTORIALS, exampleName)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileLabel(file),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(
                    enabled = content.isNotBlank(),
                    onClick = {
                        clipboard.setText(AnnotatedString(content))
                        vm.postNotice("讲义已复制")
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("复制")
                }
                TextButton(
                    enabled = example.isNotBlank(),
                    onClick = { vm.materializeExample("${fileNumber(file)}_示例", example) }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入")
                }
                TextButton(
                    enabled = example.isNotBlank(),
                    onClick = {
                        // 一键：写进项目 → 打开 → 直接在控制台跑起来
                        vm.materializeExample("${fileNumber(file)}_示例", example, runAfter = true)
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("运行")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (content.isBlank()) {
                Text(
                    text = "讲义内容为空或读取失败。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownText(content)
            }

            if (example.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "本课可运行示例",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                CodeBlock(example)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点右上角「导入示例」会把它写进当前项目并打开，然后回「编辑器」点「运行」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SnippetDetail(vm: AppViewModel, file: String) {
    var content by remember(file) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(file) { content = vm.readResource(SNIPPETS, file) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileLabel(file),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(
                    enabled = content.isNotBlank(),
                    onClick = {
                        clipboard.setText(AnnotatedString(content))
                        vm.postNotice("片段已复制到剪贴板")
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("复制")
                }
                TextButton(
                    enabled = content.isNotBlank(),
                    onClick = { vm.materializeExample(file.removeSuffix(".py"), content) }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("导入为文件")
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (content.isBlank()) {
                Text("片段为空或读取失败。", style = MaterialTheme.typography.bodySmall)
            } else {
                CodeBlock(content)
            }
        }
    }
}

// ---------------------------------------------------------------- Markdown --

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val code = StringBuilder()
    var inCode = false
    val para = StringBuilder()

    fun flushPara() {
        if (para.isNotBlank()) {
            blocks.add(MdBlock.Para(para.toString().trim()))
            para.clear()
        }
    }

    src.lines().forEach { raw ->
        val line = raw
        when {
            line.trimStart().startsWith("```") -> {
                if (inCode) {
                    blocks.add(MdBlock.Code(code.toString().trimEnd('\n')))
                    code.clear()
                    inCode = false
                } else {
                    flushPara()
                    inCode = true
                }
            }

            inCode -> code.append(line).append('\n')

            line.startsWith("### ") -> {
                flushPara(); blocks.add(MdBlock.Heading(3, line.removePrefix("### ")))
            }

            line.startsWith("## ") -> {
                flushPara(); blocks.add(MdBlock.Heading(2, line.removePrefix("## ")))
            }

            line.startsWith("# ") -> {
                flushPara(); blocks.add(MdBlock.Heading(1, line.removePrefix("# ")))
            }

            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flushPara(); blocks.add(MdBlock.Bullet(line.trimStart().substring(2)))
            }

            line.isBlank() -> flushPara()

            else -> para.append(line).append('\n')
        }
    }
    if (inCode && code.isNotEmpty()) blocks.add(MdBlock.Code(code.toString().trimEnd('\n')))
    flushPara()
    return blocks
}

@Composable
private fun MarkdownText(md: String) {
    val blocks = remember(md) { parseMarkdown(md) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    text = b.text,
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (b.level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (b.level == 1) 0.dp else 10.dp)
                )

                is MdBlock.Bullet -> Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(b.text, style = MaterialTheme.typography.bodyMedium)
                }

                is MdBlock.Para -> Text(b.text, style = MaterialTheme.typography.bodyMedium)

                is MdBlock.Code -> CodeBlock(b.text)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(10.dp)
        )
    }
}

// ------------------------------------------------------------------- 工具 --

private const val TUTORIALS = "tutorials"
private const val SNIPPETS = "snippets"
private const val EXAMPLE_SUFFIX = "_example.py"

private const val CATEGORY_ALL = "全部"

/** 示例库分类：按文件名的数字前缀分组。 */
private val CATEGORIES = listOf(CATEGORY_ALL, "起步", "基础", "进阶", "数据与网络")

private fun categoryOf(fileName: String): String {
    val n = fileName.takeWhile { it.isDigit() }.toIntOrNull() ?: return "进阶"
    return when {
        n <= 2 -> "起步"
        n <= 5 -> "基础"
        n <= 7 -> "进阶"
        else -> "数据与网络"
    }
}

private fun fileNumber(name: String): String =
    name.takeWhile { it.isDigit() }.ifEmpty { "·" }

private fun fileLabel(name: String): String {
    val base = name.substringBeforeLast('.').removeSuffix(EXAMPLE_SUFFIX)
    val withoutNumber = base.dropWhile { it.isDigit() }.trimStart('_', '-')
    return withoutNumber.ifEmpty { base }
}
