package com.pydroidlab.app.terminal

import jackpal.androidterm.emulatorview.TermSession
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * 一个**不依赖 pty** 的 [TermSession]。
 *
 * [TermSession] 的模型是「模拟器 ↔ 一对输入输出流」：
 *  - `termIn`  ：进程的 stdout，读线程读到后交给 VT100 模拟器渲染；
 *  - `termOut` ：进程的 stdin，写入线程把用户按键写进去。
 *
 * 设备上没有可用的本地 pty（jackpal 的 AAR 未包含 libtermexec），
 * 所以这里用两条管道把两端接到宿主自己实现的命令解释器上。
 *
 * 注意：读写线程是在 `initializeEmulator()` 里启动的，因此必须在
 * `EmulatorView.attachSession()` 之前就把流挂好 —— 构造函数里已经完成。
 */
class PdlTerminalSession(
    private val onInput: (String) -> Unit
) : TermSession(true) {

    private val displayIn = PipedInputStream(PIPE_SIZE)
    private val displayOut = PipedOutputStream(displayIn)

    private val keyIn = PipedInputStream(PIPE_SIZE)
    private val keyOut = PipedOutputStream(keyIn)

    private var keyThread: Thread? = null

    init {
        setTermIn(displayIn)
        setTermOut(keyOut)
    }

    /** 启动按键读取线程；幂等。 */
    fun start() {
        if (keyThread != null) return
        keyThread = Thread({
            val buf = ByteArray(1024)
            try {
                while (true) {
                    val n = keyIn.read(buf)
                    if (n < 0) break
                    if (n > 0) onInput(String(buf, 0, n, Charsets.UTF_8))
                }
            } catch (_: Throwable) {
                // 管道关闭 / 线程退出，正常收尾
            }
        }, "pdl-term-input").apply {
            isDaemon = true
            start()
        }
    }

    /** 把文本渲染到终端屏幕上（自动把 \n 转成 VT100 需要的 \r\n）。 */
    fun emit(text: String) {
        if (text.isEmpty()) return
        val normalized = text.replace("\r\n", "\n").replace("\n", "\r\n")
        val bytes = normalized.toByteArray(Charsets.UTF_8)
        try {
            displayOut.write(bytes)
            displayOut.flush()
        } catch (_: Throwable) {
            // 会话已销毁
        }
    }

    override fun finish() {
        // 父类实现假定 mEmulator 非空，且会关闭两条流；这里做防御式收尾。
        try {
            super.finish()
        } catch (_: Throwable) {
        }
        try {
            keyIn.close()
        } catch (_: Throwable) {
        }
        try {
            displayOut.close()
        } catch (_: Throwable) {
        }
        keyThread = null
    }

    companion object {
        private const val PIPE_SIZE = 64 * 1024
    }
}
