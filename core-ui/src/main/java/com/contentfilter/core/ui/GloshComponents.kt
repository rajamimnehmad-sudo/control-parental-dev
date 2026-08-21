package com.contentfilter.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GloshWordmark(modifier: Modifier = Modifier) {
    Text(
        text = "glosh",
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.1).sp,
        color = GloshColors.Graphite,
        maxLines = 1,
    )
}

@Composable
fun GloshSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = GloshColors.Muted,
    )
}

@Composable
fun GloshStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier
                .background(color.copy(alpha = 0.10f), GloshShapes.Pill)
                .padding(horizontal = 11.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
fun GloshIconBubble(
    icon: ProductIcon,
    modifier: Modifier = Modifier,
    accent: Color = GloshColors.Lime,
    contentDescription: String? = null,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .background(accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ProductGlyph(
            icon = icon,
            color = GloshColors.Graphite,
            contentDescription = contentDescription,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
fun GloshSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = GloshColors.Surface)
    val border = BorderStroke(1.dp, GloshColors.Line)
    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = GloshShapes.Card,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(GloshSpacing.Card),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            shape = GloshShapes.Card,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(GloshSpacing.Card),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
fun GloshNavigationGlyph(
    icon: ProductIcon,
    selected: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .background(
                    if (selected) GloshColors.Lime else Color.Transparent,
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        ProductGlyph(
            icon = icon,
            color = if (selected) GloshColors.Graphite else GloshColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun GloshInfoRow(
    title: String,
    subtitle: String,
    icon: ProductIcon,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GloshSurfaceCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
            trailing?.invoke()
        }
    }
}
