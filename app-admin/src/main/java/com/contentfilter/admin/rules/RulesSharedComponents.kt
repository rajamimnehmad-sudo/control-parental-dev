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
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.StatusChip
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog

internal enum class DevicePanel {
    Apps,
    AppGroups,
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
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${device.lastSeenLabel} · ${device.appCount} apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onBack) {
                    Text("Volver")
                }
            }
            StatusChip(device.status.label, device.status.color())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedPanel == DevicePanel.Apps) {
                    Button(onClick = { }) {
                        Text("Aplicaciones")
                    }
                } else {
                    OutlinedButton(onClick = { onPanelSelected(DevicePanel.Apps) }) {
                        Text("Aplicaciones")
                    }
                }
                if (selectedPanel == DevicePanel.AppGroups) {
                    Button(onClick = { }) {
                        Text("Apps en grupo")
                    }
                } else {
                    OutlinedButton(onClick = { onPanelSelected(DevicePanel.AppGroups) }) {
                    Text("Apps en grupo")
                }
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
    val indicatorColor =
        when (device.status) {
            UserDeviceStatus.Active -> Color(0xFF2E7D32)
            UserDeviceStatus.Unprotected -> MaterialTheme.colorScheme.error
            UserDeviceStatus.Inactive -> Color(0xFFF9A825)
            UserDeviceStatus.Unknown -> MaterialTheme.colorScheme.outline
        }
    ProductCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${device.lastSeenLabel} · ${device.appCount} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(device.status.label, indicatorColor)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (selected) "Elegido" else "Ver",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (showDelete) {
                    ProgressActionButton(
                        modifier = Modifier,
                        text = "Borrar",
                        loadingText = "Borrando...",
                        successText = "Borrado",
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
            title = { Text("Borrar dispositivo") },
            text = {
                Text(
                    "Esto borra definitivamente el dispositivo, sus apps detectadas, activaciones y solicitudes asociadas.",
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
                    text = "Borrar definitivo",
                    loadingText = "Borrando...",
                    successText = "Borrado",
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private val UserDeviceStatus.label: String
    get() =
        when (this) {
            UserDeviceStatus.Active -> "Activo"
            UserDeviceStatus.Unprotected -> "Protección caída"
            UserDeviceStatus.Inactive -> "Desconectado"
            UserDeviceStatus.Unknown -> "Desconocido"
        }

@Composable
private fun UserDeviceStatus.color(): Color =
    when (this) {
        UserDeviceStatus.Active -> Color(0xFF00A650)
        UserDeviceStatus.Unprotected -> MaterialTheme.colorScheme.error
        UserDeviceStatus.Inactive -> Color(0xFFF9A825)
        UserDeviceStatus.Unknown -> MaterialTheme.colorScheme.outline
    }

@Composable
internal fun SectionHeader(
    title: String,
    count: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider()
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$count", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun EmptySectionText(text: String) {
    Text(
        modifier = Modifier.padding(vertical = 4.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        RuleScope.Domain -> "Dominio"
        RuleScope.Category -> "Categoría"
        RuleScope.Global -> "Global"
    }

internal val SearchEngineDomainsForUi =
    SearchEngineCatalog.searchSupportDomains
        .plus(SearchEngineDomains)
        .plus(SecureDnsDomains)
