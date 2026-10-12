package com.pydroidlab.app.core

import android.content.Context
import com.pydroidlab.app.project.ProjectManager
import com.pydroidlab.app.python.PythonRunner
import com.pydroidlab.app.python.PackageManager
import com.pydroidlab.app.python.DebuggerController
import com.pydroidlab.app.python.ReplController
import com.pydroidlab.app.project.RecentFiles
import com.pydroidlab.app.ui.settings.SettingsRepository

/** Simple manual DI container to keep the codebase framework-free. */
class ServiceLocator(context: Context) {
    val appContext: Context = context.applicationContext
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val projectManager: ProjectManager by lazy { ProjectManager(appContext) }
    val recentFiles: RecentFiles by lazy { RecentFiles(appContext) }
    val pythonRunner: PythonRunner by lazy { PythonRunner(appContext) }
    val packageManager: PackageManager by lazy { PackageManager() }
    val debugger: DebuggerController by lazy { DebuggerController() }
    val repl: ReplController by lazy { ReplController() }
}
