package com.pydroidlab.app.python

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.pydroidlab.app.core.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StackFrame(val file: String, val line: Int, val function: String)
data class Variable(val name: String, val value: String)

data class DebugState(
    val paused: Boolean = false,
    val file: String = "",
    val line: Int = 0,
    val stack: List<StackFrame> = emptyList(),
    val locals: List<Variable> = emptyList(),
    val exception: String? = null,
    val finished: Boolean = false
)

class DebuggerController {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var dbg: PyObject? = null
    private var job: Job? = null

    private val _state = MutableStateFlow(DebugState(finished = true))
    val state: StateFlow<DebugState> = _state

    val breakpoints = MutableStateFlow<Set<Pair<String, Int>>>(emptySet())

    val running: Boolean get() = job != null

    fun toggleBreakpoint(file: String, line: Int) {
        val key = file to line
        val cur = breakpoints.value
        breakpoints.value = if (cur.contains(key)) cur - key else cur + key
    }

    fun start(path: String, argv: List<String> = emptyList(), cwd: String? = null) {
        stop()
        HostBridge.resetStdin()
        _state.value = DebugState()
        job = scope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("pdl_debugger")
                val d = module.callAttr("make_debugger")
                dbg = d
                // Apply breakpoints.
                for ((f, l) in breakpoints.value) {
                    try { d.callAttr("add_breakpoint", f, l) } catch (_: Throwable) {}
                }
                d.callAttr("start", path, argv.toTypedArray(), cwd)
            } catch (t: Throwable) {
                EventBus.emitStderr("Debugger error: ${t.message}\n")
            } finally {
                _state.value = _state.value.copy(finished = true, paused = false)
                job = null
                dbg = null
            }
        }
        // Collect pause notifications.
        scope.launch {
            EventBus.debugPause.collect { p -> refreshState(p.file, p.line) }
        }
        scope.launch {
            EventBus.debugFinished.collect {
                _state.value = _state.value.copy(finished = true, paused = false)
            }
        }
    }

    private fun refreshState(file: String, line: Int) {
        val d = dbg ?: return
        try {
            // PyObject.asDict() was removed in modern Chaquopy; the supported
            // name is asMap(), returning Map<PyObject, PyObject>.
            val s = d.callAttr("get_state").asMap()
            val stackObj = s[com.chaquo.python.PyObject.fromJava("stack")]?.asList() ?: emptyList()
            val stack = stackObj.map { fr ->
                val fd = fr.asMap()
                StackFrame(
                    file = fd[com.chaquo.python.PyObject.fromJava("file")].toString(),
                    line = fd[com.chaquo.python.PyObject.fromJava("line")].toString().toIntOrNull() ?: 0,
                    function = fd[com.chaquo.python.PyObject.fromJava("func")].toString()
                )
            }
            val localsObj = s[com.chaquo.python.PyObject.fromJava("locals")]?.asMap()
                ?: emptyMap<com.chaquo.python.PyObject, com.chaquo.python.PyObject>()
            val locals = localsObj.entries.map { (k, v) ->
                Variable(k.toString(), v.toString())
            }.sortedBy { it.name }
            _state.value = DebugState(
                paused = true, file = file, line = line,
                stack = stack, locals = locals,
                exception = s[com.chaquo.python.PyObject.fromJava("exception")]?.toString(),
                finished = false
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(paused = true, file = file, line = line)
        }
    }

    private fun cmd(name: String) {
        scope.launch(Dispatchers.IO) {
            try { dbg?.callAttr(name) } catch (_: Throwable) {}
        }
    }

    fun step() = cmd("cmd_step")
    fun next() = cmd("cmd_next")
    fun stepOut() = cmd("cmd_return")
    fun resume() = cmd("cmd_continue")

    fun stop() {
        try { dbg?.callAttr("cmd_continue") } catch (_: Throwable) {}
        HostBridge.cancelStdin()
        job?.cancel()
        job = null
        dbg = null
        _state.value = _state.value.copy(finished = true, paused = false)
    }
}
