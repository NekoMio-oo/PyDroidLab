package com.pydroidlab.app.runtime

import android.content.Context
import com.pydroidlab.app.python.RunConfig
import java.io.File

/**
 * 脚本运行时的抽象。
 *
 * v1.0 只实现一种：[ConsoleRuntimeProvider]（内嵌 CPython + 控制台桥）。
 * 这里保留接口是为了 v2 能接入**非控制台**的运行方式而不用改动 UI 与
 * ViewModel：
 *
 * | 目标 | 需要的新 Provider | 关键差异 |
 * | --- | --- | --- |
 * | pygame | `SdlRuntimeProvider` | 需要 SDLSurface 级别的 SurfaceView，输出不是 stdout 而是屏幕 |
 * | kivy | `KivyRuntimeProvider` | 需要额外的 bootstrap 与 OpenGL ES 上下文 |
 * | Jupyter | 不属于运行时，属于前端（v1.0 非目标） | |
 *
 * 接入方式：实现本接口 → 用 [RuntimeRegistry.register] 注册 →
 * 在编辑器工具栏的运行按钮上选择 Provider（UI 后续只需读
 * [RuntimeRegistry.all] 渲染一个下拉即可，无需改动现有代码）。
 *
 * **注意**：本文件只是**扩展点骨架**。pygame / kivy 属于原始需求里
 * 明确划到 v2 的内容，v1.0 不实现，所以这里只有接口与文档，没有具体实现。
 */
interface ScriptRuntime {

    /** 稳定标识，用于持久化用户选择。 */
    val id: String

    /** 界面上显示的名字。 */
    val displayName: String

    /**
     * 当前设备 / 当前安装状态下是否可用。
     * 例如 pygame Provider 可以检查 `import pygame` 是否成功。
     */
    fun isAvailable(context: Context): Boolean

    /**
     * 启动一次运行。实现方负责自己的一切：
     * 控制台桥接、Surface 创建、异常上报等。
     *
     * @param onFinished 运行结束时回调（退出码）
     */
    fun start(context: Context, config: RunConfig, onFinished: (Int) -> Unit)

    /** 请求中断。与 `PythonRunner.interrupt()` 语义一致：尽力而为。 */
    fun stop()
}

/**
 * v1.0 的唯一实现：把运行委托给宿主已有的 `PythonRunner`。
 *
 * 它不是直接持有 `PythonRunner`，而是通过回调注入，
 * 这样 `runtime` 包就不需要依赖 `ServiceLocator`，保持单向依赖。
 */
class ConsoleRuntimeProvider(
    private val launcher: (RunConfig, (Int) -> Unit) -> Unit,
    private val interrupter: () -> Unit
) : ScriptRuntime {

    override val id: String = ID
    override val displayName: String = "内置控制台"

    override fun isAvailable(context: Context): Boolean = true

    override fun start(context: Context, config: RunConfig, onFinished: (Int) -> Unit) {
        launcher(config, onFinished)
    }

    override fun stop() = interrupter()

    companion object {
        const val ID = "console"
    }
}

/** 运行时的注册表（v1.0 只有一项）。 */
object RuntimeRegistry {

    private val providers = linkedMapOf<String, ScriptRuntime>()

    @Volatile
    private var defaultId: String = ConsoleRuntimeProvider.ID

    fun register(provider: ScriptRuntime) {
        providers[provider.id] = provider
    }

    fun unregister(id: String) {
        providers.remove(id)
    }

    fun all(): List<ScriptRuntime> = providers.values.toList()

    fun byId(id: String): ScriptRuntime? = providers[id]

    fun default(): ScriptRuntime? = providers[defaultId] ?: providers.values.firstOrNull()

    fun setDefault(id: String) {
        if (providers.containsKey(id)) defaultId = id
    }

    /** 供 UI 展示：脚本文件后缀 → 建议运行时（v1.0 恒为内置）。 */
    fun suggestFor(script: File): String = ConsoleRuntimeProvider.ID
}
