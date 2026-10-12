package com.pydroidlab.app.python

import android.content.Context
import com.chaquo.python.Python
import com.pydroidlab.app.core.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RunConfig(
    val path: String,
    val argv: List<String> = emptyList(),
    val cwd: String? = null,
    val env: Map<String, String> = emptyMap(),
    val stdinPreset: String = ""
)

/** Runs user scripts through the embedded interpreter on a background thread. */
class PythonRunner(context: Context) {

    @Volatile
    private var runJob: Job? = null

    @Volatile
    var running = false
        private set

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        HostBridge.init(context)
    }

    fun run(config: RunConfig, onDone: (Int) -> Unit = {}) {
        if (running) return
        HostBridge.resetStdin()
        running = true
        val startedAt = System.currentTimeMillis()
        EventBus.emitStdout("▶ Running ${config.path}\n")
        runJob = scope.launch(Dispatchers.IO) {
            var code = -1
            try {
                code = try {
                    val py = Python.getInstance()
                    val runner = py.getModule("pdl_runner")
                    runner.callAttr(
                        "run_script",
                        config.path,
                        config.argv.toTypedArray(),
                        config.cwd,
                        config.env,
                        config.stdinPreset.ifEmpty { null }
                    ).toInt()
                } catch (t: Throwable) {
                    EventBus.emitStderr("Runner error: ${t.message}\n")
                    -1
                }
            } finally {
                running = false
                // Bug 4：结束行带退出码与耗时（方法签名不变，信息通过控制台文本传出）。
                val secs = (System.currentTimeMillis() - startedAt) / 1000.0
                EventBus.emitStdout("\n■ 进程结束 · 退出码 $code · 用时 %.1fs\n".format(secs))
            }
            onDone(code)
        }
    }

    /** Send SIGINT-equivalent: interrupt the running thread and unblock stdin. */
    fun interrupt() {
        HostBridge.cancelStdin()
        // 关键补充（方法签名未变）：
        //   取消协程只能停掉 Kotlin 侧，解除 stdin 阻塞只对卡在 input() 时有效，
        //   两者都**无法**中断已经在执行的 Python 字节码，即 `while True: pass`
        //   停不下来。这里额外调用 pdl_interrupt.request()，它通过 CPython 的
        //   PyThreadState_SetAsyncExc 往执行线程的下一个字节码边界注入
        //   KeyboardInterrupt，从而真正打断 CPU 死循环。
        //   放到独立线程里执行，避免在 GIL 争用时卡住 UI；
        //   注入不可用（ctypes 缺失等）时静默降级为原有行为。
        Thread {
            try {
                Python.getInstance().getModule("pdl_interrupt").callAttr("request")
            } catch (_: Throwable) {
            }
        }.start()
        runJob?.let { job ->
            job.cancel()
        }
        running = false
        EventBus.emitStderr("\n[Interrupt requested]\n")
    }
}
