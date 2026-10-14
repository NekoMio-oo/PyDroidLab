package com.pydroidlab.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a single styled segment in a terminal line, supporting ANSI SGR colors and attributes.
 */
data class TerminalSegment(
    val text: String,
    val fgColorIndex: Int = DEFAULT_FG,
    val bgColorIndex: Int = DEFAULT_BG,
    val isBold: Boolean = false,
    val isError: Boolean = false
) {
    companion object {
        const val DEFAULT_FG = -1
        const val DEFAULT_BG = -1
    }
}

/**
 * Represents a line in the terminal scrollback buffer.
 */
data class TerminalLine(
    val id: Long,
    val segments: List<TerminalSegment>,
    val isComplete: Boolean = false
) {
    val plainText: String
        get() = segments.joinToString(separator = "") { it.text }
}

/**
 * Signal delivered to the blocking stdin queue when Python invokes `input()` or `sys.stdin.readline()`.
 */
sealed class StdinSignal {
    data class InputLine(val line: String) : StdinSignal()
    data object EOF : StdinSignal()
    data object Interrupt : StdinSignal()
}

/**
 * Immutable snapshot of the terminal state exposed to Compose UI.
 */
data class TerminalSnapshot(
    val lines: List<TerminalLine> = emptyList(),
    val isRunning: Boolean = false,
    val isAwaitingInput: Boolean = false,
    val currentInputPrompt: String = "",
    val exitCode: Int? = null,
    val executionTimeMs: Long? = null
)

/**
 * Interactive VT100/ANSI-compatible Terminal Emulator designed for embedded Python execution.
 *
 * Responsibilities:
 * 1. Parses stdout and stderr streams in real time, handling '\r', '\n', '\b', and ANSI CSI sequences
 *    (such as `\033[2J` clear screen, `\033[31m`–`\033[37m` SGR colors, `\033[0m` reset).
 * 2. Maintains a bounded scrollback ring buffer (default 2,000 lines) for smooth Compose rendering.
 * 3. Provides a thread-safe blocking `stdin` line discipline (`requestLineInput`) so Python's `input()`
 *    and `sys.stdin.readline()` suspend the worker thread until the user submits a line or interrupts execution.
 */
