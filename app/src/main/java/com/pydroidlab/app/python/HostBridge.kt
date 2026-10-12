package com.pydroidlab.app.python

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.pydroidlab.app.core.EventBus
import com.pydroidlab.app.core.Chart
import com.pydroidlab.app.core.DebugPause
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Kotlin implementation of the `pdl_host` module imported by the Python
 * bootstrap.  We build a real `types.ModuleType` and attach Java callables;
 * Chaquopy can call Kotlin function objects from Python transparently.
 */
object HostBridge {

    @Volatile
    private var stdinQueue: ArrayBlockingQueue<String?> = ArrayBlockingQueue(8)

    private lateinit var hostDir: File

    fun init(context: Context) {
        hostDir = File(context.filesDir, "pdl").apply { mkdirs() }
        registerHostModule()
    }

    fun resetStdin() = stdinQueue.clear()

    /** Console UI -> Python stdin. */
    fun submitStdin(line: String) = stdinQueue.offer(line).let { }

    /** Interrupt a blocked input(). */
    fun cancelStdin() = stdinQueue.offer(null).let { }

    // ---- pdl_host API (invoked from Python) -------------------------------

    @JvmStatic fun write_stdout(s: String) = EventBus.emitStdout(s)
    @JvmStatic fun write_stderr(s: String) = EventBus.emitStderr(s)

    @JvmStatic
    fun read_stdin(): String? = try {
        stdinQueue.poll(3600, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        null
    }

    @JvmStatic
    fun on_chart(path: String, title: String) = EventBus.emitChart(Chart(path, title))

    @JvmStatic fun host_dir(): String = hostDir.absolutePath

    @JvmStatic
    fun debug_paused(file: String, line: Int) = EventBus.emitDebugPause(DebugPause(file, line))

    @JvmStatic fun debug_finished() = EventBus.emitDebugFinished()

    // ---- registration -----------------------------------------------------

    private var registered = false

    @Synchronized
    private fun registerHostModule() {
        if (registered) return
        val py = Python.getInstance()
        val types = py.getModule("types")
        val mod = types.callAttr("ModuleType", "pdl_host")

        // Attach Kotlin function references; Chaquopy exposes them as callables.
        // NOTE: PyObject extends AbstractMap<String, PyObject>, so Kotlin's
        // `mod["k"] = v` would bind to put(String, PyObject) and fail to compile.
        // Use the explicit put(String, Object) overload instead.
        mod.put("write_stdout", java.util.function.Consumer<String> { write_stdout(it) })
        mod.put("write_stderr", java.util.function.Consumer<String> { write_stderr(it) })
        mod.put("read_stdin", java.util.function.Supplier<String?> { read_stdin() })
        mod.put("host_dir", java.util.function.Supplier<String> { host_dir() })
        mod.put("on_chart", BiConsumerLike { a, b -> on_chart(a, b) })
        mod.put("debug_paused", BiConsumerLike { a, b -> debug_paused(a, b.toInt()) })
        mod.put("debug_finished", java.util.function.Supplier<Unit> { debug_finished() })

        py.getModule("sys")["modules"]?.callAttr("__setitem__", "pdl_host", mod)
        registered = true
    }

    /** Chaquopy maps a Java object with a call-style SAM to a Python callable.
     *  For two-arg callbacks we expose a BiConsumer. */
    private fun BiConsumerLike(f: (String, String) -> Unit) =
        java.util.function.BiConsumer<String, String> { a, b -> f(a, b) }
}
