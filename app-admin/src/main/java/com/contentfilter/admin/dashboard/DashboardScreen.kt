package com.contentfilter.admin.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DashboardRoute(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(state = state)
}

@Composable
private fun DashboardScreen(state: DashboardUiState) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.offlineMode) {
            Text("Sin conexion. Mostrando datos guardados.")
        }
        if (state.communityName.isNotBlank()) {
            Text("Comunidad: ${state.communityName}")
        }
        if (state.guideName.isNotBlank()) {
            Text("Responsable: ${state.guideName}")
        }
        Text("Dispositivos: ${state.deviceCount}")
        Text("Solicitudes pendientes: ${state.pendingRequests}")
        Text("Sincronizacion: ${state.syncState}")
        Text("Estado general: ${state.systemState}")
        Text("Ultima sincronizacion: ${state.lastSync}")
    }
}
