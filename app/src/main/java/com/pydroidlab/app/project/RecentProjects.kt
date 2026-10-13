package com.pydroidlab.app.project

import android.content.Context

/**
 * 最近打开的项目名单（Phase 1 项目主页的「最近打开」分区）。
 *
 * 之所以独立一个 SharedPreferences 而不进 SettingsRepository：
 * SettingsRepository 是 DataStore 单一数据源的已冻结模块，这里是
 * 「按项目」的稀疏有序名单，单独存放更合适，也不会影响它的公开接口。
 */
class RecentProjects(context: Context) {

    private val prefs = context.getSharedPreferences("pdl_recent_projects", Context.MODE_PRIVATE)

    /** 最近打开的项目名，最新的在前，最多保留 [MAX] 个。 */
    fun names(): List<String> =
        prefs.getString(KEY, null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun touch(name: String) {
        val updated = names().toMutableList().apply {
            remove(name)
            add(0, name)
        }.take(MAX)
        prefs.edit().putString(KEY, updated.joinToString(SEP)).apply()
    }

    fun remove(name: String) {
        val updated = names().toMutableList().apply { remove(name) }
        prefs.edit().putString(KEY, updated.joinToString(SEP)).apply()
    }

    companion object {
        private const val KEY = "recent_project_names"
        private const val SEP = "|"
        private const val MAX = 8
    }
}
