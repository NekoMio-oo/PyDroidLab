package com.pydroidlab.app.python

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wraps the Python-side Repl; persists globals across evaluations. */
class ReplController {

    private var repl: PyObject? = null

    private suspend fun ensure(): PyObject = withContext(Dispatchers.IO) {
        repl ?: Python.getInstance().getModule("pdl_repl").callAttr("make_repl").also { repl = it }
    }

    /** Returns true if the input forms an incomplete block (needs more lines). */
    suspend fun push(line: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ensure().callAttr("push", line).toBoolean()
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun complete(prefix: String): List<String> = withContext(Dispatchers.IO) {
        try {
            ensure().callAttr("complete", prefix).asList().map { it.toString() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    suspend fun docstring(expr: String): String = withContext(Dispatchers.IO) {
        try {
            ensure().callAttr("docstring", expr).toString()
        } catch (t: Throwable) {
            ""
        }
    }

    fun reset() {
        repl = null
    }
}
