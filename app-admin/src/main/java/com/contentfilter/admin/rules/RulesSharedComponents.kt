package com.contentfilter.admin.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip

internal enum class DevicePanel {
    Apps,
    AppGroups,
    Web,
    Protection,
}

@Composable
internal fun SelectedDeviceHeader(
    device: UserDeviceUiState,
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
    onBack: () -> Unit,
) {
    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    text = "${device.lastSeenLabel} · ${device.appCount} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
            OutlinedButton(onClick = onBack) {
                Text("Volver")
            }
        }
        StatusChip(device.status.label, device.status.color())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedPanel == DevicePanel.Apps) {
                Button(onClick = { }) { Text("Apps") }
            } else {
                OutlinedButton(onClick = { onPanelSelected(DevicePanel.Apps) }) { Text("Apps") }
            }
            if (selectedPanel == DevicePanel.AppGroups) {
                Button(onClick = { }) { Text("Grupos") }
            } else {
                OutlinedButton(onClick = { onPanelSelected(DevicePanel.AppGroups) }) { Text("Grupos") }
            }
        }
    }
}

@Composable
internal fun UserDeviceCard(
    device: UserDeviceUiState,
    selected: Boolean,
    deleting: Boolean,
    showDelete: Boolean = true,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val indicatorColor = device.status.color()
    ProductCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(
                icon = if (device.status == UserDeviceStatus.Active) com.contentfilter.core.ui.ProductIcon.Person else com.contentfilter.core.ui.ProductIcon.ShieldAlert,
                accent = indicatorColor,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    text = "${device.lastSeenLabel} · ${device.appCount} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
                StatusChip(device.status.label, indicatorColor)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (selected) "Abierto" else "Ver",
                    style = MaterialTheme.typography.labelLarge,
                    color = GloshColors.Graphite,
                )
                if (showDelete) {
                    ProgressActionButton(
                        modifier = Modifier,
                        text = "Archivar",
                        loadingText = "Archivando…",
                        successText = "Archivado",
                        onClick = { confirmDelete = true },
                        loading = deleting,
                        enabled = !deleting,
                        tone = ActionButtonTone.Destructive,
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Archivar usuario") },
            text = {
                Text(
                    "El usuario saldrá de la lista activa. Su configuración y auditoría quedarán guardadas para poder restaurarlo después.",
                )
            },
            confirmButton = {
                ProgressActionButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !deleting,
                    modifier = Modifier,
                    text = "Archivar",
                    loadingText = "Archivando…",
                    successText = "Archivado",
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

private val UserDeviceStatus.label: String
    get() =
        when (this) {
            UserDeviceStatus.Active -> "Protegido"
            UserDeviceStatus.Unprotected -> "Requiere atención"
            UserDeviceStatus.Inactive -> "Sin conexión"
            UserDeviceStatus.Unknown -> "Verificando"
        }

@Composable
private fun UserDeviceStatus.color(): Color =
    when (this) {
        UserDeviceStatus.Active -> GloshColors.Positive
        UserDeviceStatus.Unprotected -> GloshColors.Danger
        UserDeviceStatus.Inactive -> GloshColors.Warning
        UserDeviceStatus.Unknown -> GloshColors.Muted
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
