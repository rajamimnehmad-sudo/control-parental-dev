package com.contentfilter.feature.status

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val isDevProtectionAvailable = VpnController.isDevProtectionAvailable(context)
    var isDevProtectionDisabled by remember {
        mutableStateOf(VpnController.isDevProtectionDisabled(context))
    }
    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                VpnController.start(context)
                isDevProtectionDisabled = false
            }
        }
    SystemStatusScreen(
        state = state.value.withVpnRunning(isVpnRunning.value),
        onStartVpn = {
            val prepareIntent = VpnController.prepareIntent(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                VpnController.start(context)
                isDevProtectionDisabled = false
            }
        },
        onStopVpn = { VpnController.stop(context) },
        showDevRescue = isDevProtectionAvailable,
        isDevProtectionDisabled = isDevProtectionDisabled,
        onDisableDevProtection = {
            VpnController.disableDevProtection(context)
            isDevProtectionDisabled = true
        },
        onEnableDevProtection = {
            VpnController.enableDevProtection(context)
            isDevProtectionDisabled = false
        },
        modifier = modifier,
    )
}

@Composable
fun SystemStatusScreen(
    state: SystemStatusUiState,
    onStartVpn: () -> Unit = {},
    onStopVpn: () -> Unit = {},
    showDevRescue: Boolean = false,
    isDevProtectionDisabled: Boolean = false,
    onDisableDevProtection: () -> Unit = {},
    onEnableDevProtection: () -> Unit = {},
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
        Button(onClick = if (state.isVpnActive) onStopVpn else onStartVpn) {
            Text(if (state.isVpnActive) "Desactivar VPN" else "Activar VPN")
        }
        if (showDevRescue) {
            Button(
                onClick = if (isDevProtectionDisabled) onEnableDevProtection else onDisableDevProtection,
            ) {
                Text(if (isDevProtectionDisabled) "Reactivar protección DEV" else "Desactivar protección DEV")
            }
            if (isDevProtectionDisabled) {
                Text("Protección DEV pausada.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
