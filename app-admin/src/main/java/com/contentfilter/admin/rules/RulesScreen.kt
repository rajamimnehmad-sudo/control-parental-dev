package com.contentfilter.admin.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction

@Composable
fun RulesRoute(
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulesScreen(
        state = state,
        onTargetChanged = viewModel::onTargetChanged,
        onActionChanged = viewModel::onActionChanged,
        onCreateApp = viewModel::createAppRule,
        onCreateDomain = viewModel::createDomainRule,
        onToggle = viewModel::toggle,
    )
}

@Composable
private fun RulesScreen(
    state: RulesUiState,
    onTargetChanged: (String) -> Unit,
    onActionChanged: (RuleAction) -> Unit,
    onCreateApp: () -> Unit,
    onCreateDomain: () -> Unit,
    onToggle: (PolicyRule) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Reglas", style = MaterialTheme.typography.headlineSmall)
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.target,
            onValueChange = onTargetChanged,
            label = { Text("Paquete de aplicación o dominio") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RuleAction.Allow, RuleAction.Block, RuleAction.RequestAuthorization).forEach { action ->
                FilterChip(
                    selected = state.selectedAction == action,
                    onClick = { onActionChanged(action) },
                    label = { Text(action.displayName()) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreateApp) { Text("Paquete de aplicación") }
            OutlinedButton(onClick = onCreateDomain) { Text("Dominio") }
        }
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.rules, key = { it.id }) { rule ->
                RuleCard(rule = rule, onToggle = { onToggle(rule) })
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: PolicyRule,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${rule.scope}: ${rule.target}", style = MaterialTheme.typography.titleMedium)
            Text("Acción: ${rule.action.displayName()}")
            Text("Estado: ${if (rule.enabled) "Activa" else "Inactiva"}")
            OutlinedButton(onClick = onToggle) {
                Text(if (rule.enabled) "Desactivar" else "Activar")
            }
        }
    }
}

private fun RuleAction.displayName(): String =
    when (this) {
        RuleAction.Allow -> "Permitir"
        RuleAction.Block -> "Bloquear"
        RuleAction.Warn -> "Advertir"
        RuleAction.RequestAuthorization -> "Requiere autorización"
    }
