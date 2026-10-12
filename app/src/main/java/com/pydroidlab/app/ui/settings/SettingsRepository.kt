package com.pydroidlab.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "pdl_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class EditorColorScheme(val label: String) {
    DARCULA("Darcula"),
    GITHUB("GitHub Light"),
    MONOKAI("Monokai"),
    SOLARIZED_DARK("Solarized Dark"),
    SOLARIZED_LIGHT("Solarized Light")
}

data class EditorSettings(
    val fontSizeSp: Float = 14f,
    val lineHeightMultiplier: Float = 1.0f,
    val tabWidth: Int = 4,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val autoIndent: Boolean = true,
    val autoPair: Boolean = true,
    // Bug 1/2：中文输入法联想（composing）期间 sora 默认会抑制补全弹窗；
    // 该开关映射 props.autoCompletionOnComposing，默认保持补全可见。
    val imeCompletions: Boolean = true,
    val formatOnSave: Boolean = false,
    val colorScheme: EditorColorScheme = EditorColorScheme.DARCULA
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pipMirror: String = "pypi",
    val symbolBar: List<String> = DEFAULT_SYMBOLS,
    val editor: EditorSettings = EditorSettings(),
    val shortcuts: Map<String, String> = DEFAULT_SHORTCUTS
) {
    companion object {
        val DEFAULT_SYMBOLS = listOf(":", "(", ")", "[", "]", "{", "}", "=", "_", "\"", "'", ",", ".", ";", "+", "-", "*", "/", "<", ">", "#", "@", "|", "→")
        val DEFAULT_SHORTCUTS = mapOf(
            "save" to "Ctrl+S",
            "run" to "Ctrl+R",
            "find" to "Ctrl+F",
            "replace" to "Ctrl+H",
            "undo" to "Ctrl+Z",
            "redo" to "Ctrl+Y",
            "format" to "Ctrl+Alt+L",
            "debug" to "Ctrl+D"
        )
    }
}

class SettingsRepository(private val context: Context) {

    private object K {
        val THEME = stringPreferencesKey("theme_mode")
        val MIRROR = stringPreferencesKey("pip_mirror")
        val SYMBOLS = stringPreferencesKey("symbol_bar")
        val FONT = floatPreferencesKey("font_size")
        val LINE_H = floatPreferencesKey("line_height")
        val TAB = intPreferencesKey("tab_width")
        val WRAP = booleanPreferencesKey("word_wrap")
        val LINENO = booleanPreferencesKey("line_numbers")
        val AUTO_INDENT = booleanPreferencesKey("auto_indent")
        val AUTO_PAIR = booleanPreferencesKey("auto_pair")
        val IME_COMPLETIONS = booleanPreferencesKey("ime_completions")
        val FORMAT_SAVE = booleanPreferencesKey("format_on_save")
        val SCHEME = stringPreferencesKey("color_scheme")
        val SHORTCUTS = stringPreferencesKey("shortcuts_json")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val editor = EditorSettings(
            fontSizeSp = p[K.FONT] ?: 14f,
            lineHeightMultiplier = p[K.LINE_H] ?: 1.0f,
            tabWidth = p[K.TAB] ?: 4,
            wordWrap = p[K.WRAP] ?: false,
            showLineNumbers = p[K.LINENO] ?: true,
            autoIndent = p[K.AUTO_INDENT] ?: true,
            autoPair = p[K.AUTO_PAIR] ?: true,
            imeCompletions = p[K.IME_COMPLETIONS] ?: true,
            formatOnSave = p[K.FORMAT_SAVE] ?: false,
            colorScheme = EditorColorScheme.values().find { it.name == p[K.SCHEME] } ?: EditorColorScheme.DARCULA
        )
        AppSettings(
            themeMode = ThemeMode.values().find { it.name == p[K.THEME] } ?: ThemeMode.SYSTEM,
            pipMirror = p[K.MIRROR] ?: "pypi",
            symbolBar = (p[K.SYMBOLS]?.split("|")?.filter { it.isNotEmpty() } ?: AppSettings.DEFAULT_SYMBOLS),
            editor = editor,
            shortcuts = parseShortcuts(p[K.SHORTCUTS]) ?: AppSettings.DEFAULT_SHORTCUTS
        )
    }

