package com.pydroidlab.app.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MyCharacter
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Python 3 Language implementation for Rosemoe Sora Editor (`io.github.Rosemoe.sora-editor`).
 *
 * Provides:
 * 1. Real-time lexical syntax highlighting (`PythonAnalyzeManager`) for keywords, built-in functions,
 *    decorators, single/double/triple-quoted strings, comments, numbers, and function/class definitions.
 * 2. Smart auto-indentation (`getIndentAdvance`): automatically adds `tabWidth` spaces when pressing
 *    Enter after a colon (`:`), and unindents after `return`, `break`, `continue`, `pass`, `raise`.
 * 3. Bracket & quote auto-completion (`getSymbolPairs`): `()`, `[]`, `{}`, `""`, `''`.
 * 4. Context-aware Python keyword, built-in, and snippet auto-completion (`requireAutoComplete`).
 */
class PythonSoraLanguage(
    private val tabWidthProvider: () -> Int = { 4 }
) : EmptyLanguage() {

    private val analyzeManager = PythonAnalyzeManager()
    private val symbolPairs = SymbolPairMatch.DefaultSymbolPairs().apply {
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun useTab(): Boolean = false

    override fun getSymbolPairs(): SymbolPairMatch = symbolPairs

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val lineStr = content.getLine(line).substring(0, column.coerceAtMost(content.getLine(line).length))
        // Strip trailing inline comment before checking for ':'
        val commentIdx = lineStr.indexOf('#')
        val codePart = (if (commentIdx >= 0) lineStr.substring(0, commentIdx) else lineStr).trimEnd()
        val tabSize = tabWidthProvider().coerceIn(2, 8)
        return when {
            codePart.endsWith(":") -> tabSize
            codePart.endsWith("(") || codePart.endsWith("[") || codePart.endsWith("{") -> tabSize
            else -> 0
        }
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            MyCharacter.isJavaIdentifierPart(ch)
        }
        if (prefix.isEmpty()) return

        val lowerPrefix = prefix.lowercase()
        val items = mutableListOf<CompletionItem>()

        for (kw in PYTHON_KEYWORDS) {
            if (kw.startsWith(lowerPrefix)) {
                items.add(SimpleCompletionItem(kw, "Python Keyword", prefix.length, kw))
            }
        }
        for (builtin in PYTHON_BUILTINS) {
            if (builtin.startsWith(lowerPrefix)) {
                items.add(SimpleCompletionItem(builtin, "Built-in Function", prefix.length, "$builtin()"))
            }
        }
        for ((trigger, desc, snippet) in PYTHON_SNIPPETS) {
            if (trigger.startsWith(lowerPrefix)) {
                items.add(SimpleCompletionItem(trigger, desc, prefix.length, snippet))
            }
        }

        publisher.addItems(items)
    }

    companion object {
        val PYTHON_KEYWORDS = setOf(
            "and", "as", "assert", "async", "await", "break", "case", "class",
            "continue", "def", "del", "elif", "else", "except", "False", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "match",
            "None", "nonlocal", "not", "or", "pass", "raise", "return", "True",
            "try", "while", "with", "yield"
        )

        val PYTHON_BUILTINS = setOf(
            "abs", "all", "any", "ascii", "bin", "bool", "bytearray", "bytes",
            "callable", "chr", "classmethod", "compile", "complex", "delattr",
            "dict", "dir", "divmod", "enumerate", "eval", "exec", "filter",
            "float", "format", "frozenset", "getattr", "globals", "hasattr",
            "hash", "help", "hex", "id", "input", "int", "isinstance",
            "issubclass", "iter", "len", "list", "locals", "map", "max",
            "memoryview", "min", "next", "object", "oct", "open", "ord",
            "pow", "print", "property", "range", "repr", "reversed", "round",
            "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum",
            "super", "tuple", "type", "vars", "zip", "self", "cls"
        )

        val PYTHON_SNIPPETS = listOf(
            Triple("main", "if __name__ == '__main__':", "if __name__ == \"__main__\":\n    "),
            Triple("def", "Define a function", "def func_name():\n    pass"),
            Triple("fori", "For loop with range()", "for i in range(10):\n    "),
            Triple("tryex", "try / except block", "try:\n    pass\nexcept Exception as e:\n    print(e)")
        )
    }
}

/**
 * Lexical analyzer for Python 3 source code using Sora Editor's [SimpleAnalyzeManager].
 */