class TerminalEmulator(
    private val maxScrollbackLines: Int = 2000
) {
    private val lock = Any()
    private val stdinQueue = LinkedBlockingQueue<StdinSignal>()
    private val awaitingInput = AtomicBoolean(false)

    private var nextLineId = 1L
    private val completedLines = ArrayDeque<TerminalLine>()
    private val activeSegments = mutableListOf<TerminalSegment>()
    private val currentTextBuffer = StringBuilder()

    // Current SGR state
    private var currentFg = TerminalSegment.DEFAULT_FG
    private var currentBg = TerminalSegment.DEFAULT_BG
    private var currentBold = false
    private var currentIsError = false

    private val _snapshot = MutableStateFlow(TerminalSnapshot())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    /**
     * Resets the terminal screen and clears any pending stdin signals before a new script run.
     */
    fun resetForNewSession(scriptTitle: String) {
        synchronized(lock) {
            stdinQueue.clear()
            awaitingInput.set(false)
            completedLines.clear()
            activeSegments.clear()
            currentTextBuffer.clear()
            currentFg = 36 // Cyan banner
            currentBold = true
            currentIsError = false
            appendRawTextInternal("▶ Running $scriptTitle\n")
            resetSgr()
            _snapshot.value = TerminalSnapshot(
                lines = buildVisibleLinesLocked(),
                isRunning = true,
                isAwaitingInput = false,
                currentInputPrompt = "",
                exitCode = null,
                executionTimeMs = null
            )
        }
    }

    /**
     * Clears the terminal buffer manually.
     */
    fun clearScreen() {
        synchronized(lock) {
            completedLines.clear()
            activeSegments.clear()
            currentTextBuffer.clear()
            publishSnapshotLocked()
        }
    }

    /**
     * Appends standard output text (supports ANSI escape codes and partial lines without trailing newline).
     */
    fun writeStdout(text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            currentIsError = false
            processAnsiStreamLocked(text)
            publishSnapshotLocked()
        }
    }

    /**
     * Appends standard error text (highlighted in red/error style).
     */
    fun writeStderr(text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            val prevFg = currentFg
            val prevErr = currentIsError
            currentFg = 31 // ANSI Red
            currentIsError = true
            processAnsiStreamLocked(text)
            currentFg = prevFg
            currentIsError = prevErr
            publishSnapshotLocked()
        }
    }

    /**
     * Appends a system informational message (e.g., process finished or interrupted).
     */
    fun writeSystemMessage(message: String, isError: Boolean = false) {
        synchronized(lock) {
            flushActiveSegmentLocked()
            if (activeSegments.isNotEmpty()) {
                commitCurrentLineLocked()
            }
            val color = if (isError) 31 else 32 // Red or Green
            completedLines.addLast(
                TerminalLine(
                    id = nextLineId++,
                    segments = listOf(
                        TerminalSegment(
                            text = message,
                            fgColorIndex = color,
                            isBold = true,
                            isError = isError
                        )
                    ),
                    isComplete = true
                )
            )
            trimScrollbackLocked()
            publishSnapshotLocked()
        }
    }

    /**
     * Marks the current session as finished.
     */
    fun markSessionFinished(exitCode: Int, elapsedMs: Long) {
        synchronized(lock) {
            awaitingInput.set(false)
            flushActiveSegmentLocked()
            if (activeSegments.isNotEmpty()) {
                commitCurrentLineLocked()
            }
            val statusMsg = if (exitCode == 0) {
                "\n[Process finished with exit code 0 in ${elapsedMs}ms]"
            } else {
                "\n[Process terminated with exit code $exitCode after ${elapsedMs}ms]"
            }
            val color = if (exitCode == 0) 32 else 33
            completedLines.addLast(
                TerminalLine(
                    id = nextLineId++,
                    segments = listOf(
                        TerminalSegment(
                            text = statusMsg.trimStart('\n'),
                            fgColorIndex = color,
                            isBold = true,
                            isError = exitCode != 0
                        )
                    ),
                    isComplete = true
                )
            )
            trimScrollbackLocked()
            _snapshot.value = _snapshot.value.copy(
                lines = buildVisibleLinesLocked(),
                isRunning = false,
                isAwaitingInput = false,
                currentInputPrompt = "",
                exitCode = exitCode,
                executionTimeMs = elapsedMs
            )
        }
    }

    /**
     * Called synchronously from the Chaquopy Python worker thread when `input(prompt)` is executed.
     * Flushes the prompt to stdout, marks the terminal as awaiting input, and blocks until the user
     * enters a line in the console UI or interrupts execution.
     *
     * @return The user-submitted line (without trailing newline), or `null` if interrupted/EOF.
     */
    fun requestLineInput(prompt: String): String? {
        synchronized(lock) {
            if (prompt.isNotEmpty()) {
                currentIsError = false
                processAnsiStreamLocked(prompt)
            }
            flushActiveSegmentLocked()
            awaitingInput.set(true)
            _snapshot.value = _snapshot.value.copy(
                lines = buildVisibleLinesLocked(),
                isAwaitingInput = true,
                currentInputPrompt = prompt
            )
        }

        return try {
            when (val signal = stdinQueue.take()) {
                is StdinSignal.InputLine -> {
                    synchronized(lock) {
                        awaitingInput.set(false)
                        // Echo the user's input in bold green/cyan and commit the line
                        val prevFg = currentFg
                        val prevBold = currentBold
                        currentFg = 36
                        currentBold = true
                        appendRawTextInternal(signal.line + "\n")
                        currentFg = prevFg
                        currentBold = prevBold
                        _snapshot.value = _snapshot.value.copy(
                            lines = buildVisibleLinesLocked(),
                            isAwaitingInput = false,
                            currentInputPrompt = ""
                        )
                    }
                    signal.line
                }
                is StdinSignal.EOF, is StdinSignal.Interrupt -> {
                    synchronized(lock) {
                        awaitingInput.set(false)
                        _snapshot.value = _snapshot.value.copy(
                            isAwaitingInput = false,
                            currentInputPrompt = ""
                        )
                    }
                    null
                }
            }
        } catch (e: InterruptedException) {
            synchronized(lock) {
                awaitingInput.set(false)
                _snapshot.value = _snapshot.value.copy(
                    isAwaitingInput = false,
                    currentInputPrompt = ""
                )
            }
            null
        }
    }

    /**
     * Called from the UI thread when the user submits a line into the interactive terminal prompt.
     */
    fun submitUserInput(line: String) {
        stdinQueue.offer(StdinSignal.InputLine(line))
    }

    /**
     * Unblocks any pending `input()` call immediately with an Interrupt signal.
     */
    fun sendInterruptSignal() {
        stdinQueue.offer(StdinSignal.Interrupt)
    }

    // --- Internal ANSI VT100 stream parser ---

    private fun processAnsiStreamLocked(input: String) {
        var i = 0
        val len = input.length
        while (i < len) {
            val ch = input[i]
            if (ch == '\u001B' && i + 1 < len && input[i + 1] == '[') {
                flushActiveSegmentLocked()
                i += 2
                val paramsStart = i
                while (i < len && input[i] in '0'..'9' || (i < len && input[i] == ';')) {
                    i++
                }
                val params = input.substring(paramsStart, i)
                if (i < len) {
                    val cmd = input[i]
                    i++
                    handleCsiCommandLocked(cmd, params)
                }
            } else {
                processCharLocked(ch)
                i++
            }
        }
        flushActiveSegmentLocked()
    }

    private fun handleCsiCommandLocked(cmd: Char, params: String) {
        when (cmd) {
            'm' -> {
                val codes = if (params.isEmpty()) listOf(0) else params.split(';').mapNotNull { it.toIntOrNull() }
                for (code in codes) {
                    when (code) {
                        0 -> resetSgr()
                        1 -> currentBold = true
                        22 -> currentBold = false
                        in 30..37 -> currentFg = code
                        39 -> currentFg = TerminalSegment.DEFAULT_FG
                        in 40..47 -> currentBg = code
                        49 -> currentBg = TerminalSegment.DEFAULT_BG
                        in 90..97 -> currentFg = code
                    }
                }
            }
            'J' -> {
                if (params == "2" || params == "3") {
                    completedLines.clear()
                    activeSegments.clear()
                    currentTextBuffer.clear()
                }
            }
            'K' -> {
                activeSegments.clear()
                currentTextBuffer.clear()
            }
        }
    }

    private fun processCharLocked(ch: Char) {
        when (ch) {
            '\r' -> {
                // Carriage return: if not followed immediately by \n, allow overwriting line
            }
            '\n' -> {
                flushActiveSegmentLocked()
                commitCurrentLineLocked()
            }
            '\b' -> {
                if (currentTextBuffer.isNotEmpty()) {
                    currentTextBuffer.deleteCharAt(currentTextBuffer.length - 1)
                }
            }
            else -> {
                currentTextBuffer.append(ch)
            }
        }
    }

    private fun appendRawTextInternal(text: String) {
        for (ch in text) {
            processCharLocked(ch)
        }
        flushActiveSegmentLocked()
    }

    private fun flushActiveSegmentLocked() {
        if (currentTextBuffer.isNotEmpty()) {
            activeSegments.add(
                TerminalSegment(
                    text = currentTextBuffer.toString(),
                    fgColorIndex = currentFg,
                    bgColorIndex = currentBg,
                    isBold = currentBold,
                    isError = currentIsError
                )
            )
            currentTextBuffer.clear()
        }
    }

    private fun commitCurrentLineLocked() {
        val segmentsCopy = if (activeSegments.isEmpty()) {
            listOf(TerminalSegment(""))
        } else {
            activeSegments.toList()
        }
        activeSegments.clear()
        completedLines.addLast(
            TerminalLine(
                id = nextLineId++,
                segments = segmentsCopy,
                isComplete = true
            )
        )
        trimScrollbackLocked()
    }

    private fun trimScrollbackLocked() {
        while (completedLines.size > maxScrollbackLines) {
            completedLines.removeFirst()
        }
    }

    private fun buildVisibleLinesLocked(): List<TerminalLine> {
        val result = ArrayList<TerminalLine>(completedLines.size + 1)
        result.addAll(completedLines)
        if (activeSegments.isNotEmpty()) {
            result.add(
                TerminalLine(
                    id = nextLineId,
                    segments = activeSegments.toList(),
                    isComplete = false
                )
            )
        }
        return result
    }

    private fun publishSnapshotLocked() {
        _snapshot.value = _snapshot.value.copy(
            lines = buildVisibleLinesLocked()
        )
    }

    private fun resetSgr() {
        currentFg = TerminalSegment.DEFAULT_FG
        currentBg = TerminalSegment.DEFAULT_BG
        currentBold = false
    }
}
