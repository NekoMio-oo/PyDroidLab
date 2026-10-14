package com.pydroidlab.app.runtime

import com.chaquo.python.Python
import com.pydroidlab.terminal.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
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

/**
 * Manages background Python script execution and runtime pip operations via Chaquopy.
 */
class PythonExecutor(
    val terminal: TerminalEmulator,
    private val workspaceDir: File,
    private val userSitePackagesDir: File,
    private val pipCacheDir: File
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
            terminal.writeSystemMessage("A Python script is already running. Stop it first before starting a new run.", isError = true)
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
                    bridgeHost
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
     * Stops the currently running script:
     * 1. Unblocks any waiting `input()` call in the terminal queue.
     * 2. Calls CPython's `PyThreadState_SetAsyncExc` via `pydroid_runner.interrupt_running_code()`
     *    to inject a `KeyboardInterrupt` into CPU-bound loops.
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

    /**
     * Lists all installed Python packages (both user-installed in `filesDir/python/site-packages`
     * and built-in Chaquopy packages).
     */
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

    /**
     * Installs or upgrades a package into the app's writable `userSitePackagesDir`.
     */
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

    /**
     * Uninstalls a user-installed package from `userSitePackagesDir`.
     */
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
