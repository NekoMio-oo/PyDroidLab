package com.pydroidlab.app.python

import android.content.Context
import org.json.JSONObject

/**
 * 每个脚本一份的「运行配置」。
 *
 * 之所以单独一个 store 而不塞进 [com.pydroidlab.app.ui.settings.SettingsRepository]：
 * SettingsRepository 是已冻结的模块（DataStore 单一数据源），运行配置是
 * 「按文件」的稀疏数据，用独立的 SharedPreferences 更合适，也不会影响它的公开接口。
 */
data class RunOptions(
    val filePath: String = "",
    /** 命令行参数原文，按 shell 规则分词（支持引号）。 */
    val argv: String = "",
    /** 工作目录；留空表示用脚本所在目录。 */
    val cwd: String = "",
    /** 环境变量，每行 `KEY=VALUE`。 */
    val env: String = "",
    /** 预置 stdin：会在脚本读取 input() 之前先被消费。 */
    val stdinPreset: String = ""
) {

    fun argvAsList(): List<String> = splitArgs(argv)

    fun envAsMap(): Map<String, String> = parseEnv(env)

    fun toRunConfig(): RunConfig = RunConfig(
        path = filePath,
        argv = argvAsList(),
        cwd = cwd.trim().ifBlank { null },
        env = envAsMap(),
        stdinPreset = stdinPreset
    )

    companion object {

        /** 简单分词：支持单/双引号；未闭合的引号按「到行尾」处理。 */
        fun splitArgs(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quote: Char? = null
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    quote != null && c == quote -> quote = null
                    quote == null && (c == '"' || c == '\'') -> quote = c
                    quote == null && c.isWhitespace() -> {
                        if (sb.isNotEmpty()) {
                            out.add(sb.toString())
                            sb.clear()
                        }
                    }

                    else -> sb.append(c)
                }
                i++
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
            return out
        }

        /** 解析 `KEY=VALUE` 行；忽略空行与 `#` 注释，去掉可选的 export 前缀。 */
        fun parseEnv(raw: String): Map<String, String> {
            val out = linkedMapOf<String, String>()
            raw.lines().forEach { line ->
                val trimmed = line.trim().removePrefix("export ").trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val idx = trimmed.indexOf('=')
                if (idx <= 0) return@forEach
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim().trim('"', '\'')
                if (key.isNotEmpty()) out[key] = value
            }
            return out
        }
    }
}

/** 以脚本绝对路径为键持久化 [RunOptions]。 */
class RunConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("pdl_run_config", Context.MODE_PRIVATE)

    fun load(filePath: String): RunOptions {
        val raw = prefs.getString(filePath, null)
            ?: return RunOptions(filePath = filePath)
        return try {
            val o = JSONObject(raw)
            RunOptions(
                filePath = filePath,
                argv = o.optString("argv"),
                cwd = o.optString("cwd"),
                env = o.optString("env"),
                stdinPreset = o.optString("stdin")
            )
        } catch (t: Throwable) {
            RunOptions(filePath = filePath)
        }
    }

    fun save(options: RunOptions) {
        if (options.filePath.isBlank()) return
        val o = JSONObject()
            .put("argv", options.argv)
            .put("cwd", options.cwd)
            .put("env", options.env)
            .put("stdin", options.stdinPreset)
        prefs.edit().putString(options.filePath, o.toString()).apply()
    }

    fun clear(filePath: String) {
        prefs.edit().remove(filePath).apply()
    }
}
