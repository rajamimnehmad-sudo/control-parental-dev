package com.contentfilter.admin.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip

@Composable
internal fun AppGroupsPanel(
    state: RulesUiState,
    onGroupNameChanged: (String) -> Unit,
    onGroupMinutesChanged: (String) -> Unit,
    onGroupAppToggled: (String, Boolean) -> Unit,
    onSaveAppGroup: () -> Unit,
    onEditAppGroup: (String) -> Unit,
    onCancelAppGroupEdit: () -> Unit,
    onDeleteAppGroup: (String) -> Unit,
) {
    val editingGroupId = state.editingGroupId
    val usedPackages =
        state.appGroups
            .filter { it.id != editingGroupId }
            .flatMap { group -> group.appPackages.map { packageName -> packageName to group.name } }
            .toMap()
    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Apps en grupo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tiempo compartido por grupo · reinicia 12 PM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip("${state.appGroups.size}", MaterialTheme.colorScheme.primary)
        }
        if (state.appGroups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.appGroups.forEach { group ->
                    AppGroupSummaryCard(
                        group = group,
                        deleting = group.id in state.pendingAppGroupDeleteIds,
                        onEdit = { onEditAppGroup(group.id) },
                        onDelete = { onDeleteAppGroup(group.id) },
                    )
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editingGroupId == null) "Nuevo grupo" else "Editando grupo",
                style = MaterialTheme.typography.labelLarge,
            )
            if (editingGroupId != null) {
                OutlinedButton(onClick = onCancelAppGroupEdit) {
                    Text("Cancelar")
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.groupName,
            onValueChange = onGroupNameChanged,
            label = { Text("Nombre del grupo") },
            placeholder = { Text("Entretenimiento") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.groupMinutes,
            onValueChange = onGroupMinutesChanged,
            label = { Text("Tiempo total diario") },
            placeholder = { Text("240 minutos") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text("Apps disponibles", style = MaterialTheme.typography.labelLarge)
        val selectedPackages = state.groupSelectedPackages
        val selectedApps = state.appControls.filter { it.packageName in selectedPackages }
        val selectableApps = state.appControls.filter { it.packageName !in selectedPackages }
        if (state.appControls.isEmpty()) {
            Text(
                "Actualizá apps o buscá el usuario para armar el grupo.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectableApps.forEach { app ->
                    val usedByGroup = usedPackages[app.packageName]
                    GroupAppPickerRow(
                        app = app,
                        actionText = if (usedByGroup == null) "+" else "En grupo",
                        helperText = usedByGroup?.let { "Ya está en $it" },
                        enabled = usedByGroup == null,
                        onClick = { onGroupAppToggled(app.packageName, true) },
                    )
                }
            }
        }
        Text("Cajón de apps (${selectedApps.size})", style = MaterialTheme.typography.labelLarge)
        if (selectedApps.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                        .padding(12.dp),
            ) {
                Text(
                    "Agregá apps con +. Todas compartirán el tiempo total diario.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedApps.forEach { app ->
                    GroupAppPickerRow(
                        app = app,
                        actionText = "x",
                        helperText = null,
                        onClick = { onGroupAppToggled(app.packageName, false) },
                    )
                }
            }
        }
        ProgressActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSaveAppGroup,
            loading = state.groupSaving,
            loadingText = if (editingGroupId == null) "Guardando..." else "Actualizando...",
            successText = if (editingGroupId == null) "Grupo guardado" else "Grupo actualizado",
            text = if (editingGroupId == null) "Guardar grupo" else "Actualizar grupo",
        )
    }
}

@Composable
private fun GroupAppPickerRow(
    app: AppControlUiState,
    actionText: String,
    helperText: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(app.appName, app.iconBase64)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.bodyMedium)
            Text(helperText ?: app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(actionText)
        }
    }
}

@Composable
private fun AppGroupSummaryCard(
    group: AppGroupUiState,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${group.appPackages.size} apps · ${group.limitMinutes} min · ${group.resetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !deleting,
                ) {
                    Text("Editar")
                }
                ProgressActionButton(
                    modifier = Modifier,
                    text = "Borrar",
                    loadingText = "Borrando...",
                    successText = "Borrado",
                    loading = deleting,
                    enabled = !deleting,
                    onClick = { confirmDelete = true },
                    tone = ActionButtonTone.Destructive,
                )
            }
        }
        group.appPackages.take(4).forEach { packageName ->
            Text(packageName, style = MaterialTheme.typography.bodySmall)
        }
        if (group.appPackages.size > 4) {
            Text("+${group.appPackages.size - 4} más", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar grupo") },
            text = { Text("Las apps de este grupo volverán a sus reglas individuales.") },
            confirmButton = {
                ProgressActionButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier,
                    text = "Borrar",
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
