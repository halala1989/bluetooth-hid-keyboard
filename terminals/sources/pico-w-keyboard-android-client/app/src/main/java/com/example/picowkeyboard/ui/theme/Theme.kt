package com.example.picowkeyboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF1565C0)
private val BlueOnPrimary = Color(0xFFFFFFFF)
private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
)

@Composable
fun PicoWKeyboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
