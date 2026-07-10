package com.contentfilter.admin.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.PolicyRule

@Composable
internal fun RuleCard(
    rule: PolicyRule,
    dailyLimitMinutes: Int? = null,
    enabled: Boolean = true,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(rule.target, style = MaterialTheme.typography.titleMedium)
                    Text(rule.scope.displayName(), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = rule.enabled,
                    enabled = enabled,
                    onCheckedChange = { onToggle() },
                )
            }
            Text("Acción: ${rule.action.displayName()}")
            if (dailyLimitMinutes != null) {
                Text("Límite diario: $dailyLimitMinutes min")
            }
            Text("Estado: ${if (rule.enabled) "Activada" else "Desactivada"}")
            OutlinedButton(onClick = { confirmDelete = true }, enabled = enabled) {
                Text("Eliminar")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar regla") },
            text = { Text("Esta regla se eliminará.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Eliminar")
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
