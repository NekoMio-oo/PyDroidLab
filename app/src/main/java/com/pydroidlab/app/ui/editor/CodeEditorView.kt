package com.pydroidlab.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorMotionEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.pydroidlab.app.ui.settings.EditorSettings

/**
 * Compose wrapper around sora-editor's [CodeEditor].
 *
 * The CodeEditor instance is created once per tab content and retained, so
 * cursor position / scroll / undo history survive recomposition & rotation.
 *
 * 事件接线（原来声明了但从未触发的回调，本次补齐）：
 *  - [SelectionChangeEvent] → [onCursorChanged]，状态栏与标签光标位置实时同步
 *  - [ClickEvent] 命中行号栏 → [onBreakpointToggle]，实现「点行号打断点」
 *  - [LongPressEvent] → 取词 → [onLongPressWord]，长按查看 docstring
 *
 * 断点行会在行号栏画出红色圆点：断点集合通过 [breakpointProvider] 传给
 * [PythonLanguage]，注入 `LineSideIcon` 后由 sora 的 `drawSideIcons` 绘制。
 */
@Composable
fun CodeEditorView(
    tab: EditorTab,
    settings: EditorSettings,
    colorScheme: EditorColorScheme,
    identifierProvider: () -> List<String>,
    onDirtyChanged: (Boolean) -> Unit,
    onCursorChanged: (line: Int, col: Int) -> Unit,
    onBreakpointToggle: (line: Int) -> Unit,
    onLongPressWord: (String) -> Unit,
    memberProvider: (String) -> List<String> = { emptyList() },
    breakpointProvider: () -> Set<Int> = { emptySet() },
    modifier: Modifier = Modifier,
    editorRef: (CodeEditor?) -> Unit = {}
) {
    // 记录上一次的断点集合：变化时重跑语法分析以刷新 gutter 圆点。
    // 用普通数组做容器（不是 Compose 状态），避免在 update 里写状态导致多余重组。
    val lastBreakpoints = remember(tab.id) { arrayOf<Set<Int>>(emptySet()) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                setEditorLanguage(PythonLanguage(identifierProvider, breakpointProvider, memberProvider))
                setColorScheme(colorScheme)
                applySettings(settings)
                setText(tab.content)

                // Propagate edits -> dirty flag.
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    if (!tab.isDirty) {
                        tab.isDirty = true
                        onDirtyChanged(true)
                    }
                }

                // Bug 7.1：输入右括号/引号时若光标右侧已是同一字符 → skip over。
                // sora 0.23.6 的 SymbolPairEx 只能决定"配不配对"，做不到"跳过"，
                // 所以在这里监听插入事件：把刚插入的字符删掉、光标右移一位，
                // 效果与"跨过已有括号"一致。SymbolBar 与键盘输入都走 commitText，
                // 因此两条路径自动共享这一行为，不会双重处理。
                val skipGuard = arrayOf(false)
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    if (skipGuard[0]) return@subscribeEvent
                    try {
                        if (event.action != ContentChangeEvent.ACTION_INSERT) {
                            return@subscribeEvent
                        }
                        val inserted = event.changedText.toString()
                        if (inserted.length != 1 || inserted[0] !in SKIP_OVER_CLOSERS) {
                            return@subscribeEvent
                        }
                        val start = event.changeStart
                        val content = text
                        val rightIdx = start.index + 1
                        // 注意：Content.charAt 有 (index) 与 (line, column) 两个
                        // 重载，Kotlin 显式调用会撞上重载歧义；用 get 约定（[]）
                        // 走 CharSequence.get(int) 则没有歧义。
                        val rightChar: Char =
                            if (rightIdx < content.length) content[rightIdx] else '\u0000'
                        if (rightChar != inserted[0]) {
                            return@subscribeEvent
                        }
                        skipGuard[0] = true
                        try {
                            content.delete(start.index, rightIdx)
                            setSelection(start.line, start.column + 1)
                        } finally {
                            skipGuard[0] = false
                        }
                    } catch (_: Throwable) {
                    }
                }

                // 光标 / 选区变化 -> 同步到 tab 与 UI 状态栏
                subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                    try {
                        val cur = cursor
                        tab.cursorLine = cur.leftLine
                        tab.cursorColumn = cur.leftColumn
                        onCursorChanged(cur.leftLine, cur.leftColumn)
                    } catch (_: Throwable) {
                    }
                }

                // 行号栏单击 -> 切换断点
                subscribeEvent(ClickEvent::class.java) { event, _ ->
                    if (event.motionRegion == EditorMotionEvent.REGION_LINE_NUMBER) {
                        try {
                            onBreakpointToggle(event.line)
                        } catch (_: Throwable) {
                        }
                    }
                }

                // 长按取词 -> 交给宿主查询 docstring
                subscribeEvent(LongPressEvent::class.java) { event, _ ->
                    val word = wordAt(event.line, event.column)
                    if (word.isNotEmpty()) onLongPressWord(word)
                }

                // Restore cursor.
                post {
                    try {
                        setSelection(tab.cursorLine, tab.cursorColumn)
                    } catch (_: Throwable) {
                    }
                }
                editorRef(this)
            }
        },
        update = { editor ->
            // Keep settings & scheme in sync (theme/size changes).
            editor.applySettings(settings)
            if (editor.colorScheme !== colorScheme) {
                editor.setColorScheme(colorScheme)
            }
            val current = breakpointProvider()
            if (current != lastBreakpoints[0]) {
                lastBreakpoints[0] = current
                try {
                    editor.editorLanguage?.analyzeManager?.rerun()
                } catch (_: Throwable) {
                }
            }
        },
        onRelease = { editor ->
            // Save cursor back into the tab before disposal (rotation/switch).
            try {
                val cur = editor.cursor
                tab.cursorLine = cur.leftLine
                tab.cursorColumn = cur.leftColumn
            } catch (_: Throwable) {
            }
            editorRef(null)
        }
    )
}

