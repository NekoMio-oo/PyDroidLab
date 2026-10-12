package com.pydroidlab.app.shell

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置终端的命令解释器。
 *
 * 背景：jackpal 的 AAR 只发布了 `emulatorview` 模块，**不含** libtermexec 的
 * 本地 pty 支持（Android 上 fork/exec 一个真实 shell 需要它），因此这里没有
 * 「真」shell，而是实现了一个面向 Python 的轻量命令集：
 *
 *   help / clear / pwd / ls / cd / cat / mkdir / touch / rm / echo / open
 *   run <file.py> / python [-c code | file.py] / pip <install|list|...>
 *
 * 这些都是终端里最常用的操作，配合「控制台」已经能覆盖完整的编辑→运行闭环。
 */
class PyShell(
    private val cwdProvider: () -> File?,
    private val cwdSetter: (File) -> Unit,
    private val openInEditor: (File) -> Unit,
    private val mirrorProvider: () -> String
) {

    companion object {
        const val CLEAR_SCREEN = "\u001B[2J\u001B[H"
        private const val MAX_CAT_BYTES = 200_000

        private const val RESET = "\u001B[0m"
        private const val BOLD = "\u001B[1m"
        private const val GREEN = "\u001B[32m"
        private const val BLUE = "\u001B[34m"
        private const val CYAN = "\u001B[36m"
        private const val RED = "\u001B[31m"
        private const val YELLOW = "\u001B[33m"
    }

    fun banner(): String =
        "$BOLD$CYAN PyDroidLab 内置终端 $RESET(无 root / 无 pty)\n" +
            "Python 解释器直接跑在 App 进程内；输入 ${BOLD}help$RESET 查看命令。\n\n"

    fun prompt(): String {
        val dir = cwdProvider() ?: File("/")
        return "$BOLD$GREEN pydroid$RESET:$BOLD$BLUE${dir.name.ifEmpty { "/" }}$RESET\$ "
    }

    suspend fun execute(raw: String): String {
        val line = raw.trim()
        if (line.isEmpty()) return ""
        val parts = tokenize(line)
        if (parts.isEmpty()) return ""
        val cmd = parts[0]
        val args = parts.drop(1)
        return try {
            when (cmd) {
                "help", "?", "--help" -> help()
                "clear", "cls" -> CLEAR_SCREEN
                "pwd" -> (cwdProvider()?.absolutePath ?: "/") + "\n"
                "ls", "dir" -> ls(args)
                "cd" -> cd(args)
                "cat", "type" -> cat(args)
                "mkdir", "md" -> mkdir(args)
                "touch" -> touch(args)
                "rm", "del" -> rm(args)
                "echo" -> args.joinToString(" ", postfix = "\n")
                "open", "edit" -> open(args)
                "run" -> run(args)
                "python", "py", "python3" -> python(args)
                "pip", "pip3" -> pip(args)
                "exit", "quit" -> "内置终端是常驻的，直接切换左侧导航即可。\n"
                else -> err("未知命令：$cmd（输入 help 查看可用命令）")
            }
        } catch (t: Throwable) {
            err(t.message ?: t.toString())
        }
    }

    // ------------------------------------------------------------------ 命令 --

    private fun help(): String = buildString {
        append("$BOLD 可用命令$RESET\n")
        append("  ${pad("help")}显示这份帮助\n")
        append("  ${pad("clear")}清屏\n")
        append("  ${pad("pwd")}显示当前目录\n")
        append("  ${pad("ls [dir]")}列出目录内容\n")
        append("  ${pad("cd <dir>")}切换目录（支持 .. 与绝对路径）\n")
        append("  ${pad("cat <file>")}查看文本文件\n")
        append("  ${pad("mkdir <name>")}新建目录\n")
        append("  ${pad("touch <name>")}新建空文件\n")
        append("  ${pad("rm [-r] <path>")}删除文件 / 目录\n")
        append("  ${pad("echo <text>")}回显文本\n")
        append("  ${pad("open <file>")}在编辑器中打开\n")
        append("  ${pad("run <file.py> [args]")}运行脚本并显示输出\n")
        append("  ${pad("python -c <code>")}直接执行一行 Python\n")
        append("  ${pad("python <file.py>")}等价于 run\n")
        append("  ${pad("pip install <pkg>")}安装包（沿用「设置」里的镜像源）\n")
        append("  ${pad("pip list")}列出已安装包\n")
        append("\n${YELLOW}注意$RESET：内置命令以外的可执行文件（如 ls/git）需要真 pty，暂不支持。\n\n")
    }

    private fun pad(s: String) = s.padEnd(26)

    private fun ls(args: List<String>): String {
        val target = resolve(args.firstOrNull()) ?: return err("目录不存在：${args.firstOrNull()}")
        val children = target.listFiles()
            ?: return err("无法读取目录：${target.absolutePath}")
        if (children.isEmpty()) return "（空目录）\n"
        val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = children.filter { !it.isDirectory }.sortedBy { it.name.lowercase() }
        return buildString {
            dirs.forEach { append("$BOLD$BLUE${it.name}/$RESET\n") }
            files.forEach {
                val size = it.length()
                append("${it.name}  $CYAN${human(size)}$RESET\n")
            }
            append("\n共 ${dirs.size} 个目录，${files.size} 个文件\n")
        }
    }

    private fun cd(args: List<String>): String {
        val arg = args.firstOrNull()?.trim().orEmpty()
        if (arg.isEmpty() || arg == "~") {
            cwdSetter(File(cwdProvider()?.absolutePath ?: "/"))
            return ""
        }
        val target = resolve(arg) ?: return err("目录不存在：$arg")
        if (!target.isDirectory) return err("不是目录：$arg")
        cwdSetter(target)
        return ""
    }

    private fun cat(args: List<String>): String {
        val name = args.firstOrNull() ?: return err("用法：cat <file>")
        val f = resolve(name) ?: return err("文件不存在：$name")
        if (!f.isFile) return err("不是文件：$name")
        if (f.length() > MAX_CAT_BYTES) {
            return err("文件过大（>${MAX_CAT_BYTES / 1000}KB），请用编辑器打开")
        }
        return try {
            f.readText() + "\n"
        } catch (t: Throwable) {
            err("读取失败：${t.message}")
        }
    }

    private fun mkdir(args: List<String>): String {
        val name = args.firstOrNull() ?: return err("用法：mkdir <name>")
        val dir = childOf(name) ?: return err("路径无效")
        return if (dir.mkdirs()) "" else err("创建失败：${dir.absolutePath}")
    }

    private fun touch(args: List<String>): String {
        val name = args.firstOrNull() ?: return err("用法：touch <name>")
        val f = childOf(name) ?: return err("路径无效")
        return try {
            if (!f.exists()) f.createNewFile()
            ""
        } catch (t: Throwable) {
            err("创建失败：${t.message}")
        }
    }

    private fun rm(args: List<String>): String {
        val recursive = args.any { it.startsWith("-") && (it.contains('r') || it.contains('R')) }
        val name = args.firstOrNull { !it.startsWith("-") } ?: return err("用法：rm [-r] <path>")
        val f = resolve(name) ?: return err("路径不存在：$name")
        val cwd = cwdProvider()
        if (cwd != null && (f.absolutePath == cwd.absolutePath || cwd.absolutePath.startsWith(f.absolutePath))) {
            return err("拒绝删除当前目录或其父目录")
        }
        if (f.isDirectory && !recursive) return err("$name 是目录，请使用 rm -r")
        return if (f.deleteRecursively()) "" else err("删除失败：$name")
    }

    private fun open(args: List<String>): String {
        val name = args.firstOrNull() ?: return err("用法：open <file>")
        val f = resolve(name) ?: return err("文件不存在：$name")
        if (!f.isFile) return err("不是文件：$name")
        openInEditor(f)
        return "已在编辑器中打开 ${f.name}\n"
    }

    private suspend fun run(args: List<String>): String {
        val name = args.firstOrNull() ?: return err("用法：run <file.py> [args...]")
        val f = resolve(name) ?: return err("文件不存在：$name")
        val extra = args.drop(1)
        return exec(listOf(f.absolutePath) + extra)
    }

    private suspend fun python(args: List<String>): String = exec(args)

    private suspend fun pip(args: List<String>): String = withContext(Dispatchers.IO) {
        if (args.isEmpty()) return@withContext err("用法：pip install <pkg> | pip list")
        try {
            val mod = Python.getInstance().getModule("pdl_shell_exec")
            mod.callAttr("run_pip", args.toTypedArray(), mirrorProvider()).toString()
        } catch (t: Throwable) {
            err("pip 执行失败：${t.message}")
        }
    }

    private suspend fun exec(args: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val mod = Python.getInstance().getModule("pdl_shell_exec")
            // 让 Python 侧的 os.getcwd() 与内置终端保持一致，
            // 这样 `python foo.py` 里的相对路径才符合直觉。
            cwdProvider()?.absolutePath?.let {
                try {
                    mod.callAttr("set_cwd", it)
                } catch (_: Throwable) {
                }
            }
            val out = mod.callAttr("run_command", args.toTypedArray()).toString()
            val code = try {
                mod.callAttr("last_status").toInt()
            } catch (_: Throwable) {
                0
            }
            if (out.isEmpty() && code == 0) "（无输出）\n" else out
        } catch (t: Throwable) {
            err("执行失败：${t.message}")
        }
    }

    // ------------------------------------------------------------------ 工具 --

    private fun resolve(name: String?): File? {
        if (name.isNullOrBlank()) return cwdProvider()
        var f = File(name)
        if (!f.isAbsolute) {
            f = File(cwdProvider() ?: File("/"), name)
        }
        return f.absoluteFile.normalize()
    }

    private fun childOf(name: String): File? {
        val f = File(name)
        return if (f.isAbsolute) f.absoluteFile.normalize()
        else File(cwdProvider() ?: return null, name).absoluteFile.normalize()
    }

    private fun human(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }

    private fun err(msg: String) = "$RED$msg$RESET\n"

    /** 简单分词：支持单/双引号。 */
    private fun tokenize(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null && c == quote -> quote = null
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == null && c.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        out.add(sb.toString()); sb.clear()
                    }
                }

                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
