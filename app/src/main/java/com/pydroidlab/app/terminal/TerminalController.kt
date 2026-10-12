package com.pydroidlab.app.terminal

import com.pydroidlab.app.shell.PyShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 内置终端的行编辑器 + 调度器。
 *
 * EmulatorView 把用户按键通过 [PdlTerminalSession] 的管道送过来，
 * 这里负责：回显、退格、历史上下翻、Ctrl-C / Ctrl-L，以及把整行交给 [PyShell]。
 */
class TerminalController(
    private val shell: PyShell,
    private val scope: CoroutineScope
) {

    val session = PdlTerminalSession { data -> onInput(data) }

    private val buffer = StringBuilder()
    private val history = mutableListOf<String>()
    private var historyIndex = 0
    private var busy = false
    /** 0=普通 1=收到 ESC 2=收到 ESC [ */
    private var escapeState = 0

    fun start() {
        session.start()
        session.emit(shell.banner())
        showPrompt()
    }

    fun shutdown() {
        try {
            session.finish()
        } catch (_: Throwable) {
        }
    }

    private fun showPrompt() {
        session.emit(shell.prompt())
    }

    private fun onInput(data: String) {
        for (ch in data) {
            when {
                escapeState == 1 -> escapeState = if (ch == '[') 2 else 0
                escapeState == 2 -> {
                    escapeState = 0
                    handleCsi(ch)
                }

                ch == '\u001B' -> escapeState = 1
                ch == '\r' || ch == '\n' -> submit()
                ch == '\u007F' || ch == '\b' -> backspace()
                ch == '\u0003' -> cancelLine()
                ch == '\u000C' -> clearScreen()
                ch == '\u0015' -> clearLine()          // Ctrl-U
                ch == '\t' -> insert("    ")           // Tab → 4 空格
                ch.code >= 32 -> insert(ch.toString())
                else -> Unit
            }
        }
    }

    private fun handleCsi(final: Char) {
        when (final) {
            'A' -> historyMove(-1)
            'B' -> historyMove(1)
            else -> Unit // C/D（左右移动）等暂不实现
        }
    }

    private fun historyMove(delta: Int) {
        if (history.isEmpty()) return
        val next = (historyIndex + delta).coerceIn(0, history.size)
        if (next == historyIndex) return
        repeat(buffer.length) { session.emit("\b \b") }
        buffer.clear()
        historyIndex = next
        if (historyIndex < history.size) {
            val h = history[historyIndex]
            buffer.append(h)
            session.emit(h)
        }
    }

    private fun insert(text: String) {
        if (busy) return
        buffer.append(text)
        session.emit(text)
    }

    private fun backspace() {
        if (busy || buffer.isEmpty()) return
        buffer.deleteCharAt(buffer.length - 1)
        session.emit("\b \b")
    }

    private fun clearLine() {
        if (busy) return
        repeat(buffer.length) { session.emit("\b \b") }
        buffer.clear()
    }

    private fun clearScreen() {
        session.emit(PyShell.CLEAR_SCREEN)
        session.emit(shell.banner())
        showPrompt()
        session.emit(buffer.toString())
    }

    private fun cancelLine() {
        session.emit("^C\r\n")
        buffer.clear()
        if (!busy) showPrompt()
    }

    private fun submit() {
        if (busy) return
        val line = buffer.toString()
        buffer.clear()
        session.emit("\r\n")
        if (line.isBlank()) {
            showPrompt()
            return
        }
        history.add(line)
        historyIndex = history.size
        busy = true
        scope.launch {
            val out = shell.execute(line)
            session.emit(out)
            busy = false
            showPrompt()
        }
    }
}
