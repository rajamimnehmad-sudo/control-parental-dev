package com.contentfilter.admin.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Grupos de apps", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    "Las apps del grupo comparten un mismo tiempo diario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
            StatusChip("${state.appGroups.size}", GloshColors.Graphite)
        }
        if (state.appGroups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.appGroups.forEach { group ->
                    AppGroupSummaryCard(
                        group = group,
                        apps = state.appControls,
                        deleting = group.id in state.pendingAppGroupDeleteIds,
                        onEdit = { onEditAppGroup(group.id) },
                        onDelete = { onDeleteAppGroup(group.id) },
                    )
                }
            }
        }
        HorizontalDivider(color = GloshColors.Line)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editingGroupId == null) "Nuevo grupo" else "Editando grupo",
                style = MaterialTheme.typography.labelLarge,
                color = GloshColors.Graphite,
            )
            if (editingGroupId != null) {
                OutlinedButton(onClick = onCancelAppGroupEdit) { Text("Cancelar") }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.groupName,
            onValueChange = onGroupNameChanged,
            label = { Text("Nombre") },
            placeholder = { Text("Entretenimiento") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.groupMinutes,
            onValueChange = onGroupMinutesChanged,
            label = { Text("Tiempo diario compartido") },
            placeholder = { Text("240 minutos") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text("Agregar apps", style = MaterialTheme.typography.labelLarge, color = GloshColors.Graphite)
        val selectedPackages = state.groupSelectedPackages
        val selectedApps = state.appControls.filter { it.packageName in selectedPackages }
        val selectableApps = state.appControls.filter { it.packageName !in selectedPackages }
        if (state.appControls.isEmpty()) {
            Text(
                "Actualizá la lista de apps para armar el grupo.",
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectableApps.forEach { app ->
                    val usedByGroup = usedPackages[app.packageName]
                    GroupAppPickerRow(
                        app = app,
                        actionText = if (usedByGroup == null) "Agregar" else "En grupo",
                        helperText = usedByGroup?.let { "Ya está en $it" },
                        enabled = usedByGroup == null,
                        onClick = { onGroupAppToggled(app.packageName, true) },
                    )
                }
            }
        }
        Text("Apps del grupo (${selectedApps.size})", style = MaterialTheme.typography.labelLarge, color = GloshColors.Graphite)
        if (selectedApps.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(GloshColors.LimeSoft, GloshShapes.Small)
                        .padding(12.dp),
            ) {
                Text(
                    "Agregá apps. Todas compartirán el tiempo diario del grupo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Graphite,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedApps.forEach { app ->
                    GroupAppPickerRow(
                        app = app,
                        actionText = "Quitar",
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
            loadingText = if (editingGroupId == null) "Guardando…" else "Actualizando…",
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(app.appName, app.iconBase64)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Graphite)
                helperText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted) }
            }
            OutlinedButton(enabled = enabled, onClick = onClick) { Text(actionText) }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = GloshColors.Line)
    }
}

@Composable
private fun AppGroupSummaryCard(
    group: AppGroupUiState,
    apps: List<AppControlUiState>,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val appsByPackage = remember(apps) { apps.associateBy(AppControlUiState::packageName) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleSmall, color = GloshColors.Graphite)
                Text(
                    "${group.appPackages.size} apps · ${group.limitMinutes} min por día",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, enabled = !deleting) { Text("Editar") }
                ProgressActionButton(
                    modifier = Modifier,
                    text = "Borrar",
                    loadingText = "Borrando…",
                    successText = "Borrado",
                    loading = deleting,
                    enabled = !deleting,
                    onClick = { confirmDelete = true },
                    tone = ActionButtonTone.Destructive,
                )
            }
        }
        val names = group.appPackages.map { appsByPackage[it]?.appName ?: "App" }
        Text(names.take(4).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        if (names.size > 4) {
            Text("+${names.size - 4} más", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        }
        HorizontalDivider(color = GloshColors.Line)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar grupo") },
            text = { Text("Las apps de este grupo volverán a usar sus reglas individuales.") },
            confirmButton = {
                ProgressActionButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier,
                    text = "Borrar",
                    loadingText = "Borrando…",
                    successText = "Borrado",
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}
