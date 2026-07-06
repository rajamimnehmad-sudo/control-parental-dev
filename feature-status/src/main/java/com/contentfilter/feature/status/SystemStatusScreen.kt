package com.contentfilter.feature.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.StatusBadge
import com.contentfilter.feature.vpn.service.VpnController

@Composable
fun SystemStatusRoute(
    modifier: Modifier = Modifier,
    viewModel: SystemStatusViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isVpnRunning =
        VpnController
            .observeRunning(context)
            .collectAsStateWithLifecycle(initialValue = VpnController.isRunning(context))
    SystemStatusScreen(
        state = state.value.withVpnRunning(isVpnRunning.value),
        modifier = modifier,
    )
}

@Composable
fun SystemStatusScreen(
    state: SystemStatusUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = state.title, style = MaterialTheme.typography.headlineMedium)
        StatusBadge(level = state.protectionLevel)
        Text(text = state.summary, style = MaterialTheme.typography.bodyLarge)
        Text(text = "VPN: ${state.vpnState}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Accesibilidad: ${state.accessibilityState}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Sincronización: ${state.syncState}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Activación: ${state.activationState}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Versión: ${state.appVersion}", style = MaterialTheme.typography.bodyMedium)
        Text("Web congelado temporalmente. El bloqueo activo es solo por apps.", style = MaterialTheme.typography.bodySmall)
    }
}
