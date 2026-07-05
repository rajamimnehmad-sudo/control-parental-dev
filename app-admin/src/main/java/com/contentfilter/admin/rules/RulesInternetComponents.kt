package com.contentfilter.admin.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyRule

@Composable
internal fun InternetModeCard(
    blocked: Boolean,
    searchEnginesAllowed: Boolean,
    internetSaving: Boolean,
    webModeUpdating: Boolean,
    searchEnginesUpdating: Boolean,
    onBlockedChanged: (Boolean) -> Unit,
    onSearchEnginesAllowedChanged: (Boolean) -> Unit,
) {
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
                    Text("Modo web", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text =
                            if (blocked) {
                                "Bloquear todo excepto lista blanca."
                            } else {
                                "Internet abierto."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (internetSaving) {
                        Text("Guardando...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(
                    checked = blocked,
                    enabled = !internetSaving && !webModeUpdating,
                    onCheckedChange = onBlockedChanged,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Permitir buscadores", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (searchEnginesAllowed) "Estado: permitidos" else "Estado: bloqueados",
                        color = if (searchEnginesAllowed) SearchAllowedColor else SearchBlockedColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Permite ver resultados de Google, Bing, Yahoo y DuckDuckGo cuando el modo web bloquea todo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = searchEnginesAllowed,
                    enabled = blocked && !internetSaving && !webModeUpdating && !searchEnginesUpdating,
                    onCheckedChange = onSearchEnginesAllowedChanged,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = SearchAllowedColor,
                            checkedTrackColor = SearchAllowedTrackColor,
                            uncheckedThumbColor = SearchBlockedColor,
                            uncheckedTrackColor = SearchBlockedTrackColor,
                        ),
                )
            }
        }
    }
}

private val SearchAllowedColor = Color(0xFF2E7D32)
private val SearchAllowedTrackColor = Color(0xFFA5D6A7)
private val SearchBlockedColor = Color(0xFFC62828)
private val SearchBlockedTrackColor = Color(0xFFFFCDD2)

@Composable
internal fun AllowDomainEditorCard(
    domain: String,
    minutes: String,
    enabled: Boolean,
    saving: Boolean,
    onDomainChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onAllow: () -> Unit,
    onAllowWithLimit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Lista blanca", style = MaterialTheme.typography.titleMedium)
                if (saving) {
                    Text("Guardando...", style = MaterialTheme.typography.bodySmall)
                }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = domain,
                onValueChange = onDomainChanged,
                label = { Text("Sitio permitido") },
                singleLine = true,
                enabled = enabled,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = minutes,
                onValueChange = onMinutesChanged,
                label = { Text("Minutos por día opcional") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow, enabled = enabled) { Text("Permitir") }
                OutlinedButton(onClick = onAllowWithLimit, enabled = enabled) { Text("Permitir con tiempo") }
            }
        }
    }
}

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
            text = { Text("Esta regla se eliminará de este entorno DEV.") },
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

@Composable
internal fun DomainLimitCard(
    limit: DailyLimit,
    enabled: Boolean = true,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(limit.target, style = MaterialTheme.typography.titleMedium)
            Text("Límite diario: ${limit.limitMinutes} min")
            Text("Estado: ${if (limit.enabled) "Activado" else "Desactivado"}")
            OutlinedButton(onClick = { confirmDelete = true }, enabled = enabled) {
                Text("Eliminar límite")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar límite") },
            text = { Text("Este límite de dominio se eliminará de este entorno DEV.") },
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
