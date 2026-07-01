package com.contentfilter.admin.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DevicesRoute(
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dispositivos", style = MaterialTheme.typography.headlineSmall)
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        if (state.devices.isEmpty()) {
            Text("Todavia no hay dispositivos locales.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.devices, key = { it.id }) { device ->
                DeviceCard(device)
            }
        }
    }
}

@Composable
private fun DeviceCard(device: AdminDeviceItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("Usuario: ${device.user}")
            Text("Version: ${device.version}")
            Text("VPN: ${device.vpnState}")
            Text("Accesibilidad: ${device.accessibilityState}")
            Text("Ultima sincronizacion: ${device.lastSync}")
            Text("Estado: ${device.systemState}")
        }
    }
}
