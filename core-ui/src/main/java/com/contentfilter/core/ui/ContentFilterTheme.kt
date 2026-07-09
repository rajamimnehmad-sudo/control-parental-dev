package com.contentfilter.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF009EE3),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDF5FF),
        onPrimaryContainer = Color(0xFF04324A),
        secondary = Color(0xFF00A650),
        secondaryContainer = Color(0xFFDFF7EA),
        tertiary = Color(0xFF364152),
        error = Color(0xFFB3261E),
        background = Color(0xFFF4F6F8),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEAF0F5),
        outline = Color(0xFFCCD5DF),
    )

@Composable
fun ContentFilterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(18.dp),
                extraLarge = RoundedCornerShape(22.dp),
            ),
        typography = MaterialTheme.typography,
        content = content,
    )
}
