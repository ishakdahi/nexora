package com.nexora.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF1A1470),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceVariant = Color(0xFFE8EAF6),
    surfaceContainer = Color.White,
    surfaceContainerHighest = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5A9FF),
    onPrimary = Color(0xFF1A1470),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF0F1220),
    surface = Color(0xFF0F1220),
    surfaceVariant = Color(0xFF23273D),
    surfaceContainer = Color(0xFF171A2B),
    surfaceContainerHighest = Color(0xFF1D2136)
)

@Composable
fun NexoraTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
