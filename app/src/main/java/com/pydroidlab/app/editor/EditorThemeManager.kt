package com.pydroidlab.app.editor

import androidx.compose.ui.graphics.Color
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

enum class IdeColorTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val chromeBg: Color,
    val editorBg: Color,
    val tabStripBg: Color,
    val statusBarBg: Color,
    val accentColor: Color,
    val textPrimary: Color,
    val textSecondary: Color
) {
    ONE_DARK_PRO(
        id = "one_dark_pro",
        displayName = "One Dark",
        isDark = true,
        chromeBg = Color(0xFF161B22),
        editorBg = Color(0xFF0D1117),
        tabStripBg = Color(0xFF0D1117),
        statusBarBg = Color(0xFF161B22),
        accentColor = Color(0xFF58A6FF),
        textPrimary = Color(0xFFE6EDF3),
        textSecondary = Color(0xFF8B949E)
    ),
    TOKYO_NIGHT(
        id = "tokyo_night",
        displayName = "Tokyo Night",
        isDark = true,
        chromeBg = Color(0xFF16161E),
        editorBg = Color(0xFF1A1B26),
        tabStripBg = Color(0xFF16161E),
        statusBarBg = Color(0xFF16161E),
        accentColor = Color(0xFF7AA2F7),
        textPrimary = Color(0xFFC0CAF5),
        textSecondary = Color(0xFF565F89)
    ),
    DRACULA(
        id = "dracula",
        displayName = "Dracula",
        isDark = true,
        chromeBg = Color(0xFF1E1F29),
        editorBg = Color(0xFF282A36),
        tabStripBg = Color(0xFF1E1F29),
        statusBarBg = Color(0xFF1E1F29),
        accentColor = Color(0xFFBD93F9),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFF6272A4)
    ),
    MONOKAI_PRO(
        id = "monokai_pro",
        displayName = "Monokai",
        isDark = true,
        chromeBg = Color(0xFF221F22),
        editorBg = Color(0xFF2D2A2E),
        tabStripBg = Color(0xFF221F22),
        statusBarBg = Color(0xFF221F22),
        accentColor = Color(0xFFFFD866),
        textPrimary = Color(0xFFFCFCFA),
        textSecondary = Color(0xFF727072)
    ),
    CATPPUCCIN_MOCHA(
        id = "catppuccin_mocha",
        displayName = "Catppuccin",
        isDark = true,
        chromeBg = Color(0xFF181825),
        editorBg = Color(0xFF1E1E2E),
        tabStripBg = Color(0xFF181825),
        statusBarBg = Color(0xFF181825),
        accentColor = Color(0xFFCBA6F7),
        textPrimary = Color(0xFFCDD6F4),
        textSecondary = Color(0xFF6C7086)
    ),
    GITHUB_LIGHT(
        id = "github_light",
        displayName = "GitHub Light",
        isDark = false,
        chromeBg = Color(0xFFF6F8FA),
        editorBg = Color(0xFFFFFFFF),
        tabStripBg = Color(0xFFF6F8FA),
        statusBarBg = Color(0xFFF6F8FA),
        accentColor = Color(0xFF0969DA),
        textPrimary = Color(0xFF1F2328),
        textSecondary = Color(0xFF57606A)
    );

    companion object {
        fun fromId(id: String): IdeColorTheme {
            return entries.find { it.id == id } ?: ONE_DARK_PRO
        }
    }
}

object EditorThemeManager {

