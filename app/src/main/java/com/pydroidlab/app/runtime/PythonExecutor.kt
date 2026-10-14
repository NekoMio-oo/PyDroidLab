package com.pydroidlab.app.runtime

import com.chaquo.python.Python
import com.pydroidlab.terminal.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class InstalledPackage(
    val name: String,
    val version: String,
    val summary: String,
    val isUserInstalled: Boolean,
    val location: String
)

data class PipOperationResult(
    val success: Boolean,
    val message: String
)

data class AstSyntaxDiagnostic(
    val line: Int,
    val column: Int,
    val endColumn: Int,
    val message: String
)

data class AstOutlineSymbol(
    val name: String,
    val kind: String,
    val line: Int,
    val column: Int
)

data class AstAnalysisResult(
    val diagnostics: List<AstSyntaxDiagnostic> = emptyList(),
    val outline: List<AstOutlineSymbol> = emptyList()
)

data class RuntimeContextInfo(
    val pythonVersion: String = "3.10",
    val fullVersion: String = "",
    val platform: String = "",
    val cwd: String = "",
    val workspaceDir: String = "",
    val userSitePackages: String = "",
    val sysPath: List<String> = emptyList(),
    val loadedModulesCount: Int = 0
)

/**
 * Manages background Python script execution, interactive REPL, AST static analysis,
 * runtime context diagnostics, and pip operations via Chaquopy.
 */
