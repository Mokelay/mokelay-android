package com.mokelay.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0A8FF0),
    secondary = Color(0xFF0F3028),
    tertiary = Color(0xFFD7F66B),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF74BFFF),
    secondary = Color(0xFFD7F66B),
    tertiary = Color(0xFF0F3028),
)

@Composable
fun MokelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
