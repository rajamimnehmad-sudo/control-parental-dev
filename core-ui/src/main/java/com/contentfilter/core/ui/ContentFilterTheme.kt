package com.contentfilter.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2364AA),
    secondary = Color(0xFF3D7D4B),
    tertiary = Color(0xFF8A5A44),
    error = Color(0xFFB3261E),
    background = Color(0xFFF8F9FB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun ContentFilterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
