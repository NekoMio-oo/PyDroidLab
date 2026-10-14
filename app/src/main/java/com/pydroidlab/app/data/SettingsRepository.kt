package com.pydroidlab.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pydroidlab_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val ideColorThemeId: String = "one_dark_pro",
    val terminalFullScreen: Boolean = true,
    val twoRowSymbolBar: Boolean = true,
    val editorFontSizeSp: Float = 15f,
    val terminalFontSizeSp: Float = 13f,
    val tabWidth: Int = 4,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val autoSaveOnRun: Boolean = true,
    val customPipIndexUrl: String = ""
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IDE_COLOR_THEME_ID = stringPreferencesKey("ide_color_theme_id")
        val TERMINAL_FULL_SCREEN = booleanPreferencesKey("terminal_full_screen")
        val TWO_ROW_SYMBOL_BAR = booleanPreferencesKey("two_row_symbol_bar")
        val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val TAB_WIDTH = intPreferencesKey("tab_width")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val AUTO_SAVE_ON_RUN = booleanPreferencesKey("auto_save_on_run")
        val CUSTOM_PIP_INDEX = stringPreferencesKey("custom_pip_index")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK)
        AppSettings(
            themeMode = theme,
            ideColorThemeId = prefs[Keys.IDE_COLOR_THEME_ID] ?: "one_dark_pro",
            terminalFullScreen = prefs[Keys.TERMINAL_FULL_SCREEN] ?: true,
            twoRowSymbolBar = prefs[Keys.TWO_ROW_SYMBOL_BAR] ?: true,
            editorFontSizeSp = (prefs[Keys.EDITOR_FONT_SIZE] ?: 15f).coerceIn(10f, 28f),
            terminalFontSizeSp = (prefs[Keys.TERMINAL_FONT_SIZE] ?: 13f).coerceIn(10f, 24f),
            tabWidth = (prefs[Keys.TAB_WIDTH] ?: 4).let { if (it in listOf(2, 4, 8)) it else 4 },
            wordWrap = prefs[Keys.WORD_WRAP] ?: false,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            autoSaveOnRun = prefs[Keys.AUTO_SAVE_ON_RUN] ?: true,
            customPipIndexUrl = prefs[Keys.CUSTOM_PIP_INDEX] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setIdeColorThemeId(themeId: String) {
        context.dataStore.edit { it[Keys.IDE_COLOR_THEME_ID] = themeId }
    }

    suspend fun setTerminalFullScreen(fullScreen: Boolean) {
        context.dataStore.edit { it[Keys.TERMINAL_FULL_SCREEN] = fullScreen }
    }

    suspend fun setTwoRowSymbolBar(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TWO_ROW_SYMBOL_BAR] = enabled }
    }

    suspend fun setEditorFontSize(sizeSp: Float) {
        context.dataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(10f, 28f) }
    }

    suspend fun setTerminalFontSize(sizeSp: Float) {
        context.dataStore.edit { it[Keys.TERMINAL_FONT_SIZE] = sizeSp.coerceIn(10f, 24f) }
    }

    suspend fun setTabWidth(width: Int) {
        context.dataStore.edit { it[Keys.TAB_WIDTH] = width }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WORD_WRAP] = enabled }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LINE_NUMBERS] = enabled }
    }

    suspend fun setAutoSaveOnRun(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SAVE_ON_RUN] = enabled }
    }

    suspend fun setCustomPipIndexUrl(url: String) {
        context.dataStore.edit { it[Keys.CUSTOM_PIP_INDEX] = url.trim() }
    }
}
