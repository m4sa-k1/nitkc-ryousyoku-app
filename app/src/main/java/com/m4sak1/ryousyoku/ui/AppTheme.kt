package com.m4sak1.ryousyoku.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    background = Color(0xFFFFF8F0),
    surface = Color.White,
    primary = Color(0xFFFF8C42),
    onSurface = Color(0xFF4A4A4A),
    onBackground = Color(0xFF4A4A4A)
)

val DarkColorScheme = darkColorScheme(
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    primary = Color(0xFFFF8C42),
    onSurface = Color(0xFFDDDDDD),
    onBackground = Color(0xFFDDDDDD)
)

@Composable
fun RyousyokuTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (isDarkMode) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