class PythonExecutor(
    val terminal: TerminalEmulator,
    private val workspaceDir: File,
    private val userSitePackagesDir: File,
    private val pipCacheDir: File,
    private val hardwareBridge: AndroidHardwareBridge
) {
    private val isExecuting = AtomicBoolean(false)
    private var currentThread: Thread? = null

    /**
     * Runs the given Python source code on a dedicated background worker thread,
     * streaming stdout/stderr and interactive `input()` requests through [terminal].
     */
    fun runScript(
        scope: CoroutineScope,
        code: String,
        scriptFile: File,
        onComplete: (exitCode: Int, elapsedMs: Long) -> Unit = { _, _ -> }
    ): Job? {
        if (!isExecuting.compareAndSet(false, true)) {
            terminal.writeSystemMessage("已有 Python 任务正在运行，请先点击停止按钮。", isError = true)
            return null
        }

        terminal.resetForNewSession(scriptFile.name)

        return scope.launch(Dispatchers.IO) {
            currentThread = Thread.currentThread()
            val bridgeHost = PythonBridgeHost(terminal)
            var exitCode = 0
            var elapsedMs = 0L

            try {
                val py = Python.getInstance()
                val runnerModule = py.getModule("pydroid_runner")
                val resultObj = runnerModule.callAttr(
                    "execute_script",
                    code,
                    scriptFile.absolutePath,
                    workspaceDir.absolutePath,
                    userSitePackagesDir.absolutePath,
                    bridgeHost,
                    hardwareBridge
                )
                val resultMap = resultObj.asMap()
                val pyExit = resultMap[py.builtins.callAttr("str", "exit_code")]
                val pyElapsed = resultMap[py.builtins.callAttr("str", "elapsed_ms")]
                exitCode = pyExit?.toInt() ?: 0
                elapsedMs = pyElapsed?.toLong() ?: 0L
            } catch (t: Throwable) {
                exitCode = 1
                terminal.writeStderr("\nRuntime error: ${t.localizedMessage ?: t.toString()}\n")
            } finally {
                currentThread = null
                isExecuting.set(false)
                terminal.markSessionFinished(exitCode, elapsedMs)
                onComplete(exitCode, elapsedMs)
            }
        }
    }

    /**
     * Evaluates a single line in the persistent interactive Python REPL (`code.InteractiveConsole`).
     */
    fun evalReplLine(
        scope: CoroutineScope,
        line: String,
        isContinuation: Boolean,
        onResult: (moreInputNeeded: Boolean) -> Unit
    ) {
        val promptPrefix = if (isContinuation) "... " else ">>> "
        terminal.writeStdout("$promptPrefix$line\n")

        scope.launch(Dispatchers.IO) {
            val bridgeHost = PythonBridgeHost(terminal)
            val moreNeeded = runCatching {
                val py = Python.getInstance()
                val runnerModule = py.getModule("pydroid_runner")
                runnerModule.callAttr(
                    "eval_repl_line",
                    line,
                    workspaceDir.absolutePath,
                    userSitePackagesDir.absolutePath,
                    bridgeHost,
                    hardwareBridge
                ).toBoolean()
            }.getOrElse { e ->
                terminal.writeStderr("${e.localizedMessage}\n")
                false
            }
            withContext(Dispatchers.Main) {
                onResult(moreNeeded)
            }
        }
    }

    fun resetReplSession(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val py = Python.getInstance()
                py.getModule("pydroid_runner").callAttr("reset_repl_session")
            }
            terminal.clearScreen()
            terminal.writeSystemMessage("Python 3 Interactive REPL 已重置 (直接输入表达式即可求值)", isError = false)
        }
    }

    /**
     * Performs fast background AST syntax checking and extracts `class` / `def` outline symbols.
     */
    suspend fun analyzeCodeAst(code: String, fileName: String): AstAnalysisResult = withContext(Dispatchers.IO) {
        runCatching {
            val py = Python.getInstance()
            val runnerModule = py.getModule("pydroid_runner")
            val jsonStr = runnerModule.callAttr("analyze_code_ast", code, fileName).toString()
            val root = JSONObject(jsonStr)

            val diagArr = root.optJSONArray("diagnostics") ?: JSONArray()
            val diagnostics = ArrayList<AstSyntaxDiagnostic>(diagArr.length())
            for (i in 0 until diagArr.length()) {
                val d = diagArr.getJSONObject(i)
                diagnostics.add(
                    AstSyntaxDiagnostic(
                        line = d.optInt("line", 1),
                        column = d.optInt("col", 1),
                        endColumn = d.optInt("end_col", 2),
                        message = d.optString("message", "SyntaxError")
                    )
                )
            }

            val outArr = root.optJSONArray("outline") ?: JSONArray()
            val outline = ArrayList<AstOutlineSymbol>(outArr.length())
            for (i in 0 until outArr.length()) {
                val o = outArr.getJSONObject(i)
                outline.add(
                    AstOutlineSymbol(
                        name = o.optString("name", ""),
                        kind = o.optString("kind", "function"),
                        line = o.optInt("line", 1),
                        column = o.optInt("col", 1)
                    )
                )
            }

            AstAnalysisResult(diagnostics = diagnostics, outline = outline)
        }.getOrElse { AstAnalysisResult() }
    }

    /**
     * Queries TinaIDE-style runtime context diagnostics (Python version, ABI, cwd, sys.path).
     */
    suspend fun getRuntimeDiagnostics(invalidateCache: Boolean = false): RuntimeContextInfo = withContext(Dispatchers.IO) {
        runCatching {
            val py = Python.getInstance()
            val runnerModule = py.getModule("pydroid_runner")
            val jsonStr = runnerModule.callAttr(
                "get_runtime_diagnostics",
                workspaceDir.absolutePath,
                userSitePackagesDir.absolutePath,
                invalidateCache
            ).toString()
            val obj = JSONObject(jsonStr)
            val pathArr = obj.optJSONArray("sys_path") ?: JSONArray()
            val paths = ArrayList<String>(pathArr.length())
            for (i in 0 until pathArr.length()) {
                paths.add(pathArr.optString(i))
            }
            RuntimeContextInfo(
                pythonVersion = obj.optString("python_version", "3.10"),
                fullVersion = obj.optString("full_version", ""),
                platform = obj.optString("platform", ""),
                cwd = obj.optString("cwd", ""),
                workspaceDir = obj.optString("workspace_dir", ""),
                userSitePackages = obj.optString("user_site_packages", ""),
                sysPath = paths,
                loadedModulesCount = obj.optInt("loaded_modules_count", 0)
            )
        }.getOrElse { RuntimeContextInfo() }
    }

    /**
     * Stops the currently running script via both queue interrupt and CPython async exception injection.
     */
    fun stopRunningScript() {
        if (!isExecuting.get()) return
        terminal.sendInterruptSignal()
        Thread {
            runCatching {
                val py = Python.getInstance()
                val runnerModule = py.getModule("pydroid_runner")
                runnerModule.callAttr("interrupt_running_code")
            }
            runCatching {
                currentThread?.interrupt()
            }
        }.start()
    }

    fun listInstalledPackages(): List<InstalledPackage> {
        return runCatching {
            val py = Python.getInstance()
            val pipModule = py.getModule("pip_manager")
            val jsonStr = pipModule.callAttr(
                "list_installed_packages",
                userSitePackagesDir.absolutePath
            ).toString()

            val arr = JSONArray(jsonStr)
            val list = ArrayList<InstalledPackage>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    InstalledPackage(
                        name = obj.optString("name", ""),
                        version = obj.optString("version", ""),
                        summary = obj.optString("summary", ""),
                        isUserInstalled = obj.optBoolean("is_user_installed", false),
                        location = obj.optString("location", "")
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    fun installOrUpgradePackage(
        packageSpec: String,
        upgrade: Boolean,
        customIndexUrl: String,
        onLog: (String) -> Unit
    ): PipOperationResult {
        return runCatching {
            val py = Python.getInstance()
            val pipModule = py.getModule("pip_manager")
            val logger = PipLogBridge(onLog)
            val res = pipModule.callAttr(
                "install_or_upgrade_package",
                packageSpec,
                userSitePackagesDir.absolutePath,
                pipCacheDir.absolutePath,
                upgrade,
                customIndexUrl,
                logger
            ).asMap()
            val success = res[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            val message = res[py.builtins.callAttr("str", "message")]?.toString() ?: ""
            PipOperationResult(success, message)
        }.getOrElse { e ->
            onLog("\nException: ${e.localizedMessage}\n")
            PipOperationResult(false, e.localizedMessage ?: "Unknown pip error")
        }
    }

    fun uninstallPackage(
        packageName: String,
        onLog: (String) -> Unit
    ): PipOperationResult {
        return runCatching {
            val py = Python.getInstance()
            val pipModule = py.getModule("pip_manager")
            val logger = PipLogBridge(onLog)
            val res = pipModule.callAttr(
                "uninstall_package",
                packageName,
                userSitePackagesDir.absolutePath,
                logger
            ).asMap()
            val success = res[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            val message = res[py.builtins.callAttr("str", "message")]?.toString() ?: ""
            PipOperationResult(success, message)
        }.getOrElse { e ->
            onLog("\nException: ${e.localizedMessage}\n")
            PipOperationResult(false, e.localizedMessage ?: "Unknown uninstall error")
        }
    }
}
