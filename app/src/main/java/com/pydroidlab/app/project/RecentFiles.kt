package com.pydroidlab.app.project

import android.content.Context
import org.json.JSONArray

class RecentFiles(context: Context) {
    private val prefs = context.getSharedPreferences("pdl_recent", Context.MODE_PRIVATE)
    private val max = 20

    fun list(): List<String> {
        val arr = JSONArray(prefs.getString("files", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun add(path: String) {
        val cur = list().toMutableList()
        cur.remove(path)
        cur.add(0, path)
        val trimmed = cur.take(max)
        val arr = JSONArray()
        trimmed.forEach { arr.put(it) }
        prefs.edit().putString("files", arr.toString()).apply()
    }

    fun remove(path: String) {
        val cur = list().toMutableList().also { it.remove(path) }
        val arr = JSONArray()
        cur.forEach { arr.put(it) }
        prefs.edit().putString("files", arr.toString()).apply()
    }

    fun clear() = prefs.edit().putString("files", "[]").apply()
}
