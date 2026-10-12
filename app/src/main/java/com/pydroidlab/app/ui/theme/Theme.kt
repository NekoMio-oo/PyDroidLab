package com.pydroidlab.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import com.pydroidlab.app.ui.settings.ThemeMode

/**
 * PyDroidLab 的 Material 3 主题。
 *
 * - 跟随 / 强制浅色 / 强制深色 三种模式由 [ThemeMode] 控制；
 * - Android 12+ 可选动态取色（[dynamicColor]）；
 * - 等宽字体族集中在这里定义，编辑器与终端复用同一套。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF00696E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9DF0F6),
    onSecondaryContainer = Color(0xFF002021),
    tertiary = Color(0xFFB02F1E),
    onTertiary = Color.White,
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF757780),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF003EAA),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF81D3D9),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFF9DF0F6),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF690100),
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF12131A),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/** 编辑器 / 终端 / 控制台共用等宽字体。 */
val MonoFontFamily: FontFamily = FontFamily.Monospace

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp)
    )
}

@Composable
fun PyDroidLabTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
