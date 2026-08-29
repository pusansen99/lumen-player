package com.lumen.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cinematic-dark tokens (see the design canvas style tile).
private val Base = Color(0xFF0B0B0D)
private val Surface1 = Color(0xFF151518)
private val Surface2 = Color(0xFF1E1E22)
private val TextPrimary = Color(0xFFF5F5F7)
private val TextSecondary = Color(0xFFA1A1AA)
private val AccentBlue = Color(0xFF4C8DFF)

private val LumenDarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color(0xFF08131F),
    background = Base,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
)

private val LumenLightColors = lightColorScheme(
    primary = AccentBlue,
)

@Composable
fun LumenTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Phase 1 commits to the cinematic-dark look regardless of system setting.
    MaterialTheme(
        colorScheme = if (useDarkTheme) LumenDarkColors else LumenDarkColors,
        content = content,
    )
}
