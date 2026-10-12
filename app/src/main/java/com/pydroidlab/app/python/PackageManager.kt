package com.pydroidlab.app.python

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PipMirror(val id: String, val label: String) {
    PYPI("pypi", "PyPI (官方)"),
    TSINGHUA("tsinghua", "清华镜像 (TUNA)")
}

/** Curated one-tap scientific stack (installed on demand). */
val SCI_PACKAGES = listOf(
    "numpy", "pandas", "matplotlib", "scipy", "scikit-learn", "requests", "Pillow"
)

class PackageManager {

    var mirror: PipMirror = PipMirror.PYPI

    private suspend fun call(fn: String, vararg args: Any?): Int = withContext(Dispatchers.IO) {
        try {
            val mod = Python.getInstance().getModule("pdl_pip")
            mod.callAttr(fn, *args).toInt()
        } catch (t: Throwable) {
            -1
        }
    }

    suspend fun install(spec: String) = call("install", spec, mirror.id)
    suspend fun upgrade(spec: String) = call("upgrade", spec, mirror.id)
    suspend fun uninstall(name: String) = call("uninstall", name)
    suspend fun installRequirements(path: String) = call("install_requirements", path, mirror.id)
    suspend fun installWheel(path: String) = call("install_wheel", path)

    suspend fun installScientific(onEach: (String, Int) -> Unit = { _, _ -> }) {
        for (pkg in SCI_PACKAGES) {
            val code = install(pkg)
            onEach(pkg, code)
        }
    }

    suspend fun listInstalled(): List<String> = withContext(Dispatchers.IO) {
        try {
            val mod = Python.getInstance().getModule("pdl_pip")
            mod.callAttr("list_installed").asList().map { it.toString() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    suspend fun freeze(): List<String> = listInstalled()
}
