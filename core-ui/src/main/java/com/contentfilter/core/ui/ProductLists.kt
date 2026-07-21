package com.contentfilter.core.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ProductListSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
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
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .then(
                        if (onClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = onClick)
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(content = headline)
                supporting?.let { Row(content = it) }
            }
            trailing?.invoke()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (leading == null) 14.dp else 66.dp),
                color = ProductListDivider,
            )
        }
    }
}

private val ProductListDivider = Color(0xFFE6EBEF)
