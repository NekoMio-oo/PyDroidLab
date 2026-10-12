package com.pydroidlab.app.ui.editor

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import java.util.regex.Pattern

/**
 * Self-contained Python editing support for sora-editor:
 *  - incremental regex syntax highlighting via [SimpleAnalyzeManager]
 *  - identifier completion (keywords + builtins + identifiers found in the file
 *    + host-provided names + `模块.成员` 点号补全)
 *  - Python-aware auto-indent (trailing ':' adds one indent level)
 *  - bracket & quote auto-pairing
 *  - 断点行在行号栏渲染红色圆点（[LineSideIcon]）
 *
 * Dependency-free: only the core sora-editor artifact is required.
 */
class PythonLanguage(
    private val identifierProvider: () -> List<String> = { emptyList() },
    /** 0-based 行号集合，用于在 gutter 画断点圆点。 */
    private val breakpointProvider: () -> Set<Int> = { emptySet() },
    /** 给定模块名，返回其公开成员（用于 `math.` 这类前缀补全）。 */
    private val memberProvider: (String) -> List<String> = { emptyList() }
) : EmptyLanguage() {

    private val autoComplete = IdentifierAutoComplete(PYTHON_KEYWORDS.toTypedArray())

    override fun getAnalyzeManager(): AnalyzeManager = PythonAnalyzeManager(breakpointProvider)

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // 允许把 '.' 也算进前缀，这样 "math.pi" 能作为整体传进来
        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            Character.isJavaIdentifierPart(ch) || ch == '.'
        }
        if (prefix.isEmpty()) return

        val pl = prefix.length

        if (prefix.contains('.')) {
            // 点号补全：只查模块成员，不再混入关键字
            val module = prefix.substringBeforeLast('.')
            val partial = prefix.substringAfterLast('.')
            if (module.isNotEmpty()) {
                memberProvider(module)
                    .filter { it.startsWith(partial) }
                    .take(MAX_MEMBER_ITEMS)
                    .forEach { member ->
                        val text = "$module.$member"
                        publisher.addItem(
                            SimpleCompletionItem(text, "模块成员", pl, text)
                                .kind(CompletionItemKind.Property)
                        )
                    }
            }
            publisher.updateList()
            return
        }

        // Identifiers seen in the document + host-provided ones.
        autoComplete.requireAutoComplete(content, position, prefix, publisher, null)

        PYTHON_BUILTINS.filter { it.startsWith(prefix) }.forEach {
            publisher.addItem(SimpleCompletionItem(it, "Builtin", pl, it).kind(CompletionItemKind.Function))
        }
        identifierProvider().filter { it.startsWith(prefix) }.forEach {
            publisher.addItem(SimpleCompletionItem(it, "Identifier", pl, it).kind(CompletionItemKind.Variable))
        }
        SNIPPETS.filterKeys { it.startsWith(prefix) }.forEach { (trigger, code) ->
            publisher.addItem(SimpleCompletionItem(trigger, "Snippet", pl, code).kind(CompletionItemKind.Snippet))
        }
        publisher.updateList()
    }

    override fun getNewlineHandlers(): Array<NewlineHandler> = arrayOf(IndentHandler)

    override fun getSymbolPairs(): SymbolPairMatch = PythonSymbolPairs

    /** Keep current indent; after ':' add one tab-width level. */
    private object IndentHandler : NewlineHandler {
        override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean = true

        override fun handleNewline(
            text: Content, position: CharPosition, style: Styles?, tabSize: Int
        ): NewlineHandleResult {
            val lineText = text.getLine(position.line).toString()
            val before = lineText.substring(0, position.column.coerceAtMost(lineText.length))
            val baseIndent = before.takeWhile { it == ' ' || it == '\t' }
            val extra = if (before.trimEnd().endsWith(":")) " ".repeat(tabSize) else ""
            return NewlineHandleResult("\n" + baseIndent + extra, 0)
        }
    }

    // ------------------------------------------------------------------------
    // Syntax highlighting
    // ------------------------------------------------------------------------
    private class PythonAnalyzeManager(
        private val breakpointProvider: () -> Set<Int>
    ) : SimpleAnalyzeManager<Void>() {

        override fun analyze(text: StringBuilder, delegate: Delegate<Void>): Styles {
            val builder = MappedSpans.Builder()
            val source = text.toString()
            var line = 0
            var lineStart = 0
            var i = 0
            val n = source.length

            fun addSpan(l: Int, col: Int, style: Long) {
                builder.add(l, SpanFactory.obtain(col, style))
            }

            while (i <= n) {
                val atEnd = i == n
                val c = if (atEnd) '\n' else source[i]

                if (atEnd || c == '\n') {
                    // flush the line
                    processLine(source, lineStart, i, line, ::addSpan)
                    builder.addNormalIfNull()
                    builder.determine(line)
                    line++
                    lineStart = i + 1
                }
                i++
            }
            val styles = Styles(builder.build())

            // 断点圆点：注入 LineSideIcon，由 EditorRenderer.drawSideIcons 绘制
            val lineCount = line
            try {
                for (bp in breakpointProvider()) {
                    if (bp in 0 until lineCount) {
                        styles.addLineStyle(LineSideIcon(bp, breakpointDot()))
                    }
                }
            } catch (_: Throwable) {
                // 断点绘制失败不能影响语法高亮
            }

            styles.finishBuilding()
            return styles
        }

        private fun processLine(
            src: String, start: Int, end: Int, line: Int,
            addSpan: (Int, Int, Long) -> Unit
        ) {
            if (end <= start) return
            val lineText = src.substring(start, end)
            var idx = 0
            val L = lineText.length
            while (idx < L) {
                val ch = lineText[idx]
                val col = idx
                when {
                    // comments
                    ch == '#' -> {
                        addSpan(line, col, COMMENT)
                        idx = L
                    }
                    // strings
                    ch == '"' || ch == '\'' -> {
                        val endIdx = scanString(lineText, idx)
                        addSpan(line, col, STRING)
                        idx = endIdx
                    }
                    // numbers
                    ch.isDigit() -> {
                        var j = idx
                        while (j < L && (lineText[j].isDigit() || lineText[j] == '.' || lineText[j] == '_')) j++
                        addSpan(line, col, NUMBER)
                        idx = j
                    }
                    // identifiers / keywords
                    ch.isLetter() || ch == '_' -> {
                        var j = idx
                        while (j < L && (lineText[j].isLetterOrDigit() || lineText[j] == '_')) j++
                        val word = lineText.substring(idx, j)
                        val style = when {
                            KEYWORD_SET.contains(word) -> KEYWORD
                            BUILTIN_SET.contains(word) -> BUILTIN
                            j < L && lineText[j] == '(' -> FUNCTION
                            else -> 0L
                        }
                        if (style != 0L) addSpan(line, col, style)
                        idx = j
                    }
                    ch in "+-*/%=<>!&|^~@:." -> {
                        addSpan(line, col, OPERATOR)
                        idx++
                    }
                    else -> idx++
                }
            }
        }

        private fun scanString(s: String, from: Int): Int {
            val quote = s[from]
            // triple-quoted
            if (from + 2 < s.length && s[from + 1] == quote && s[from + 2] == quote) {
                var j = from + 3
                while (j + 2 < s.length) {
                    if (s[j] == quote && s[j + 1] == quote && s[j + 2] == quote) return j + 3
                    j++
                }
                return s.length
            }
            var j = from + 1
            while (j < s.length) {
                if (s[j] == '\\') { j += 2; continue }
                if (s[j] == quote) return j + 1
                j++
            }
            return s.length
        }

        companion object {
            // Foreground slots are EditorColorScheme slot ids so colors adapt to
            // whatever theme (Darcula / GitHub / dark / light) is active.
            // TextStyle.makeStyle 的完整签名是
            //   (int foregroundColorId, int backgroundColorId, boolean bold,
            //    boolean italic, boolean strikethrough / + underline)
            // 没有 4 参数的重载，因此这里显式补上最后一个 flag。
            private val KEYWORD = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.KEYWORD, 0, true, false, false)
            private val STRING = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LITERAL)
            private val NUMBER = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LITERAL)
            private val COMMENT = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMMENT, 0, false, true, false)
            private val BUILTIN = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.IDENTIFIER_NAME)
            private val FUNCTION = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.FUNCTION_NAME)
            private val OPERATOR = TextStyle.makeStyle(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.OPERATOR)

            private val KEYWORD_SET = PYTHON_KEYWORDS.toSet()
            private val BUILTIN_SET = PYTHON_BUILTINS.toSet()
        }
    }

    companion object {
        private const val MAX_MEMBER_ITEMS = 200

        /** gutter 上的断点圆点（纯代码绘制，不依赖 drawable 资源）。 */
        private val dotDrawable: Drawable by lazy {
            ShapeDrawable(OvalShape()).apply {
                paint.color = Color.parseColor("#E5484D")
            }
        }

        fun breakpointDot(): Drawable = dotDrawable

        val PYTHON_KEYWORDS = listOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "match", "case"
        )

        val PYTHON_BUILTINS = listOf(
            "print", "input", "len", "range", "str", "int", "float", "bool",
            "list", "dict", "set", "tuple", "open", "type", "isinstance",
            "enumerate", "zip", "map", "filter", "sum", "min", "max", "abs",
            "sorted", "reversed", "any", "all", "repr", "id", "hasattr",
            "getattr", "setattr", "super", "Exception", "ValueError"
        )

        private val SNIPPETS = linkedMapOf(
            "for" to "for item in items:\n    ",
            "def" to "def name(args):\n    ",
            "class" to "class Name:\n    def __init__(self):\n        ",
            "try" to "try:\n    \nexcept Exception as e:\n    print(e)",
            "ifmain" to "if __name__ == \"__main__\":\n    main()",
            "while" to "while cond:\n    ",
            "with" to "with open(path) as f:\n    ",
            "if" to "if cond:\n    ",
            "elif" to "elif cond:\n    "
        )
    }
}

/** Bracket & quote auto-pairing. */
object PythonSymbolPairs : SymbolPairMatch() {
    init {
        putPair('(', SymbolPair("(", ")"))
        putPair('[', SymbolPair("[", "]"))
        putPair('{', SymbolPair("{", "}"))
        putPair('"', SymbolPair("\"", "\""))
        putPair('\'', SymbolPair("'", "'"))
    }
}
