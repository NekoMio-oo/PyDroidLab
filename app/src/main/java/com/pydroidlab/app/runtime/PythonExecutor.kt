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

data class AstLinterWarning(
    val line: Int,
    val code: String,
    val message: String
)

data class AstOutlineSymbol(
    val name: String,
    val kind: String,
    val line: Int,
    val column: Int
)

data class CodeMetrics(
    val totalLines: Int = 0,
    val codeLines: Int = 0,
    val commentLines: Int = 0,
    val blankLines: Int = 0,
    val classCount: Int = 0,
    val funcCount: Int = 0
)

data class AstAnalysisResult(
    val diagnostics: List<AstSyntaxDiagnostic> = emptyList(),
    val warnings: List<AstLinterWarning> = emptyList(),
    val outline: List<AstOutlineSymbol> = emptyList(),
    val metrics: CodeMetrics = CodeMetrics()
)

data class RuntimeContextInfo(
    val pythonVersion: String = "3.13",
    val fullVersion: String = "",
    val platform: String = "",
    val cwd: String = "",
    val workspaceDir: String = "",
    val userSitePackages: String = "",
    val sysPath: List<String> = emptyList(),
    val loadedModulesCount: Int = 0
)

class PythonExecutor(
    val terminal: TerminalEmulator,
    private val workspaceDir: File,
    private val userSitePackagesDir: File,
    private val pipCacheDir: File,
    private val hardwareBridge: AndroidHardwareBridge
) {
    private val isExecuting = AtomicBoolean(false)
    private var currentThread: Thread? = null

    fun runScript(
        scope: CoroutineScope,
        code: String,
        scriptFile: File,
        cliArgs: String = "",
        onComplete: (exitCode: Int, elapsedMs: Long) -> Unit = { _, _ -> }
    ): Job? {
        if (!isExecuting.compareAndSet(false, true)) {
            terminal.writeSystemMessage("已有 Python 任务正在运行，请先点击停止按钮。", isError = true)
            return null
        }

        val bannerTitle = if (cliArgs.isBlank()) scriptFile.name else "${scriptFile.name} $cliArgs"
        terminal.resetForNewSession(bannerTitle)

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
                    hardwareBridge,
                    cliArgs
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

            val warnArr = root.optJSONArray("warnings") ?: JSONArray()
            val warnings = ArrayList<AstLinterWarning>(warnArr.length())
            for (i in 0 until warnArr.length()) {
                val w = warnArr.getJSONObject(i)
                warnings.add(
                    AstLinterWarning(
                        line = w.optInt("line", 1),
                        code = w.optString("code", "W000"),
                        message = w.optString("message", "")
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

            val mObj = root.optJSONObject("metrics") ?: JSONObject()
            val metrics = CodeMetrics(
                totalLines = mObj.optInt("total_lines", 0),
                codeLines = mObj.optInt("code_lines", 0),
                commentLines = mObj.optInt("comment_lines", 0),
                blankLines = mObj.optInt("blank_lines", 0),
                classCount = mObj.optInt("class_count", 0),
                funcCount = mObj.optInt("func_count", 0)
            )

            AstAnalysisResult(
                diagnostics = diagnostics,
                warnings = warnings,
                outline = outline,
                metrics = metrics
            )
        }.getOrElse { AstAnalysisResult() }
    }

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
                pythonVersion = obj.optString("python_version", "3.13"),
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

    suspend fun formatPythonCode(code: String, tabWidth: Int): String = withContext(Dispatchers.IO) {
        runCatching {
            val py = Python.getInstance()
            val runnerModule = py.getModule("pydroid_runner")
            val res = runnerModule.callAttr("format_python_code", code, tabWidth).asMap()
            res[py.builtins.callAttr("str", "formatted")]?.toString() ?: code
        }.getOrDefault(code)
    }

    fun inspectSqliteDb(dbPath: String): String {
        return runCatching {
            val py = Python.getInstance()
            val runnerModule = py.getModule("pydroid_runner")
            runnerModule.callAttr("inspect_sqlite_db", dbPath).toString()
        }.getOrElse { "读取 SQLite 数据库失败: ${it.localizedMessage}" }
    }

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
            val pipModule = py.getModule("pydroid_runner")
            val pipMgr = py.getModule("pip_manager")
            val logger = PipLogBridge(onLog)
            val res = pipMgr.callAttr(
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
