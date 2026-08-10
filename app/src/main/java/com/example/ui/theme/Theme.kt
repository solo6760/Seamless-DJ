package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WarmOrange,
    onPrimary = Color.White,
    primaryContainer = WarmOrangeContainer,
    onPrimaryContainer = Color(0xFFE65100),
    secondary = StatusCyan,
    onSecondary = Color.White,
    tertiary = StatusViolet,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFE0E0E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmOrange,
    onPrimary = Color.Black,
    primaryContainer = WarmOrange.copy(alpha = 0.2f),
    onPrimaryContainer = WarmOrange,
    secondary = StatusCyan,
    onSecondary = Color.Black,
    tertiary = StatusViolet,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF333333)
)

val ColorScheme.textPrimary: Color
    get() = onSurface

val ColorScheme.textSecondary: Color
    get() = onSurfaceVariant

val ColorScheme.textMuted: Color
    get() = if (background == DarkBackground) DarkTextMuted else LightTextMuted

val ColorScheme.cardBorder: Color
    get() = outline

@Composable
fun SeamlessDjTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
