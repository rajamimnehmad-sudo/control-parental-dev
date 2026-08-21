package com.contentfilter.admin.rules

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
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
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Buscar app") },
                    leadingIcon = { ProductGlyph(ProductIcon.Search, GloshColors.Muted) },
                    singleLine = true,
                    shape = GloshShapes.Card,
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aplicaciones", style = MaterialTheme.typography.titleLarge, color = GloshColors.Graphite)
                    Text("${apps.size} total", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
                }
            }
            HeaderIconButton(onClick = onRefreshApps) {
                ProductGlyph(ProductIcon.Refresh, GloshColors.Muted, Modifier.size(22.dp), "Actualizar")
            }
            HeaderIconButton(onClick = { onSearchExpandedChanged(!searchExpanded) }) {
                ProductGlyph(ProductIcon.Search, GloshColors.Graphite, Modifier.size(22.dp), "Buscar app")
            }
        }
        if (!searchExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppQuickFilter.entries.forEach { filter ->
                    AppFilterBanner(
                        filter = filter,
                        selected = selectedFilter == filter,
                        count = if (filter == AppQuickFilter.All) apps.size else apps.count { it.matchesQuickFilter(filter) },
                        onClick = { onFilterSelected(filter) },
                    )
                }
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
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .clip(GloshShapes.Pill)
                .background(if (selected) GloshColors.Lime else GloshColors.Surface)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(filter.color))
        Text(filter.label, style = MaterialTheme.typography.labelLarge, color = GloshColors.Graphite)
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = GloshColors.Muted)
    }
}

internal enum class AppQuickFilter(
    val label: String,
    val color: Color,
) {
    All("Todas", GloshColors.Muted),
    Blocked("Bloqueadas", GloshColors.Danger),
    Limited("Con límite", GloshColors.Warning),
    Open("Abiertas", GloshColors.Positive),
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
