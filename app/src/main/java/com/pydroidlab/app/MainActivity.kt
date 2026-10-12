package com.pydroidlab.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pydroidlab.app.ui.AppViewModel
import com.pydroidlab.app.ui.PyDroidLabRoot

/**
 * 唯一的 Activity。
 *
 * 除了承载 Compose 根节点外，它还负责：
 *  - 把「其它 App 分享 / 打开 .py 文件」的 Intent（见 AndroidManifest 中的
 *    SEND / VIEW intent-filter）转交给 [AppViewModel.openIncoming]；
 *  - 拦截**应用级外接键盘快捷键**（见 [dispatchKeyEvent]）。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 交给 ViewModel 承载状态：配置变更（横竖屏）不会重建它，
        // 因此 sora 的 Content 实例与光标位置都会原样保留。
        setContent {
            PyDroidLabRoot(vm = viewModel)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // ------------------------------------------------------------ 快捷键 --

    /**
     * 必须在这一层处理，不能放到 Compose 的 `onPreviewKeyEvent`：
     * 编辑器视图（sora CodeEditor）拿到焦点后，其 `EditorKeyEventHandler`
     * 会把「Ctrl / Alt / Shift + 字母」全部派发成 `KeyBindingEvent` 并消费掉，
     * 事件根本冒泡不到上层。
     *
     * 这里只接管 save / run / find / replace / format / debug；
     * **undo / redo 交给编辑器自身**（sora 已内置 Ctrl+Z / Ctrl+Y），
     * 由它处理才能正确维护撤销栈与光标。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && handleShortcut(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleShortcut(event: KeyEvent): Boolean {
        if (!event.isCtrlPressed && !event.isAltPressed) return false
        val label = shortcutLabel(event) ?: return false

        val shortcuts = viewModel.settings.shortcuts
        val action = shortcuts.entries
            .firstOrNull { it.value.equals(label, ignoreCase = true) }
            ?.key ?: return false

        return when (action) {
            "save" -> {
                viewModel.saveActive()
                true
            }

            "run" -> {
                viewModel.runActiveScript()
                true
            }

            "find" -> {
                viewModel.requestEditorCommand(AppViewModel.CMD_FIND)
                true
            }

            "replace" -> {
                viewModel.requestEditorCommand(AppViewModel.CMD_REPLACE)
                true
            }

            "format" -> {
                viewModel.requestEditorCommand(AppViewModel.CMD_FORMAT)
                true
            }

            "debug" -> {
                viewModel.startDebug()
                true
            }

            // undo / redo：交给编辑器内部实现，不在此拦截
            else -> false
        }
    }

    /** 把按键事件转成 `Ctrl+Alt+L` 这样的标签，用于匹配用户自定义的快捷键。 */
    private fun shortcutLabel(event: KeyEvent): String? {
        val key = event.keyCode
        // 只处理字母键，避免误吞 Ctrl+方向键、音量键等
        if (key < KeyEvent.KEYCODE_A || key > KeyEvent.KEYCODE_Z) return null
        val parts = mutableListOf<String>()
        if (event.isCtrlPressed) parts.add("Ctrl")
        if (event.isAltPressed) parts.add("Alt")
        if (event.isShiftPressed) parts.add("Shift")
        val name = KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")
        if (name.isEmpty()) return null
        parts.add(name.uppercase())
        return parts.joinToString("+")
    }

    // -------------------------------------------------------------- Intent --

    private fun handleIntent(intent: Intent?) {
        val i = intent ?: return
        when (i.action) {
            Intent.ACTION_SEND -> {
                val uri = streamOf(i)
                if (uri != null) {
                    viewModel.openIncoming(uri)
                } else {
                    i.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.openIncomingText(it) }
                }
            }

            Intent.ACTION_VIEW -> i.data?.let { viewModel.openIncoming(it) }

            else -> Unit
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
