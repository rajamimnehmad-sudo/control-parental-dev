package com.contentfilter.admin.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DevicesRoute(viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DevicesScreen(
        state = state,
        onGeneratePairingCode = viewModel::generatePairingCode,
        onRevokeDevice = viewModel::revokeDevice,
    )
}

@Composable
private fun DevicesScreen(
    state: DevicesUiState,
    onGeneratePairingCode: () -> Unit,
    onRevokeDevice: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dispositivos", style = MaterialTheme.typography.headlineSmall)
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.secondary)
        }
        Button(
            onClick = onGeneratePairingCode,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Generar codigo de enlace")
        }
        if (state.pairingCode.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Codigo para App Usuario", style = MaterialTheme.typography.labelLarge)
                    Text(state.pairingCode, style = MaterialTheme.typography.headlineMedium)
                    if (state.pairingExpiresAt.isNotBlank()) {
                        Text("Vence: ${state.pairingExpiresAt}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.devices.isEmpty()) {
            Text("Todavia no hay dispositivos locales.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    loading = state.loading,
                    onRevokeDevice = onRevokeDevice,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: AdminDeviceItem,
    loading: Boolean,
    onRevokeDevice: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("Usuario: ${device.user}", style = MaterialTheme.typography.bodySmall)
            Text("Version: ${device.version}", style = MaterialTheme.typography.bodySmall)
            Text(
                "VPN: ${device.vpnState} | Accesibilidad: ${device.accessibilityState}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Ultima sincronizacion: ${device.lastSync}", style = MaterialTheme.typography.bodySmall)
            Text("Estado: ${device.systemState}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { onRevokeDevice(device.id) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Eliminar dispositivo")
            }
        }
    }
}
