package com.contentfilter.admin.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.ui.GloshColors

internal enum class DevicePanel {
    Apps,
    AppGroups,
    Web,
    Protection,
}

@Composable
internal fun SectionHeader(
    title: String,
    count: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text("$count", style = MaterialTheme.typography.labelLarge, color = GloshColors.Muted)
        }
        HorizontalDivider(color = GloshColors.Line)
    }
}

@Composable
internal fun SectionActionHeader(
    title: String,
    count: Int,
    actionText: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$count", style = MaterialTheme.typography.labelLarge, color = GloshColors.Muted)
                OutlinedButton(onClick = onAction) { Text(actionText) }
            }
        }
        HorizontalDivider(color = GloshColors.Line)
    }
}

@Composable
internal fun EmptySectionText(text: String) {
    Text(
        modifier = Modifier.padding(vertical = 6.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = GloshColors.Muted,
    )
}

internal fun RuleAction.displayName(): String =
    when (this) {
        RuleAction.Allow -> "Permitir"
        RuleAction.Block -> "Bloquear"
        RuleAction.Warn -> "Advertir"
        RuleAction.RequestAuthorization -> "Requiere autorización"
    }

internal fun RuleScope.displayName(): String =
    when (this) {
        RuleScope.App -> "Aplicación"
        RuleScope.Domain -> "Sitio"
        RuleScope.Category -> "Categoría"
        RuleScope.Global -> "General"
    }

internal val SearchEngineDomainsForUi =
    SearchEngineCatalog.searchSupportDomains
        .plus(SearchEngineDomains)
        .plus(SecureDnsDomains)
