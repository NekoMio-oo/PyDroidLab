package com.pydroidlab.app.ext

import android.view.SurfaceHolder

/**
 * Extension point 1: GUI / Game Rendering Backend (Reserved for v2.0 pygame / kivy / tkinter support).
 * Allows attaching an Android SurfaceHolder or SDL2/OpenGL surface to a running Python process.
 */
interface GuiBackendPlugin {
    val backendId: String
    val displayName: String
    fun isSupportedForScript(code: String): Boolean
    fun attachSurface(surfaceHolder: SurfaceHolder, width: Int, height: Int)
    fun detachSurface()
}

/**
 * Extension point 2: Interactive Notebook Kernel (Reserved for v2.0 Jupyter `.ipynb` support).
 * Supports cell-based stateful execution and rich MIME output (text/html, image/png).
 */
data class NotebookCellOutput(
    val mimeType: String,
    val data: String,
    val isError: Boolean = false
)

interface NotebookKernelPlugin {
    val kernelName: String
    suspend fun executeCell(cellId: String, code: String): List<NotebookCellOutput>
    suspend fun restartKernel()
}

/**
 * Extension point 3: AI Inline Code Completion Provider (Reserved for v2.0 LLM completion).
 * Hooks into `PythonSoraLanguage.requireAutoComplete` to merge cloud/on-device AI suggestions.
 */
data class AiCompletionCandidate(
    val label: String,
    val insertText: String,
    val detail: String,
    val score: Float = 1.0f
)

interface AiCompletionPlugin {
    val providerName: String
    val isEnabled: Boolean
    suspend fun completeCode(
        prefixContext: String,
        suffixContext: String,
        line: Int,
        column: Int
    ): List<AiCompletionCandidate>
}

/**
 * Registry holding optional runtime plugins.
 */
object PluginRegistry {
    val guiBackends = mutableListOf<GuiBackendPlugin>()
    val notebookKernels = mutableListOf<NotebookKernelPlugin>()
    val aiCompletionPlugins = mutableListOf<AiCompletionPlugin>()
}
