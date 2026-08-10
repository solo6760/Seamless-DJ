package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DjPartyColorScheme = darkColorScheme(
    primary = NeonViolet,
    secondary = NeonMagenta,
    tertiary = NeonCyan,
    background = DjBackground,
    surface = DjSurface,
    surfaceVariant = DjSurfaceVariant,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DjCardBorder
)

@Composable
fun SeamlessDjTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DjPartyColorScheme,
        typography = Typography,
        content = content
    )
}
