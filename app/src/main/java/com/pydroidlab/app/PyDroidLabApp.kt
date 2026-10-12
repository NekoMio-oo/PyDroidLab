package com.pydroidlab.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.pydroidlab.app.core.ServiceLocator
import com.pydroidlab.app.runtime.ConsoleRuntimeProvider
import com.pydroidlab.app.runtime.RuntimeRegistry

class PyDroidLabApp : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Start the embedded CPython interpreter (Chaquopy). Idempotent.
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        services = ServiceLocator(this)
        registerRuntimes()
    }

    /**
     * 注册脚本运行时。
     *
     * v1.0 只有「内置控制台」一种；pygame / kivy 等屏幕型运行时属于 v2，
     * 实现 `ScriptRuntime` 后在这里注册即可被 UI 发现，无需改动调用方。
     */
    private fun registerRuntimes() {
        RuntimeRegistry.register(
            ConsoleRuntimeProvider(
                launcher = { config, onFinished -> services.pythonRunner.run(config, onFinished) },
                interrupter = { services.pythonRunner.interrupt() }
            )
        )
    }

    companion object {
        lateinit var instance: PyDroidLabApp
            private set
    }
}
