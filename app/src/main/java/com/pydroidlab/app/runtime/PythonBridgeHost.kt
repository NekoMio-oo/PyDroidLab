package com.pydroidlab.app.runtime

import androidx.annotation.Keep
import com.pydroidlab.terminal.TerminalEmulator

/**
 * Callback bridge passed directly into Chaquopy's `pydroid_runner.py`.
 * Annotated with `@Keep` so R8/ProGuard preserves all method signatures called from Python.
 */
@Keep
class PythonBridgeHost(
    private val terminal: TerminalEmulator
) {
    @Keep
    fun onStdout(text: String) {
        terminal.writeStdout(text)
    }

    @Keep
    fun onStderr(text: String) {
        terminal.writeStderr(text)
    }

    @Keep
    fun requestInput(prompt: String): String? {
        return terminal.requestLineInput(prompt)
    }
}

/**
 * Callback bridge passed into `pip_manager.py` to stream live pip installation logs.
 */
@Keep
class PipLogBridge(
    private val onLogLine: (String) -> Unit
) {
    @Keep
    fun onLog(text: String) {
        onLogLine(text)
    }
}
