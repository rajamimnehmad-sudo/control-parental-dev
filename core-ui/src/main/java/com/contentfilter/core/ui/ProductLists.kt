package com.contentfilter.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProductListSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = GloshShapes.Card,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(content = content)
    }
}

@Composable
fun ProductListRow(
    headline: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(GloshColors.Surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp)
                    .then(
                        if (onClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = onClick)
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(content = headline)
                supporting?.let { Row(content = it) }
            }
            trailing?.invoke()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (leading == null) 16.dp else 68.dp),
                color = GloshColors.Line,
            )
        }
    }
}
