package com.contentfilter.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme =
    lightColorScheme(
        primary = GloshColors.Graphite,
        onPrimary = Color.White,
        primaryContainer = GloshColors.SurfaceMuted,
        onPrimaryContainer = GloshColors.Graphite,
        secondary = GloshColors.Lime,
        onSecondary = GloshColors.Graphite,
        secondaryContainer = GloshColors.LimeSoft,
        onSecondaryContainer = GloshColors.Graphite,
        tertiary = GloshColors.Positive,
        error = GloshColors.Danger,
        background = GloshColors.Bone,
        onBackground = GloshColors.Graphite,
        surface = GloshColors.Surface,
        onSurface = GloshColors.Graphite,
        surfaceVariant = GloshColors.SurfaceMuted,
        onSurfaceVariant = GloshColors.GraphiteSoft,
        outline = GloshColors.Line,
        outlineVariant = GloshColors.Line,
    )

private val GloshTypography =
    Typography(
        displayLarge = gloshTextStyle(48, FontWeight.Bold, -1.4f),
        displayMedium = gloshTextStyle(40, FontWeight.Bold, -1.1f),
        displaySmall = gloshTextStyle(34, FontWeight.Bold, -0.9f),
        headlineLarge = gloshTextStyle(32, FontWeight.Bold, -0.8f),
        headlineMedium = gloshTextStyle(28, FontWeight.SemiBold, -0.7f),
        headlineSmall = gloshTextStyle(24, FontWeight.SemiBold, -0.5f),
        titleLarge = gloshTextStyle(20, FontWeight.SemiBold, -0.25f),
        titleMedium = gloshTextStyle(17, FontWeight.SemiBold, -0.1f),
        titleSmall = gloshTextStyle(15, FontWeight.SemiBold, 0f),
        bodyLarge = gloshTextStyle(17, FontWeight.Normal, 0f),
        bodyMedium = gloshTextStyle(15, FontWeight.Normal, 0f),
        bodySmall = gloshTextStyle(13, FontWeight.Normal, 0.05f),
        labelLarge = gloshTextStyle(14, FontWeight.SemiBold, 0f),
        labelMedium = gloshTextStyle(12, FontWeight.Medium, 0.05f),
        labelSmall = gloshTextStyle(11, FontWeight.Medium, 0.1f),
    )

private fun gloshTextStyle(
    sizeSp: Int,
    weight: FontWeight,
    letterSpacingSp: Float,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    letterSpacing = letterSpacingSp.sp,
)

@Composable
fun ContentFilterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
        typography = GloshTypography,
        content = content,
    )
}
