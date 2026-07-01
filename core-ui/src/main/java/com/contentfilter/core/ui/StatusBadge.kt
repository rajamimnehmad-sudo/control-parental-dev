package com.contentfilter.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.ProtectionLevel

@Composable
fun StatusBadge(
    level: ProtectionLevel,
    modifier: Modifier = Modifier,
) {
    val color = when (level) {
        ProtectionLevel.Protected -> Color(0xFF2F7D32)
        ProtectionLevel.Warning -> Color(0xFF8A6500)
        ProtectionLevel.Unprotected -> Color(0xFFB3261E)
    }
    Box(
        modifier = modifier
            .background(color = color, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = level.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