    fun createSoraScheme(theme: IdeColorTheme): EditorColorScheme {
        return when (theme) {
            IdeColorTheme.ONE_DARK_PRO -> buildCustomScheme(
                wholeBg = 0xFF0D1117.toInt(),
                lineNumBg = 0xFF0D1117.toInt(),
                lineNum = 0xFF484F58.toInt(),
                curLine = 0xFF161B22.toInt(),
                selBg = 0xFF1F3A5F.toInt(),
                cursor = 0xFF58A6FF.toInt(),
                textNormal = 0xFFE6EDF3.toInt(),
                keyword = 0xFFFF7B72.toInt(),
                functionName = 0xFFD2A8FF.toInt(),
                literal = 0xFFA5D6FF.toInt(),
                comment = 0xFF8B949E.toInt(),
                operator = 0xFF79C0FF.toInt(),
                annotation = 0xFFFFA657.toInt(),
                blockLine = 0xFF21262D.toInt()
            )
            IdeColorTheme.TOKYO_NIGHT -> buildCustomScheme(
                wholeBg = 0xFF1A1B26.toInt(),
                lineNumBg = 0xFF1A1B26.toInt(),
                lineNum = 0xFF3B4261.toInt(),
                curLine = 0xFF24283B.toInt(),
                selBg = 0xFF33467C.toInt(),
                cursor = 0xFF7AA2F7.toInt(),
                textNormal = 0xFFC0CAF5.toInt(),
                keyword = 0xFFBB9AF7.toInt(),
                functionName = 0xFF7AA2F7.toInt(),
                literal = 0xFF9ECE6A.toInt(),
                comment = 0xFF565F89.toInt(),
                operator = 0xFFFF9E64.toInt(),
                annotation = 0xFFE0AF68.toInt(),
                blockLine = 0xFF292E42.toInt()
            )
            IdeColorTheme.DRACULA -> buildCustomScheme(
                wholeBg = 0xFF282A36.toInt(),
                lineNumBg = 0xFF282A36.toInt(),
                lineNum = 0xFF6272A4.toInt(),
                curLine = 0xFF343746.toInt(),
                selBg = 0xFF44475A.toInt(),
                cursor = 0xFFFF79C6.toInt(),
                textNormal = 0xFFF8F8F2.toInt(),
                keyword = 0xFFFF79C6.toInt(),
                functionName = 0xFF8BE9FD.toInt(),
                literal = 0xFFF1FA8C.toInt(),
                comment = 0xFF6272A4.toInt(),
                operator = 0xFFBD93F9.toInt(),
                annotation = 0xFF50FA7B.toInt(),
                blockLine = 0xFF44475A.toInt()
            )
            IdeColorTheme.MONOKAI_PRO -> buildCustomScheme(
                wholeBg = 0xFF2D2A2E.toInt(),
                lineNumBg = 0xFF2D2A2E.toInt(),
                lineNum = 0xFF5B595C.toInt(),
                curLine = 0xFF383539.toInt(),
                selBg = 0xFF544F55.toInt(),
                cursor = 0xFFFFD866.toInt(),
                textNormal = 0xFFFCFCFA.toInt(),
                keyword = 0xFFFF6188.toInt(),
                functionName = 0xFFA9DC76.toInt(),
                literal = 0xFFFFD866.toInt(),
                comment = 0xFF727072.toInt(),
                operator = 0xFFAB9DF2.toInt(),
                annotation = 0xFF78DCE8.toInt(),
                blockLine = 0xFF403E41.toInt()
            )
            IdeColorTheme.CATPPUCCIN_MOCHA -> buildCustomScheme(
                wholeBg = 0xFF1E1E2E.toInt(),
                lineNumBg = 0xFF1E1E2E.toInt(),
                lineNum = 0xFF585B70.toInt(),
                curLine = 0xFF313244.toInt(),
                selBg = 0xFF45475A.toInt(),
                cursor = 0xFFF5E0DC.toInt(),
                textNormal = 0xFFCDD6F4.toInt(),
                keyword = 0xFFCBA6F7.toInt(),
                functionName = 0xFF89B4FA.toInt(),
                literal = 0xFFA6E3A1.toInt(),
                comment = 0xFF6C7086.toInt(),
                operator = 0xFFFAB387.toInt(),
                annotation = 0xFFF9E2AF.toInt(),
                blockLine = 0xFF313244.toInt()
            )
            IdeColorTheme.GITHUB_LIGHT -> buildCustomScheme(
                wholeBg = 0xFFFFFFFF.toInt(),
                lineNumBg = 0xFFFFFFFF.toInt(),
                lineNum = 0xFF8C959F.toInt(),
                curLine = 0xFFF6F8FA.toInt(),
                selBg = 0xFFDDF4FF.toInt(),
                cursor = 0xFF0969DA.toInt(),
                textNormal = 0xFF1F2328.toInt(),
                keyword = 0xFFCF222E.toInt(),
                functionName = 0xFF8250DF.toInt(),
                literal = 0xFF0A3069.toInt(),
                comment = 0xFF6E7781.toInt(),
                operator = 0xFF0550AE.toInt(),
                annotation = 0xFF953800.toInt(),
                blockLine = 0xFFD0D7DE.toInt()
            )
        }
    }

    private fun buildCustomScheme(
        wholeBg: Int,
        lineNumBg: Int,
        lineNum: Int,
        curLine: Int,
        selBg: Int,
        cursor: Int,
        textNormal: Int,
        keyword: Int,
        functionName: Int,
        literal: Int,
        comment: Int,
        operator: Int,
        annotation: Int,
        blockLine: Int
    ): EditorColorScheme {
        return EditorColorScheme().apply {
            setColor(EditorColorScheme.WHOLE_BACKGROUND, wholeBg)
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, lineNumBg)
            setColor(EditorColorScheme.LINE_NUMBER, lineNum)
            setColor(EditorColorScheme.LINE_NUMBER_CURRENT, cursor)
            setColor(EditorColorScheme.CURRENT_LINE, curLine)
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selBg)
            setColor(EditorColorScheme.SELECTION_INSERT, cursor)
            setColor(EditorColorScheme.SELECTION_HANDLE, cursor)
            setColor(EditorColorScheme.TEXT_NORMAL, textNormal)
            setColor(EditorColorScheme.KEYWORD, keyword)
            setColor(EditorColorScheme.FUNCTION_NAME, functionName)
            setColor(EditorColorScheme.IDENTIFIER_NAME, textNormal)
            setColor(EditorColorScheme.IDENTIFIER_VAR, textNormal)
            setColor(EditorColorScheme.LITERAL, literal)
            setColor(EditorColorScheme.COMMENT, comment)
            setColor(EditorColorScheme.OPERATOR, operator)
            setColor(EditorColorScheme.ANNOTATION, annotation)
            setColor(EditorColorScheme.BLOCK_LINE, blockLine)
            setColor(EditorColorScheme.BLOCK_LINE_CURRENT, cursor)
        }
    }
}