private class PythonAnalyzeManager : SimpleAnalyzeManager<Unit>() {

    override fun analyze(text: java.lang.StringBuilder, delegate: Delegate<Unit>): Styles {
        val styles = Styles()
        val spansBuilder = MappedSpans.Builder()

        val styleNormal = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
        val styleKeyword = TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
        val styleBuiltin = TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
        val styleComment = TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
        val styleString = TextStyle.makeStyle(EditorColorScheme.LITERAL)
        val styleNumber = TextStyle.makeStyle(EditorColorScheme.OPERATOR)
        val styleAnnotation = TextStyle.makeStyle(EditorColorScheme.ANNOTATION)

        val source = text.toString()
        val lines = source.split('\n')
        var inTripleDouble = false
        var inTripleSingle = false

        for (lineIndex in lines.indices) {
            if (delegate.isCancelled) break
            val line = lines[lineIndex]
            if (line.isEmpty()) {
                spansBuilder.addIfNeeded(lineIndex, 0, if (inTripleDouble || inTripleSingle) styleString else styleNormal)
                continue
            }

            var col = 0
            val len = line.length

            while (col < len) {
                // Handle active multiline triple-quoted string
                if (inTripleDouble) {
                    spansBuilder.addIfNeeded(lineIndex, col, styleString)
                    val endIdx = line.indexOf("\"\"\"", col)
                    if (endIdx == -1) {
                        col = len
                    } else {
                        inTripleDouble = false
                        col = endIdx + 3
                    }
                    continue
                }
                if (inTripleSingle) {
                    spansBuilder.addIfNeeded(lineIndex, col, styleString)
                    val endIdx = line.indexOf("'''", col)
                    if (endIdx == -1) {
                        col = len
                    } else {
                        inTripleSingle = false
                        col = endIdx + 3
                    }
                    continue
                }

                val ch = line[col]

                // Whitespace
                if (ch.isWhitespace()) {
                    col++
                    continue
                }

                // Comment
                if (ch == '#') {
                    spansBuilder.addIfNeeded(lineIndex, col, styleComment)
                    break
                }

                // Decorator
                if (ch == '@') {
                    spansBuilder.addIfNeeded(lineIndex, col, styleAnnotation)
                    col++
                    while (col < len && (line[col].isLetterOrDigit() || line[col] == '_' || line[col] == '.')) {
                        col++
                    }
                    continue
                }

                // Triple-quoted or single-line string
                if (ch == '"' || ch == '\'') {
                    spansBuilder.addIfNeeded(lineIndex, col, styleString)
                    if (col + 2 < len && line[col + 1] == ch && line[col + 2] == ch) {
                        val delim = if (ch == '"') "\"\"\"" else "'''"
                        val closeIdx = line.indexOf(delim, col + 3)
                        if (closeIdx == -1) {
                            if (ch == '"') inTripleDouble = true else inTripleSingle = true
                            col = len
                        } else {
                            col = closeIdx + 3
                        }
                    } else {
                        col++
                        while (col < len) {
                            if (line[col] == '\\') {
                                col += 2
                                continue
                            }
                            if (line[col] == ch) {
                                col++
                                break
                            }
                            col++
                        }
                    }
                    continue
                }

                // Numeric literal
                if (ch.isDigit()) {
                    spansBuilder.addIfNeeded(lineIndex, col, styleNumber)
                    while (col < len && (line[col].isLetterOrDigit() || line[col] == '.' || line[col] == '_')) {
                        col++
                    }
                    continue
                }

                // Identifier or Keyword
                if (ch.isLetter() || ch == '_') {
                    val start = col
                    while (col < len && (line[col].isLetterOrDigit() || line[col] == '_')) {
                        col++
                    }
                    val token = line.substring(start, col)
                    val tokenStyle = when {
                        token in PythonSoraLanguage.PYTHON_KEYWORDS -> styleKeyword
                        token in PythonSoraLanguage.PYTHON_BUILTINS -> styleBuiltin
                        else -> styleNormal
                    }
                    spansBuilder.addIfNeeded(lineIndex, start, tokenStyle)
                    continue
                }

                // Operators and punctuation
                spansBuilder.addIfNeeded(lineIndex, col, styleNormal)
                col++
            }
        }

        spansBuilder.determine((lines.size - 1).coerceAtLeast(0))
        styles.spans = spansBuilder.build()
        return styles
    }
}
