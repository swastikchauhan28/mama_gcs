package com.mamadrones.gcs.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D8A6), secondary = Color(0xFFA5D6FF), tertiary = Color(0xFFFFC978),
    surface = Color(0xFF101513), background = Color(0xFF101513), error = Color(0xFFFFB4AB)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF006C43), secondary = Color(0xFF1A5F86), tertiary = Color(0xFF765900)
)

@Composable
fun MamaGcsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
