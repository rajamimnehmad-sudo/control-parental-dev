package com.contentfilter.admin.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
internal fun AppsToolbar(
    apps: List<AppControlUiState>,
    selectedFilter: AppQuickFilter,
    searchQuery: String,
    searchExpanded: Boolean,
    onFilterSelected: (AppQuickFilter) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    label = { Text("Buscar app") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aplicaciones",
                        style = MaterialTheme.typography.titleLarge,
                        color = HeaderInk,
                    )
                    Text(
                        text = "${apps.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = HeaderMuted,
                    )
                }
            }
            HeaderIconButton(onClick = onRefreshApps) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar",
                    tint = HeaderMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            HeaderIconButton(
                onClick = { onSearchExpandedChanged(!searchExpanded) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar app",
                    tint = HeaderInk,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (!searchExpanded) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppFilterBanner(
                    filter = AppQuickFilter.All,
                    selected = selectedFilter == AppQuickFilter.All,
                    count = apps.size,
                    onClick = { onFilterSelected(AppQuickFilter.All) },
                )
                AppFilterBanner(
                    filter = AppQuickFilter.Blocked,
                    selected = selectedFilter == AppQuickFilter.Blocked,
                    count = apps.count { it.matchesQuickFilter(AppQuickFilter.Blocked) },
                    onClick = { onFilterSelected(AppQuickFilter.Blocked) },
                )
                AppFilterBanner(
                    filter = AppQuickFilter.Limited,
                    selected = selectedFilter == AppQuickFilter.Limited,
                    count = apps.count { it.matchesQuickFilter(AppQuickFilter.Limited) },
                    onClick = { onFilterSelected(AppQuickFilter.Limited) },
                )
                AppFilterBanner(
                    filter = AppQuickFilter.Open,
                    selected = selectedFilter == AppQuickFilter.Open,
                    count = apps.count { it.matchesQuickFilter(AppQuickFilter.Open) },
                    onClick = { onFilterSelected(AppQuickFilter.Open) },
                )
            }
        }
    }
}

@Composable
private fun AppFilterBanner(
    filter: AppQuickFilter,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    val color = filter.color
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .width(142.dp)
                .clip(shape)
                .background(if (selected) Color(0xFFE9EEF0) else Color.White, shape)
                .border(
                    width = 1.dp,
                    color = if (selected) Color(0xFFB7C0C7) else Color(0xFFE1E7EA),
                    shape = shape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(color),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = HeaderInk,
                )
                Text(
                    text = "$count apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = HeaderMuted,
                )
            }
        }
    }
}

internal enum class AppQuickFilter(
    val label: String,
    val color: Color,
) {
    All("Todas", Color(0xFF64748B)),
    Blocked("Bloqueadas", Color(0xFFC62828)),
    Limited("Con limite", Color(0xFFF9A825)),
    Open("Abiertas", Color(0xFF2E7D32)),
}

internal fun AppControlUiState.matchesQuickFilter(filter: AppQuickFilter): Boolean =
    when (filter) {
        AppQuickFilter.All -> true
        AppQuickFilter.Blocked -> !confirmedAllowed && extraTimeRemainingMinutes == null
        AppQuickFilter.Limited -> extraTimeRemainingMinutes != null || dailyLimitMinutes != null
        AppQuickFilter.Open -> confirmedAllowed && extraTimeRemainingMinutes == null && dailyLimitMinutes == null
    }

@Composable
internal fun CompactActionBanner(
    message: String,
    isError: Boolean,
) {
    FeedbackBanner(text = message, isError = isError)
}
