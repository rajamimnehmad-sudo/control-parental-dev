package com.contentfilter.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ProductHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = GloshColors.Graphite)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GloshColors.Muted,
                )
            }
            badge?.let {
                GloshStatusPill(
                    text = it,
                    color = GloshColors.Positive,
                )
            }
        }
    }
}

@Composable
fun ProductCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GloshSurfaceCard(
        modifier = modifier,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun ProductSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            count?.let {
                Text(
                    "$it total",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, color = GloshColors.Graphite)
            }
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    GloshStatusPill(text = text, color = color, modifier = modifier)
}

@Composable
fun FeedbackBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    PremiumFeedbackBanner(text = text, modifier = modifier, isError = isError)
}
