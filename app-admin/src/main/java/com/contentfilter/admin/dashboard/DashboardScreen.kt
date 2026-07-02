package com.contentfilter.admin.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onClearLocalRequests = viewModel::clearLocalRequests,
        onClearRemoteRequests = viewModel::clearRemoteRequests,
        onClearAllRequests = viewModel::clearAllRequests,
        onClearRules = viewModel::clearRules,
        onClearExtraTimeGrants = viewModel::clearExtraTimeGrants,
        onClearDuplicateDevices = viewModel::clearDuplicateDevices,
        onResetDev = viewModel::resetDev,
    )
}

@Composable
private fun DashboardScreen(
    state: DashboardUiState,
    onClearLocalRequests: () -> Unit,
    onClearRemoteRequests: () -> Unit,
    onClearAllRequests: () -> Unit,
    onClearRules: () -> Unit,
    onClearExtraTimeGrants: () -> Unit,
    onClearDuplicateDevices: () -> Unit,
    onResetDev: () -> Unit,
) {
    var pendingAction by remember { mutableStateOf<DevAction?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Panel administrador", style = MaterialTheme.typography.headlineSmall)
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        Text("Dispositivos: ${state.deviceCount}")
        Text("Solicitudes pendientes: ${state.pendingRequests}")
        Text("Sincronizacion: ${state.syncState}")
        Text("Estado general: ${state.systemState}")
        Text("Ultima sincronizacion: ${state.lastSync}")
        if (state.showDevTools) {
            Text("Herramientas DEV", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { pendingAction = DevAction.ClearLocalRequests },
                enabled = !state.devToolsBusy,
            ) {
                Text("Borrar solicitudes locales")
            }
            Button(
                onClick = { pendingAction = DevAction.ClearRemoteRequests },
                enabled = !state.devToolsBusy,
            ) {
                Text("Borrar solicitudes remotas")
            }
            Button(
                onClick = { pendingAction = DevAction.ClearAllRequests },
                enabled = !state.devToolsBusy,
            ) {
                Text("Borrar todas las solicitudes")
            }
            Button(
                onClick = { pendingAction = DevAction.ClearRules },
                enabled = !state.devToolsBusy,
            ) {
                Text("Borrar reglas")
            }
            Button(
                onClick = { pendingAction = DevAction.ClearExtraTimeGrants },
                enabled = !state.devToolsBusy,
            ) {
                Text("Borrar tiempos extra")
            }
            Button(
                onClick = { pendingAction = DevAction.ClearDuplicateDevices },
                enabled = !state.devToolsBusy,
            ) {
                Text("Borrar dispositivos duplicados")
            }
            Button(
                onClick = { pendingAction = DevAction.ResetDev },
                enabled = !state.devToolsBusy,
            ) {
                Text("Reset DEV completo")
            }
            if (state.devToolsMessage.isNotBlank()) {
                Text(state.devToolsMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Confirmar") },
            text = { Text(action.confirmationText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        when (action) {
                            DevAction.ClearLocalRequests -> onClearLocalRequests()
                            DevAction.ClearRemoteRequests -> onClearRemoteRequests()
                            DevAction.ClearAllRequests -> onClearAllRequests()
                            DevAction.ClearRules -> onClearRules()
                            DevAction.ClearExtraTimeGrants -> onClearExtraTimeGrants()
                            DevAction.ClearDuplicateDevices -> onClearDuplicateDevices()
                            DevAction.ResetDev -> onResetDev()
                        }
                    },
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private enum class DevAction(val confirmationText: String) {
    ClearLocalRequests("Esto borra solicitudes locales, Outbox y cursores de sync."),
    ClearRemoteRequests("Esto marca como borradas las solicitudes remotas DEV del account actual."),
    ClearAllRequests("Esto borra solicitudes del Usuario y Admin, Room, Outbox y Supabase DEV."),
    ClearRules("Esto borra reglas locales y marca como borradas las reglas remotas DEV del account actual."),
    ClearExtraTimeGrants("Esto borra tiempos extra locales y remotos DEV del account actual."),
    ClearDuplicateDevices("Esto borra dispositivos duplicados y conserva el dispositivo actual."),
    ResetDev("Esto limpia solicitudes, reglas, grants, devices duplicados, Room, Outbox y cache local DEV."),
}