/** 输入这些右符号时若光标右侧已是同一字符，则 skip over（Bug 7.1）。 */
private val SKIP_OVER_CLOSERS = charArrayOf(')', ']', '}', '"', '\'')

private fun CodeEditor.applySettings(s: EditorSettings) {
    // NOTE: sora-editor exposes `setTextSize(float)` / `getTextSizePx()` but no
    // `getTextSize()`, so Kotlin cannot synthesise a `textSize` property here.
    setTextSize(s.fontSizeSp)
    tabWidth = s.tabWidth
    // 行高：add = 0（额外像素），mult = 倍数。这是设置页里原本被忽略的一项。
    setLineSpacing(0f, s.lineHeightMultiplier)
    setWordwrap(s.wordWrap)
    isLineNumberEnabled = s.showLineNumbers
    isBlockLineEnabled = true
    typefaceText = android.graphics.Typeface.MONOSPACE
    getComponent(Magnifier::class.java).isEnabled = true
    props.apply {
        symbolPairAutoCompletion = s.autoPair
        autoIndent = s.autoIndent
        // Bug 1/2：中文输入法联想（composing）期间的补全开关，默认保持开启。
        autoCompletionOnComposing = s.imeCompletions
    }
}

/**
 * 取 [line] 行 [column] 列处的标识符。
 *
 * 允许 '.'，因此长按 `math.pi` 会整体选中并交给 Python 的 eval 求文档；
 * 若长按位置落在词尾之后，会向左回退一格，符合直觉。
 */
private fun CodeEditor.wordAt(line: Int, column: Int): String = try {
    val text = text.getLineString(line)
    if (text.isEmpty()) {
        ""
    } else {
        var c = column.coerceIn(0, text.length)
        if (c == text.length || !text[c].isWordChar()) {
            if (c > 0 && text[c - 1].isWordChar()) c--
        }
        if (c >= text.length || !text[c].isWordChar()) {
            ""
        } else {
            var start = c
            var end = c
            while (start > 0 && text[start - 1].isWordChar()) start--
            while (end < text.length && text[end].isWordChar()) end++
            text.substring(start, end)
        }
    }
} catch (_: Throwable) {
    ""
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

/** Customizable quick-symbol toolbar above the soft keyboard. */
@Composable
fun SymbolBar(
    symbols: List<String>,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            symbols.forEach { sym ->
                Text(
                    text = sym,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onInsert(sym) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
