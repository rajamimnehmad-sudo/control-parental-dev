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
import androidx.compose.material3.Card
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

internal enum class DevicePanel {
    Apps,
    Internet,
}

@Composable
internal fun SelectedDeviceHeader(
    device: UserDeviceUiState,
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
    onBack: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${device.status.label} | ${device.lastSeenLabel}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onBack) {
                    Text("Volver")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedPanel == DevicePanel.Apps) {
                    Button(onClick = { }) {
                        Text("Apps")
                    }
                } else {
                    OutlinedButton(onClick = { onPanelSelected(DevicePanel.Apps) }) {
                        Text("Apps")
                    }
                }
                if (selectedPanel == DevicePanel.Internet) {
                    Button(onClick = { }) {
                        Text("Internet")
                    }
                } else {
                    OutlinedButton(onClick = { onPanelSelected(DevicePanel.Internet) }) {
                        Text("Internet")
                    }
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val indicatorColor =
        when (device.status) {
            UserDeviceStatus.Active -> Color(0xFF2E7D32)
            UserDeviceStatus.Inactive -> MaterialTheme.colorScheme.error
            UserDeviceStatus.Unknown -> MaterialTheme.colorScheme.outline
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                    text = "${device.status.label} | ${device.lastSeenLabel} | ${device.appCount} apps",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (selected) "Elegido" else "Ver",
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !deleting,
                ) {
                    Text(if (deleting) "Borrando" else "Borrar")
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
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !deleting,
                ) {
                    Text("Borrar definitivo")
                }
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
            UserDeviceStatus.Inactive -> "Desconectado"
            UserDeviceStatus.Unknown -> "Desconocido"
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
    setOf(
        "gstatic.com",
        "googleapis.com",
        "googleusercontent.com",
        "yimg.com",
        "bing.net",
        "duck.com",
    )
        .plus(SearchEngineDomains)
