package com.pydroidlab.app.ui.editor

import android.content.Context
import io.github.rosemoe.sora.text.Content
import java.io.File

/** One open editing tab. */
class EditorTab(
    val id: String,
    val file: File?,
    var title: String
) {
    var content: Content = Content()
    var isDirty: Boolean = false
    // Cursor position preserved across tab switches & config changes.
    var cursorLine: Int = 0
    var cursorColumn: Int = 0
    var breakpoints: MutableSet<Int> = mutableSetOf()

    val displayTitle: String
        get() = if (isDirty) "$title ●" else title

    fun loadFromFile() {
        file?.let {
            if (it.exists()) {
                content = Content(it.readText())
                isDirty = false
            }
        }
    }

    fun save() {
        file?.let {
            it.writeText(content.toString())
            isDirty = false
        }
    }
}

/** Manages the set of open tabs and the active one. */
class EditorState {
    val tabs = mutableListOf<EditorTab>()
    var activeIndex = -1
        private set

    val active: EditorTab? get() = tabs.getOrNull(activeIndex)

    fun openFile(file: File, recent: ((String) -> Unit)? = null): EditorTab {
        // Reuse an existing tab for the same path.
        val existing = tabs.indexOfFirst { it.file?.absolutePath == file.absolutePath }
        if (existing >= 0) {
            activeIndex = existing
            return tabs[existing]
        }
        val tab = EditorTab(
            id = "tab_${System.nanoTime()}",
            file = file,
            title = file.name
        )
        tab.loadFromFile()
        tabs.add(tab)
        activeIndex = tabs.size - 1
        recent?.invoke(file.absolutePath)
        return tab
    }

    fun newUntitled(template: String = ""): EditorTab {
        val n = tabs.count { it.file == null } + 1
        val tab = EditorTab("tab_${System.nanoTime()}", null, "untitled-$n.py")
        tab.content = Content(template)
        tabs.add(tab)
        activeIndex = tabs.size - 1
        return tab
    }

    fun saveTabAs(tab: EditorTab, file: File) {
        // Untitled tab becomes bound to a file.
        val idx = tabs.indexOf(tab)
        if (idx >= 0) {
            val bound = EditorTab(tab.id, file, file.name)
            bound.content = tab.content
            bound.isDirty = true
            bound.cursorLine = tab.cursorLine
            bound.cursorColumn = tab.cursorColumn
            bound.breakpoints = tab.breakpoints
            bound.save()
            tabs[idx] = bound
        }
    }

    fun close(index: Int) {
        if (index in tabs.indices) {
            tabs.removeAt(index)
            activeIndex = when {
                tabs.isEmpty() -> -1
                activeIndex >= tabs.size -> tabs.size - 1
                else -> activeIndex
            }
        }
    }

    fun select(index: Int) {
        if (index in tabs.indices) activeIndex = index
    }

    fun saveAll() = tabs.forEach { it.save() }
}

/** New-file templates. */
object FileTemplates {
    val EMPTY = ""
    val FUNCTION = "def main():\n    pass\n\n\nif __name__ == \"__main__\":\n    main()\n"
    val CLASS = "class MyClass:\n    def __init__(self):\n        pass\n\n    def run(self):\n        pass\n"
    val SCRIPT = "#!/usr/bin/env python3\n\"\"\"Script description.\"\"\"\n\n\ndef main():\n    pass\n\n\nif __name__ == \"__main__\":\n    main()\n"

    val ALL = mapOf(
        "空脚本" to EMPTY,
        "函数模板" to FUNCTION,
        "类模板" to CLASS,
        "脚本模板" to SCRIPT
    )
}