    suspend fun setThemeMode(m: ThemeMode) = context.dataStore.edit { it[K.THEME] = m.name }
    suspend fun setPipMirror(m: String) = context.dataStore.edit { it[K.MIRROR] = m }
    suspend fun setSymbolBar(symbols: List<String>) = context.dataStore.edit { it[K.SYMBOLS] = symbols.joinToString("|") }
    suspend fun setEditor(e: EditorSettings) = context.dataStore.edit {
        it[K.FONT] = e.fontSizeSp
        it[K.LINE_H] = e.lineHeightMultiplier
        it[K.TAB] = e.tabWidth
        it[K.WRAP] = e.wordWrap
        it[K.LINENO] = e.showLineNumbers
        it[K.AUTO_INDENT] = e.autoIndent
        it[K.AUTO_PAIR] = e.autoPair
        it[K.IME_COMPLETIONS] = e.imeCompletions
        it[K.FORMAT_SAVE] = e.formatOnSave
        it[K.SCHEME] = e.colorScheme.name
    }
    suspend fun setShortcuts(map: Map<String, String>) = context.dataStore.edit {
        it[K.SHORTCUTS] = JSONObject(map).toString()
    }

    private fun parseShortcuts(json: String?): Map<String, String>? {
        if (json == null) return null
        return try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) { null }
    }

    // ---- import / export ---------------------------------------------------

    suspend fun exportJson(): String {
        var result = "{}"
        context.dataStore.data.collect { p ->
            val o = JSONObject()
            o.put("theme", p[K.THEME]); o.put("mirror", p[K.MIRROR])
            o.put("symbols", p[K.SYMBOLS])
            o.put("font", p[K.FONT]?.toDouble()); o.put("line_h", p[K.LINE_H]?.toDouble())
            o.put("tab", p[K.TAB]); o.put("wrap", p[K.WRAP]); o.put("lineno", p[K.LINENO])
            o.put("auto_indent", p[K.AUTO_INDENT]); o.put("auto_pair", p[K.AUTO_PAIR])
            o.put("format_save", p[K.FORMAT_SAVE]); o.put("scheme", p[K.SCHEME])
            o.put("shortcuts", p[K.SHORTCUTS])
            result = o.toString(2)
        }
        return result
    }

    suspend fun importJson(json: String) {
        val o = JSONObject(json)
        context.dataStore.edit { p ->
            o.optString("theme").takeIf { it.isNotEmpty() }?.let { p[K.THEME] = it }
            o.optString("mirror").takeIf { it.isNotEmpty() }?.let { p[K.MIRROR] = it }
            o.optString("symbols").takeIf { it.isNotEmpty() }?.let { p[K.SYMBOLS] = it }
            if (o.has("font")) p[K.FONT] = o.optDouble("font").toFloat()
            if (o.has("line_h")) p[K.LINE_H] = o.optDouble("line_h").toFloat()
            if (o.has("tab")) p[K.TAB] = o.optInt("tab")
            if (o.has("wrap")) p[K.WRAP] = o.optBoolean("wrap")
            if (o.has("lineno")) p[K.LINENO] = o.optBoolean("lineno")
            if (o.has("auto_indent")) p[K.AUTO_INDENT] = o.optBoolean("auto_indent")
            if (o.has("auto_pair")) p[K.AUTO_PAIR] = o.optBoolean("auto_pair")
            if (o.has("format_save")) p[K.FORMAT_SAVE] = o.optBoolean("format_save")
            o.optString("scheme").takeIf { it.isNotEmpty() }?.let { p[K.SCHEME] = it }
            o.optString("shortcuts").takeIf { it.isNotEmpty() }?.let { p[K.SHORTCUTS] = it }
        }
    }
}
